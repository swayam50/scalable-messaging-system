package io.wulfcodes.messaging.auth.service.spec;

import io.wulfcodes.messaging.common.model.dto.request.LoginRequest;
import io.wulfcodes.messaging.common.model.dto.request.RefreshRequest;
import io.wulfcodes.messaging.common.model.dto.request.RegisterRequest;
import io.wulfcodes.messaging.common.model.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshRequest request);

    void logout(RefreshRequest request);
}
