package io.wulfcodes.messaging.ui.controller.resource.v1;

import io.wulfcodes.messaging.ui.config.WebConfig;
import io.wulfcodes.messaging.ui.model.dto.response.AccessTokenResponse;
import io.wulfcodes.messaging.ui.service.spec.UserSessionService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same-origin endpoint the page calls to get a (fresh) access token.
 * Safe against other sites: they can trigger the POST with the cookie, but without CORS headers
 * the browser never lets them read the response, so the token cannot leak.
 */
@RestController
@RequestMapping("/api/{version}/session")
@RequiredArgsConstructor
public class SessionResource {

    private final UserSessionService userSessionService;

    @PostMapping(path = "/token", version = WebConfig.API_V1)
    public AccessTokenResponse token(HttpSession session) {
        return userSessionService.freshAccessToken(session);
    }
}
