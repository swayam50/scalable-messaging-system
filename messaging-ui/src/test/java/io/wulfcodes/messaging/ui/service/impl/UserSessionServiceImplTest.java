package io.wulfcodes.messaging.ui.service.impl;

import io.wulfcodes.messaging.common.model.dto.response.AuthResponse;
import io.wulfcodes.messaging.common.model.dto.response.UserResponse;
import io.wulfcodes.messaging.common.model.vo.TokenType;
import io.wulfcodes.messaging.common.model.vo.UserStatus;
import io.wulfcodes.messaging.ui.exception.AuthClientException;
import io.wulfcodes.messaging.ui.exception.NotLoggedInException;
import io.wulfcodes.messaging.ui.model.dto.response.AccessTokenResponse;
import io.wulfcodes.messaging.ui.model.vo.SessionTokens;
import io.wulfcodes.messaging.ui.service.spec.AuthGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final UserResponse USER =
            new UserResponse("U1", "carol", "carol@example.com", "Carol", UserStatus.ACTIVE, NOW);

    @Mock
    private AuthGatewayService authGateway;

    private UserSessionServiceImpl service;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        service = new UserSessionServiceImpl(authGateway, Clock.fixed(NOW, ZoneOffset.UTC));
        session = new MockHttpSession();
    }

    @Test
    void validTokenIsReturnedWithoutCallingAuthService() {
        session.setAttribute(UserSessionServiceImpl.TOKENS,
                new SessionTokens("access-1", NOW.plusSeconds(600), "refresh-1", USER));

        AccessTokenResponse token = service.freshAccessToken(session);

        assertThat(token.accessToken()).isEqualTo("access-1");
        assertThat(token.expiresIn()).isEqualTo(600);
        verify(authGateway, never()).refresh(any());
    }

    @Test
    void tokenCloseToExpiryIsRefreshedAndRotatedRefreshTokenIsStored() {
        session.setAttribute(UserSessionServiceImpl.TOKENS,
                new SessionTokens("access-1", NOW.plusSeconds(30), "refresh-1", USER));
        when(authGateway.refresh("refresh-1"))
                .thenReturn(new AuthResponse("access-2", "refresh-2", TokenType.BEARER, 900, USER));

        AccessTokenResponse token = service.freshAccessToken(session);

        assertThat(token.accessToken()).isEqualTo("access-2");
        SessionTokens stored = (SessionTokens) session.getAttribute(UserSessionServiceImpl.TOKENS);
        assertThat(stored.refreshToken()).isEqualTo("refresh-2");
    }

    @Test
    void failedRefreshLogsTheUserOut() {
        session.setAttribute(UserSessionServiceImpl.TOKENS,
                new SessionTokens("access-1", NOW.minusSeconds(1), "refresh-1", USER));
        when(authGateway.refresh("refresh-1"))
                .thenThrow(new AuthClientException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid"));

        assertThatThrownBy(() -> service.freshAccessToken(session)).isInstanceOf(NotLoggedInException.class);
        assertThat(session.getAttribute(UserSessionServiceImpl.TOKENS)).isNull();
    }

    @Test
    void noSessionMeansNotLoggedIn() {
        assertThatThrownBy(() -> service.freshAccessToken(session)).isInstanceOf(NotLoggedInException.class);
    }

    @Test
    void logoutRevokesRefreshTokenAndInvalidatesSession() {
        session.setAttribute(UserSessionServiceImpl.TOKENS,
                new SessionTokens("access-1", NOW.plusSeconds(600), "refresh-1", USER));

        service.logout(session);

        verify(authGateway).logout("refresh-1");
        assertThat(session.isInvalid()).isTrue();
    }
}
