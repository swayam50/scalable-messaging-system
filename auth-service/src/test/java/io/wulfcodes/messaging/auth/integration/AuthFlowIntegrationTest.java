package io.wulfcodes.messaging.auth.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test against a real PostgreSQL started by Testcontainers.
 * {@code @ServiceConnection} wires the container's JDBC URL/credentials into Spring automatically,
 * and Flyway runs the real migrations, so this also proves the schema matches the entities.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    private MockMvcTester mvc;

    @Test
    void registerLoginMeRefreshLogoutFlow() {
        // register
        MvcTestResult registered = post("/api/v1/auth/register", """
                {"username":"alice","email":"Alice@Example.com","password":"supersecret1","displayName":"Alice"}""");
        assertThat(registered).hasStatus(HttpStatus.CREATED);
        assertThat(registered).bodyJson().extractingPath("$.user.email").isEqualTo("alice@example.com");

        // login (by username, case-insensitive)
        MvcTestResult loggedIn = post("/api/v1/auth/login", """
                {"login":"ALICE","password":"supersecret1"}""");
        assertThat(loggedIn).hasStatusOk();
        String accessToken = read(loggedIn, "$.accessToken");
        String refreshToken = read(loggedIn, "$.refreshToken");

        // me
        MvcTestResult me = mvc.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange();
        assertThat(me).hasStatusOk();
        assertThat(me).bodyJson().extractingPath("$.username").isEqualTo("alice");

        // refresh rotates the token...
        MvcTestResult refreshed = post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}");
        assertThat(refreshed).hasStatusOk();
        String newRefreshToken = read(refreshed, "$.refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        // ...so the old one cannot be reused, and reuse revokes the whole family
        assertThat(post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + newRefreshToken + "\"}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        // logout is idempotent
        assertThat(post("/api/v1/auth/logout", "{\"refreshToken\":\"" + newRefreshToken + "\"}"))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void duplicateUsernameIsConflict() {
        post("/api/v1/auth/register", """
                {"username":"bob","email":"bob@example.com","password":"supersecret1","displayName":"Bob"}""");

        MvcTestResult duplicate = post("/api/v1/auth/register", """
                {"username":"BOB","email":"other@example.com","password":"supersecret1","displayName":"Bob 2"}""");

        assertThat(duplicate).hasStatus(HttpStatus.CONFLICT);
        assertThat(duplicate).bodyJson().extractingPath("$.title").isEqualTo("User already exists");
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() {
        MvcTestResult result = post("/api/v1/auth/register", """
                {"username":"x!","email":"not-an-email","password":"short","displayName":""}""");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.email").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.errors.password").isNotNull();
    }

    @Test
    void protectedEndpointRequiresToken() {
        assertThat(mvc.get().uri("/api/v1/users/me").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unsupportedApiVersionIsRejected() {
        assertThat(post("/api/v2/auth/login", "{\"login\":\"a\",\"password\":\"b\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void jwksPublishesOnlyThePublicKey() {
        MvcTestResult jwks = mvc.get().uri("/.well-known/jwks.json").exchange();

        assertThat(jwks).hasStatusOk();
        assertThat(jwks).bodyJson().extractingPath("$.keys[0].kty").isEqualTo("RSA");
        assertThat(jwks).bodyText().doesNotContain("\"d\"");   // "d" is the RSA private exponent
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
