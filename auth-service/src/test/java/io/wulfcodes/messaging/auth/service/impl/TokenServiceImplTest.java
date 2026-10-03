package io.wulfcodes.messaging.auth.service.impl;

import io.wulfcodes.messaging.auth.config.JwtProperties;
import io.wulfcodes.messaging.auth.exception.InvalidRefreshTokenException;
import io.wulfcodes.messaging.auth.model.po.RefreshToken;
import io.wulfcodes.messaging.auth.model.po.User;
import io.wulfcodes.messaging.auth.repository.RefreshTokenRepository;
import io.wulfcodes.messaging.auth.util.HashUtil;
import io.wulfcodes.messaging.common.util.UlidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

    @Mock
    private JwtEncoder jwtEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private TokenServiceImpl tokenService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("http://test", Duration.ofMinutes(15), Duration.ofDays(7), "k1", "", "");
        tokenService = new TokenServiceImpl(jwtEncoder, properties, refreshTokenRepository,
                new UlidGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void validTokenIsConsumedAndRevoked() {
        RefreshToken token = token(false, NOW.plusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex("raw"))).thenReturn(Optional.of(token));

        RefreshToken consumed = tokenService.consumeRefreshToken("raw");

        assertThat(consumed.isRevoked()).isTrue();
    }

    @Test
    void expiredTokenIsRejected() {
        when(refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex("raw")))
                .thenReturn(Optional.of(token(false, NOW.minusSeconds(1))));

        assertThatThrownBy(() -> tokenService.consumeRefreshToken("raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void reusedRevokedTokenRevokesAllUserSessions() {
        RefreshToken token = token(true, NOW.plusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex("raw"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> tokenService.consumeRefreshToken("raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository).revokeAllForUser("U1");
    }

    @Test
    void unknownTokenIsRejected() {
        when(refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex("nope"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.consumeRefreshToken("nope"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    private static RefreshToken token(boolean revoked, Instant expiresAt) {
        return RefreshToken.builder()
                .id("T1")
                .user(User.builder().id("U1").build())
                .tokenHash("hash")
                .expiresAt(expiresAt)
                .revoked(revoked)
                .build();
    }
}
