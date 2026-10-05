package io.wulfcodes.messaging.ui.controller.resource.v1;

import io.wulfcodes.messaging.common.model.dto.response.UserSummaryResponse;
import io.wulfcodes.messaging.ui.config.WebConfig;
import io.wulfcodes.messaging.ui.service.spec.AuthGatewayService;
import io.wulfcodes.messaging.ui.service.spec.UserSessionService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Proxies user search / lookup to auth-service (BFF): the browser only talks to its own origin
 * for user data, so auth-service needs no CORS setup and never sees browser traffic.
 */
@RestController
@RequestMapping("/api/{version}/users")
@RequiredArgsConstructor
public class UserLookupResource {

    private final UserSessionService userSessionService;
    private final AuthGatewayService authGateway;

    @GetMapping(version = WebConfig.API_V1)
    public List<UserSummaryResponse> search(@RequestParam String query, HttpSession session) {
        return authGateway.searchUsers(token(session), query);
    }

    @GetMapping(path = "/{id}", version = WebConfig.API_V1)
    public UserSummaryResponse get(@PathVariable String id, HttpSession session) {
        return authGateway.getUser(token(session), id);
    }

    private String token(HttpSession session) {
        return userSessionService.freshAccessToken(session).accessToken();
    }
}
