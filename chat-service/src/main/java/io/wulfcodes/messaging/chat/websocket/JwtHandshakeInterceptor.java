package io.wulfcodes.messaging.chat.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.wulfcodes.messaging.chat.service.spec.RingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Authenticates the WebSocket upgrade request: /ws/chat?access_token=JWT.
 * <p>
 * Browsers cannot add an Authorization header to a WebSocket handshake, so the short-lived
 * access token travels as a query parameter. The connection is refused (401) before it is
 * ever opened if the token is missing, expired or not signed by auth-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID = "userId";
    public static final String USERNAME = "username";
    static final String TOKEN_PARAM = "access_token";

    private final JwtDecoder jwtDecoder;
    private final RingService ringService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst(TOKEN_PARAM);
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            if (!ringService.isLocal(jwt.getSubject())) {
                // Wrong node: this user is owned by another node. Refuse, so the client asks
                // /api/v1/connect again; otherwise messages routed to the owner would never reach it.
                response.setStatusCode(HttpStatus.CONFLICT);
                return false;
            }
            attributes.put(USER_ID, jwt.getSubject());
            attributes.put(USERNAME, jwt.getClaimAsString("username"));
            return true;
        } catch (JwtException e) {
            log.debug("Rejected WebSocket handshake: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing to do
    }
}
