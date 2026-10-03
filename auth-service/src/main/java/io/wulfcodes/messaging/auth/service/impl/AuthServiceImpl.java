package io.wulfcodes.messaging.auth.service.impl;

import io.wulfcodes.messaging.auth.exception.AccountDisabledException;
import io.wulfcodes.messaging.auth.exception.InvalidCredentialsException;
import io.wulfcodes.messaging.auth.exception.InvalidRefreshTokenException;
import io.wulfcodes.messaging.auth.exception.UserAlreadyExistsException;
import io.wulfcodes.messaging.auth.mapper.UserMapper;
import io.wulfcodes.messaging.auth.model.dto.request.LoginRequest;
import io.wulfcodes.messaging.auth.model.dto.request.RefreshRequest;
import io.wulfcodes.messaging.auth.model.dto.request.RegisterRequest;
import io.wulfcodes.messaging.auth.model.dto.response.AuthResponse;
import io.wulfcodes.messaging.auth.model.po.RefreshToken;
import io.wulfcodes.messaging.auth.model.po.User;
import io.wulfcodes.messaging.auth.model.vo.IssuedRefreshToken;
import io.wulfcodes.messaging.auth.model.vo.TokenType;
import io.wulfcodes.messaging.auth.model.vo.UserStatus;
import io.wulfcodes.messaging.auth.repository.UserRepository;
import io.wulfcodes.messaging.auth.service.spec.AuthService;
import io.wulfcodes.messaging.auth.service.spec.TokenService;
import io.wulfcodes.messaging.common.util.UlidGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final UlidGenerator ulidGenerator;
    private final UserMapper userMapper;

    /** Hash compared against when the user does not exist, so both paths take BCrypt time. */
    private volatile String dummyPasswordHash;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new UserAlreadyExistsException("username");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new UserAlreadyExistsException("email");
        }

        User user = userRepository.save(User.builder()
                .id(ulidGenerator.nextString())
                .username(request.username())
                .email(request.email().toLowerCase(Locale.ROOT))
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .status(UserStatus.ACTIVE)
                .build());

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = findByLogin(request.login()).orElse(null);

        if (user == null) {
            // Still run a BCrypt comparison: otherwise "unknown user" answers much faster than
            // "wrong password", and response timing would reveal which usernames exist.
            passwordEncoder.matches(request.password(), dummyPasswordHash());
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        return issueTokens(user);
    }

    /** Rotation: the presented refresh token is revoked and a brand-new pair is issued. */
    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken consumed = tokenService.consumeRefreshToken(request.refreshToken());
        User user = consumed.getUser();
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        return issueTokens(user);
    }

    @Override
    @Transactional
    public void logout(RefreshRequest request) {
        tokenService.revokeRefreshToken(request.refreshToken());
    }

    private String dummyPasswordHash() {
        if (dummyPasswordHash == null) {
            dummyPasswordHash = passwordEncoder.encode("timing-attack-dummy-password");
        }
        return dummyPasswordHash;
    }

    private Optional<User> findByLogin(String login) {
        return login.contains("@")
                ? userRepository.findByEmailIgnoreCase(login)
                : userRepository.findByUsernameIgnoreCase(login);
    }

    private AuthResponse issueTokens(User user) {
        IssuedRefreshToken refreshToken = tokenService.issueRefreshToken(user);
        return new AuthResponse(
                tokenService.issueAccessToken(user),
                refreshToken.rawValue(),
                TokenType.BEARER,
                tokenService.accessTokenTtlSeconds(),
                userMapper.toResponse(user));
    }
}
