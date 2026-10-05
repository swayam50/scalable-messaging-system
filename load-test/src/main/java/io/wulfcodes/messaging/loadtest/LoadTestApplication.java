package io.wulfcodes.messaging.loadtest;

import io.wulfcodes.messaging.loadtest.config.LoadTestConfig;
import io.wulfcodes.messaging.loadtest.model.vo.StageResult;
import io.wulfcodes.messaging.loadtest.service.impl.HttpChatApiClient;
import io.wulfcodes.messaging.loadtest.service.impl.JwtTokenService;
import io.wulfcodes.messaging.loadtest.service.impl.LoadTestServiceImpl;
import io.wulfcodes.messaging.loadtest.util.PemUtil;
import io.wulfcodes.messaging.loadtest.util.ReportWriter;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.List;

/**
 * <pre>
 *   java -jar load-test.jar keygen [dir]      writes private.pem + public.pem
 *   java -jar load-test.jar run --chat-urls=http://localhost:8082,... --users=1000 --rates=500,1000,2000
 *                               [--warmup-seconds=5 --measure-seconds=20 --private-key=... --issuer=load-test]
 * </pre>
 * Start the chat nodes with AUTH_PUBLIC_KEY=$(cat public.pem) and AUTH_ISSUER=load-test.
 */
public final class LoadTestApplication {

    private LoadTestApplication() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("keygen")) {
            Path dir = Path.of(args.length > 1 ? args[1] : "load-test-keys");
            Files.createDirectories(dir);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keys = generator.generateKeyPair();
            Files.writeString(dir.resolve("private.pem"), PemUtil.toPem(keys.getPrivate()));
            Files.writeString(dir.resolve("public.pem"), PemUtil.toPem(keys.getPublic()));
            System.out.println("Wrote " + dir.resolve("private.pem") + " and " + dir.resolve("public.pem"));
            return;
        }

        LoadTestConfig config = LoadTestConfig.parse(args);
        RSAPrivateKey privateKey = (RSAPrivateKey) PemUtil.privateKey(Files.readString(config.privateKey()));
        RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privateKey;   // derive the public half from the private key
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        LoadTestServiceImpl loadTest = new LoadTestServiceImpl(config,
                new JwtTokenService(publicKey, privateKey, config.issuer()),
                new HttpChatApiClient(http, config.chatUrls()), http);

        List<StageResult> results = loadTest.run();
        String environment = Runtime.getRuntime().availableProcessors() + " CPU cores, "
                + config.chatUrls().size() + " chat nodes, everything on one machine";
        String markdown = ReportWriter.markdown(config, results, environment);
        ReportWriter.write(config, markdown);
        System.out.println();
        System.out.println(markdown);
        System.exit(0);
    }
}
