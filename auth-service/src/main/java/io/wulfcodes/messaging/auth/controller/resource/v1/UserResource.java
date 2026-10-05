package io.wulfcodes.messaging.auth.controller.resource.v1;

import io.wulfcodes.messaging.auth.config.WebConfig;
import io.wulfcodes.messaging.common.model.dto.response.UserResponse;
import io.wulfcodes.messaging.common.model.dto.response.UserSummaryResponse;
import io.wulfcodes.messaging.auth.service.spec.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User API, version 1: /api/v1/users/... (requires a Bearer access token).
 */
@RestController
@RequestMapping("/api/{version}/users")
@RequiredArgsConstructor
public class UserResource {

    private final UserService userService;

    /** The JWT "sub" claim is the user's id, set by TokenService when the token was issued. */
    @GetMapping(path = "/me", version = WebConfig.API_V1)
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getCurrentUser(jwt.getSubject());
    }

    @GetMapping(path = "/{id}", version = WebConfig.API_V1)
    public UserSummaryResponse getById(@PathVariable String id) {
        return userService.getById(id);
    }

    @GetMapping(version = WebConfig.API_V1)
    public List<UserSummaryResponse> search(@RequestParam("query") String query) {
        return userService.searchByUsername(query);
    }
}
