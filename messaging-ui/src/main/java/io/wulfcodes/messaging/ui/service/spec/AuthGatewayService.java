package io.wulfcodes.messaging.ui.service.spec;

import io.wulfcodes.messaging.common.model.dto.request.LoginRequest;
import io.wulfcodes.messaging.common.model.dto.request.RegisterRequest;
import io.wulfcodes.messaging.common.model.dto.response.AuthResponse;
import io.wulfcodes.messaging.common.model.dto.response.UserSummaryResponse;

import java.util.List;

/**
 * Server-to-server client for auth-service (uses the shared DTOs from messaging-common).
 */
public interface AuthGatewayService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(String refreshToken);

    void logout(String refreshToken);

    List<UserSummaryResponse> searchUsers(String accessToken, String query);

    UserSummaryResponse getUser(String accessToken, String userId);
}
