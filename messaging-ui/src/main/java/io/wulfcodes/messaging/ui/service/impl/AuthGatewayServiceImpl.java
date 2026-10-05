package io.wulfcodes.messaging.ui.service.impl;

import io.wulfcodes.messaging.common.model.dto.request.LoginRequest;
import io.wulfcodes.messaging.common.model.dto.request.RefreshRequest;
import io.wulfcodes.messaging.common.model.dto.request.RegisterRequest;
import io.wulfcodes.messaging.common.model.dto.response.AuthResponse;
import io.wulfcodes.messaging.common.model.dto.response.UserSummaryResponse;
import io.wulfcodes.messaging.ui.exception.AuthClientException;
import io.wulfcodes.messaging.ui.service.spec.AuthGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthGatewayServiceImpl implements AuthGatewayService {

    private final RestClient authRestClient;
    private final JsonMapper jsonMapper;

    @Override
    public AuthResponse register(RegisterRequest request) {
        return post("/api/v1/auth/register", request);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        return post("/api/v1/auth/login", request);
    }

    @Override
    public AuthResponse refresh(String refreshToken) {
        return post("/api/v1/auth/refresh", new RefreshRequest(refreshToken));
    }

    @Override
    public void logout(String refreshToken) {
        authRestClient.post().uri("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshRequest(refreshToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw toException(res.getStatusCode(), res.getBody()); })
                .toBodilessEntity();
    }

    @Override
    public List<UserSummaryResponse> searchUsers(String accessToken, String query) {
        return authRestClient.get().uri(uri -> uri.path("/api/v1/users").queryParam("query", query).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw toException(res.getStatusCode(), res.getBody()); })
                .body(new ParameterizedTypeReference<>() {
                });
    }

    @Override
    public UserSummaryResponse getUser(String accessToken, String userId) {
        return authRestClient.get().uri("/api/v1/users/{id}", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw toException(res.getStatusCode(), res.getBody()); })
                .body(UserSummaryResponse.class);
    }

    private AuthResponse post(String path, Object body) {
        return authRestClient.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw toException(res.getStatusCode(), res.getBody()); })
                .body(AuthResponse.class);
    }

    /** Turns auth-service's RFC 9457 problem body into a readable message (first field error if any). */
    private AuthClientException toException(HttpStatusCode status, java.io.InputStream body) throws IOException {
        String detail = "Request failed";
        try {
            JsonNode problem = jsonMapper.readTree(body);
            JsonNode errors = problem.path("errors");
            if (errors.isObject() && !errors.isEmpty()) {
                var first = errors.properties().iterator().next();
                detail = first.getKey() + " " + first.getValue().asString();
            } else if (problem.hasNonNull("detail")) {
                detail = problem.get("detail").asString();
            }
        } catch (RuntimeException ignored) {
            // non-JSON error body: keep the generic message
        }
        return new AuthClientException(status, detail);
    }
}
