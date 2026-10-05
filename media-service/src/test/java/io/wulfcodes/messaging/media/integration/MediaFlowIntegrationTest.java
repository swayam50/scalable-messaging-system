package io.wulfcodes.messaging.media.integration;

import com.jayway.jsonpath.JsonPath;
import io.wulfcodes.messaging.common.model.dto.AttachmentDescriptor;
import io.wulfcodes.messaging.common.util.AttachmentSigner;
import io.wulfcodes.messaging.media.service.spec.ConversationAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Real end-to-end upload flow against MinIO (Testcontainers) and PostgreSQL:
 * ticket -> multipart POST straight to MinIO -> complete -> signed descriptor -> download URL.
 * Membership checks against chat-service are mocked; JWTs are mocked ("token-alice" = ALICE).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class MediaFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    /** The upstream minio/minio image is no longer published; Chainguard's build is a drop-in. */
    @Container
    static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName.parse("cgr.dev/chainguard/minio:latest").asCompatibleSubstituteFor("minio/minio"))
            .withUserName("testuser").withPassword("testpassword");

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("media.storage.internal-endpoint", MINIO::getS3URL);
        registry.add("media.storage.public-endpoint", MINIO::getS3URL);
        registry.add("media.storage.access-key", MINIO::getUserName);
        registry.add("media.storage.secret-key", MINIO::getPassword);
        registry.add("media.storage.bucket", () -> "test-media");
    }

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private ConversationAccessService conversationAccess;   // everyone is a participant in this test

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private AttachmentSigner signer;
    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode(anyString())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            return Jwt.withTokenValue(token).header("alg", "RS256").subject(token.substring(6).toUpperCase())
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        });
    }

    @Test
    void uploadDirectlyToStorageThenCompleteThenDownload() throws Exception {
        byte[] png = "\u0089PNG fake image bytes".getBytes(StandardCharsets.ISO_8859_1);
        String ticket = createUpload("cat.png", "image/png", png.length);
        String attachmentId = JsonPath.read(ticket, "$.attachmentId");

        // completing before uploading is refused
        assertThat(post("/api/v1/media/" + attachmentId + "/complete", null)).hasStatus(HttpStatus.CONFLICT);

        // browser-style multipart POST straight to MinIO with the signed policy fields
        HttpResponse<String> upload = uploadToStorage(ticket, png, "image/png");
        assertThat(upload.statusCode()).isEqualTo(204);

        MvcTestResult completed = post("/api/v1/media/" + attachmentId + "/complete", null);
        assertThat(completed).hasStatusOk();
        AttachmentDescriptor descriptor = jsonMapper.readValue(completed.getResponse().getContentAsString(), AttachmentDescriptor.class);
        assertThat(descriptor.uploaderId()).isEqualTo("ALICE");
        assertThat(descriptor.size()).isEqualTo(png.length);
        assertThat(signer.isValid(descriptor)).isTrue();

        // presigned download returns the exact bytes
        String download = mvc.get().uri("/api/v1/media/" + attachmentId + "/download")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-alice").exchange().getResponse().getContentAsString();
        HttpResponse<byte[]> file = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(download, "$.url"))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(file.statusCode()).isEqualTo(200);
        assertThat(file.body()).isEqualTo(png);
    }

    @Test
    void storageRejectsAnUploadThatBreaksTheSignedPolicy() throws Exception {
        // ticket for a 10-byte PNG, then try to upload something with another Content-Type
        String ticket = createUpload("small.png", "image/png", 10);
        HttpResponse<String> sneaky = uploadToStorage(ticket, "<html>not an image</html>".getBytes(), "text/html");

        assertThat(sneaky.statusCode()).isEqualTo(403);   // policy condition failed, nothing stored
    }

    @Test
    void executableUploadsAreRefused() {
        MvcTestResult result = post("/api/v1/media/uploads",
                "{\"conversationId\":\"CONV\",\"fileName\":\"setup.exe\",\"mimeType\":\"application/x-msdownload\",\"size\":10}");
        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    private String createUpload(String fileName, String mime, long size) throws Exception {
        MvcTestResult result = post("/api/v1/media/uploads", """
                {"conversationId":"CONV","fileName":"%s","mimeType":"%s","size":%d}""".formatted(fileName, mime, size));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result.getResponse().getContentAsString();
    }

    private MvcTestResult post(String uri, String json) {
        var request = mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer token-alice");
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return request.exchange();
    }

    /** Builds the same multipart/form-data body a browser's FormData would send. */
    @SuppressWarnings("unchecked")
    private HttpResponse<String> uploadToStorage(String ticket, byte[] content, String contentType) throws Exception {
        Map<String, String> fields = JsonPath.read(ticket, "$.formFields");
        String boundary = "----test" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            String value = field.getKey().equals("Content-Type") ? contentType : field.getValue();
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
                    + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"upload\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(JsonPath.read(ticket, "$.uploadUrl")))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
