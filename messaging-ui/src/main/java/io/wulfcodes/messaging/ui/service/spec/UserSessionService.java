package io.wulfcodes.messaging.ui.service.spec;

import io.wulfcodes.messaging.common.model.dto.request.LoginRequest;
import io.wulfcodes.messaging.common.model.dto.request.RegisterRequest;
import io.wulfcodes.messaging.ui.model.dto.response.AccessTokenResponse;
import io.wulfcodes.messaging.ui.model.vo.SessionTokens;
import jakarta.servlet.http.HttpSession;

import java.util.Optional;

/**
 * Keeps the user's tokens in the server-side HttpSession (BFF pattern).
 */
public interface UserSessionService {

    void login(LoginRequest request, HttpSession session);

    void register(RegisterRequest request, HttpSession session);

    void logout(HttpSession session);

    Optional<SessionTokens> current(HttpSession session);

    /** Returns a valid access token, transparently refreshing it when it is about to expire. */
    AccessTokenResponse freshAccessToken(HttpSession session);
}
