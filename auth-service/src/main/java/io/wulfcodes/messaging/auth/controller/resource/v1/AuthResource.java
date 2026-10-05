package io.wulfcodes.messaging.auth.controller.resource.v1;

import io.wulfcodes.messaging.auth.config.WebConfig;
import io.wulfcodes.messaging.common.model.dto.request.LoginRequest;
import io.wulfcodes.messaging.common.model.dto.request.RefreshRequest;
import io.wulfcodes.messaging.common.model.dto.request.RegisterRequest;
import io.wulfcodes.messaging.common.model.dto.response.AuthResponse;
import io.wulfcodes.messaging.auth.service.spec.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication API, version 1: /api/v1/auth/...
 * The controller only deals with DTOs and delegates everything to the service layer.
 */
@RestController
@RequestMapping("/api/{version}/auth")
@RequiredArgsConstructor
public class AuthResource {

    private final AuthService authService;

    @PostMapping(path = "/register", version = WebConfig.API_V1)
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping(path = "/login", version = WebConfig.API_V1)
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping(path = "/refresh", version = WebConfig.API_V1)
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping(path = "/logout", version = WebConfig.API_V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }
}
