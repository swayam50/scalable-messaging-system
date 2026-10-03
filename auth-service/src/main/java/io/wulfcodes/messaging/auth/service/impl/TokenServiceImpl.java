package io.wulfcodes.messaging.auth.service.impl;

import io.wulfcodes.messaging.auth.config.JwtProperties;
import io.wulfcodes.messaging.auth.exception.InvalidRefreshTokenException;
import io.wulfcodes.messaging.auth.model.po.RefreshToken;
import io.wulfcodes.messaging.auth.model.po.User;
import io.wulfcodes.messaging.auth.model.vo.IssuedRefreshToken;
import io.wulfcodes.messaging.auth.repository.RefreshTokenRepository;
import io.wulfcodes.messaging.auth.service.spec.TokenService;
import io.wulfcodes.messaging.auth.util.HashUtil;
import io.wulfcodes.messaging.auth.util.SecureTokenUtil;
import io.wulfcodes.messaging.common.util.UlidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UlidGenerator ulidGenerator;
    private final Clock clock;

    @Override
    public String issueAccessToken(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getId())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                .claim("username", user.getUsername())
                .claim("name", user.getDisplayName())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwtProperties.keyId()).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    @Transactional
    public IssuedRefreshToken issueRefreshToken(User user) {
        String raw = SecureTokenUtil.generate();
        Instant expiresAt = clock.instant().plus(jwtProperties.refreshTokenTtl());
        refreshTokenRepository.save(RefreshToken.builder()
                .id(ulidGenerator.nextString())
                .user(user)
                .tokenHash(HashUtil.sha256Hex(raw))
                .expiresAt(expiresAt)
                .revoked(false)
                .build());
        return new IssuedRefreshToken(raw, expiresAt);
    }

    /**
     * noRollbackFor: when reuse is detected we revoke all of the user's tokens and then throw;
     * the revocation must still be committed.
     */
    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RefreshToken consumeRefreshToken(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (token.isRevoked()) {
            // A revoked token being replayed means it was likely stolen: kill every session of this user.
            int revoked = refreshTokenRepository.revokeAllForUser(token.getUser().getId());
            log.warn("Refresh token reuse detected for user {}; revoked {} active tokens", token.getUser().getId(), revoked);
            throw new InvalidRefreshTokenException();
        }
        if (!token.isUsable(clock.instant())) {
            throw new InvalidRefreshTokenException();
        }

        token.setRevoked(true);
        return token;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void revokeRefreshToken(String rawToken) {
        refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex(rawToken))
                .ifPresent(token -> token.setRevoked(true));
    }

    @Override
    public long accessTokenTtlSeconds() {
        return jwtProperties.accessTokenTtl().toSeconds();
    }
}
