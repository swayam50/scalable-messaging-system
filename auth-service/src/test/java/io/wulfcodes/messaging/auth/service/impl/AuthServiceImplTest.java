package io.wulfcodes.messaging.auth.service.impl;

import io.wulfcodes.messaging.auth.exception.AccountDisabledException;
import io.wulfcodes.messaging.auth.exception.InvalidCredentialsException;
import io.wulfcodes.messaging.auth.exception.UserAlreadyExistsException;
import io.wulfcodes.messaging.auth.mapper.UserMapper;
import io.wulfcodes.messaging.common.model.dto.request.LoginRequest;
import io.wulfcodes.messaging.common.model.dto.request.RefreshRequest;
import io.wulfcodes.messaging.common.model.dto.request.RegisterRequest;
import io.wulfcodes.messaging.common.model.dto.response.AuthResponse;
import io.wulfcodes.messaging.auth.model.po.RefreshToken;
import io.wulfcodes.messaging.auth.model.po.User;
import io.wulfcodes.messaging.auth.model.vo.IssuedRefreshToken;
import io.wulfcodes.messaging.common.model.vo.TokenType;
import io.wulfcodes.messaging.common.model.vo.UserStatus;
import io.wulfcodes.messaging.auth.repository.UserRepository;
import io.wulfcodes.messaging.auth.service.spec.TokenService;
import io.wulfcodes.messaging.common.util.UlidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, tokenService, passwordEncoder, new UlidGenerator(), userMapper);
    }

    @Test
    void registerCreatesActiveUserWithHashedPasswordAndIssuesTokens() {
        when(passwordEncoder.encode("supersecret1")).thenReturn("{bcrypt}hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTokens();

        AuthResponse response = authService.register(
                new RegisterRequest("swayam", "Swayam@Example.com", "supersecret1", "Swayam"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.getId()).hasSize(26);
        assertThat(user.getEmail()).isEqualTo("swayam@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}hash");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.tokenType()).isEqualTo(TokenType.BEARER);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsernameIgnoreCase("swayam")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("swayam", "a@b.com", "supersecret1", "S")))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithEmailUsesEmailLookup() {
        User user = activeUser();
        when(userRepository.findByEmailIgnoreCase("swayam@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("supersecret1", user.getPasswordHash())).thenReturn(true);
        stubTokens();

        AuthResponse response = authService.login(new LoginRequest("swayam@example.com", "supersecret1"));

        assertThat(response.accessToken()).isEqualTo("access");
    }

    @Test
    void loginWithWrongPasswordFails() {
        User user = activeUser();
        when(userRepository.findByUsernameIgnoreCase("swayam")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("swayam", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginForUnknownUserStillRunsPasswordCheckAndFails() {
        when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}dummy");

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordEncoder).matches("whatever", "{bcrypt}dummy");   // constant-time-ish path
    }

    @Test
    void loginForDisabledUserFails() {
        User user = activeUser();
        user.setStatus(UserStatus.DISABLED);
        when(userRepository.findByUsernameIgnoreCase("swayam")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("supersecret1", user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("swayam", "supersecret1")))
                .isInstanceOf(AccountDisabledException.class);
    }

    @Test
    void refreshConsumesOldTokenAndIssuesNewPair() {
        User user = activeUser();
        RefreshToken consumed = RefreshToken.builder().user(user).revoked(true).build();
        when(tokenService.consumeRefreshToken("old")).thenReturn(consumed);
        stubTokens();

        AuthResponse response = authService.refresh(new RefreshRequest("old"));

        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(tokenService).consumeRefreshToken("old");
    }

    private void stubTokens() {
        when(tokenService.issueAccessToken(any())).thenReturn("access");
        when(tokenService.issueRefreshToken(any())).thenReturn(new IssuedRefreshToken("refresh", Instant.now()));
        when(tokenService.accessTokenTtlSeconds()).thenReturn(900L);
    }

    private static User activeUser() {
        return User.builder()
                .id("01M41CXBD9QSH5S348AEDDKN18")
                .username("swayam")
                .email("swayam@example.com")
                .passwordHash("{bcrypt}hash")
                .displayName("Swayam")
                .status(UserStatus.ACTIVE)
                .build();
    }
}
