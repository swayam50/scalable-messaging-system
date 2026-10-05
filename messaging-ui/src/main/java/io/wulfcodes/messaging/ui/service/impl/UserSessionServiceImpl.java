package io.wulfcodes.messaging.ui.service.impl;

import io.wulfcodes.messaging.common.model.dto.request.LoginRequest;
import io.wulfcodes.messaging.common.model.dto.request.RegisterRequest;
import io.wulfcodes.messaging.common.model.dto.response.AuthResponse;
import io.wulfcodes.messaging.ui.exception.AuthClientException;
import io.wulfcodes.messaging.ui.exception.NotLoggedInException;
import io.wulfcodes.messaging.ui.model.dto.response.AccessTokenResponse;
import io.wulfcodes.messaging.ui.model.vo.SessionTokens;
import io.wulfcodes.messaging.ui.service.spec.AuthGatewayService;
import io.wulfcodes.messaging.ui.service.spec.UserSessionService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionServiceImpl implements UserSessionService {

    static final String TOKENS = "sessionTokens";
    /** Refresh a bit before expiry so a token never dies mid-request. */
    static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final AuthGatewayService authGateway;
    private final Clock clock;

    @Override
    public void login(LoginRequest request, HttpSession session) {
        store(session, authGateway.login(request));
    }

    @Override
    public void register(RegisterRequest request, HttpSession session) {
        store(session, authGateway.register(request));
    }

    @Override
    public void logout(HttpSession session) {
        current(session).ifPresent(tokens -> {
            try {
                authGateway.logout(tokens.refreshToken());
            } catch (AuthClientException e) {
                log.debug("Logout at auth-service failed: {}", e.getMessage());
            }
        });
        session.invalidate();
    }

    @Override
    public Optional<SessionTokens> current(HttpSession session) {
        return Optional.ofNullable((SessionTokens) session.getAttribute(TOKENS));
    }

    /**
     * Synchronized on the session: several browser tabs share one session cookie, and refresh tokens
     * are single-use. Without the lock, two tabs refreshing at once would present the same refresh
     * token twice, auth-service would treat that as token theft and revoke everything.
     */
    @Override
    public AccessTokenResponse freshAccessToken(HttpSession session) {
        synchronized (WebUtils.getSessionMutex(session)) {
            SessionTokens tokens = current(session).orElseThrow(NotLoggedInException::new);
            if (tokens.expiresWithin(REFRESH_MARGIN, clock.instant())) {
                try {
                    tokens = store(session, authGateway.refresh(tokens.refreshToken()));
                } catch (AuthClientException e) {
                    session.removeAttribute(TOKENS);
                    throw new NotLoggedInException();
                }
            }
            long expiresIn = Duration.between(clock.instant(), tokens.accessTokenExpiresAt()).toSeconds();
            return new AccessTokenResponse(tokens.accessToken(), Math.max(expiresIn, 0));
        }
    }

    private SessionTokens store(HttpSession session, AuthResponse auth) {
        SessionTokens tokens = new SessionTokens(auth.accessToken(),
                clock.instant().plusSeconds(auth.expiresIn()), auth.refreshToken(), auth.user());
        session.setAttribute(TOKENS, tokens);
        return tokens;
    }
}
