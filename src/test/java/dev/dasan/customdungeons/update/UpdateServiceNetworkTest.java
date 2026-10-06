package dev.dasan.customdungeons.update;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual HttpClient against a trusted local TLS endpoint; publishes nothing. */
class UpdateServiceNetworkTest {
    @TempDir Path directory;
    @Test void realHttpsDownloadRedirectSignatureAndBodyLimit() throws Exception {
        Path storeFile = directory.resolve("local.p12");
        var generator = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/keytool").toString(),
                "-genkeypair", "-alias", "local", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-storetype", "PKCS12",
                "-keystore", storeFile.toString(), "-storepass", "test-password", "-noprompt")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        assertTrue(generator.waitFor(20, TimeUnit.SECONDS)); assertEquals(0, generator.exitValue());
        var store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(storeFile)) { store.load(input, "test-password".toCharArray()); }
        var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); keys.init(store, "test-password".toCharArray());
        var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trust.init(store);
        var context = SSLContext.getInstance("TLS"); context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        var previous = SSLContext.getDefault();
        var server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context));
        var httpWorkers = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(httpWorkers);
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] jar = JarInspectorTest.jar("name: CustomDungeons\nversion: '1.0.2'\n");
        var signer = Signature.getInstance("Ed25519"); signer.initSign(pair.getPrivate()); signer.update(jar);
        byte[] signature = signer.sign();
        String base = "https://localhost:" + server.getAddress().getPort() + "/";
        var mode = new java.util.concurrent.atomic.AtomicReference<>("valid");
        var count = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/", exchange -> {
            count.incrementAndGet();
            assertTrue(exchange.getRequestHeaders().getFirst("User-Agent").startsWith("CustomDungeons/"));
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("latest")) {
                byte[] metadata = ("""
                        {"tag_name":"v1.0.2","html_url":"%snotes","assets":[
                        {"name":"CustomDungeons-1.0.2.jar","size":%d,"browser_download_url":"%sjar"},
                        {"name":"CustomDungeons-1.0.2.jar.sig","size":64,"browser_download_url":"%ssig"}]}
                        """).formatted(base, jar.length, base, base).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, metadata.length); exchange.getResponseBody().write(metadata);
            } else if (path.equals("/jar")) {
                if (mode.get().equals("oversized")) {
                    exchange.sendResponseHeaders(200, UpdateService.MAX_JAR_BYTES + 1L);
                } else if (mode.get().equals("slow")) {
                    exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write(jar, 0, 1); exchange.getResponseBody().flush();
                    try { Thread.sleep(12_000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                } else {
                    exchange.getResponseHeaders().add("Location", mode.get().equals("http") ? "http://localhost/file" : base + "file");
                    exchange.sendResponseHeaders(302, -1);
                }
            } else {
                byte[] bytes = path.equals("/sig") ? signature : jar;
                exchange.sendResponseHeaders(200, 0); // chunked
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        try {
            SSLContext.setDefault(context); server.start();
            var settings = new UpdateService.Settings(true, "test/repository", URI.create(base));
            for (String scenario : new String[]{"valid", "http", "oversized", "slow"}) {
                mode.set(scenario);
                Path destination = directory.resolve(scenario + "/CustomDungeons.jar");
                try (var service = new UpdateService("1.0.1", destination,
                        new SignatureVerifier(pair.getPublic()), null, () -> 0)) {
                    assertEquals("update.confirm-required", service.prepare("console", settings).get(15, TimeUnit.SECONDS).key());
                    String key = service.confirm("console", settings).get(15, TimeUnit.SECONDS).key();
                    assertEquals(switch (scenario) {
                        case "valid" -> "update.staged";
                        case "http" -> "update.insecure-url";
                        case "oversized" -> "update.too-large";
                        default -> "update.download-failed";
                    }, key, scenario);
                    assertEquals(scenario.equals("valid"), Files.exists(destination));
                    if (scenario.equals("valid")) assertArrayEquals(jar, Files.readAllBytes(destination));
                }
            }
            assertTrue(count.get() >= 9);
        } finally { server.stop(0); httpWorkers.shutdownNow(); SSLContext.setDefault(previous); }
    }
    @Test void chunkedBodiesAreBoundedAndCancelled() {
        var subscription = org.mockito.Mockito.mock(java.util.concurrent.Flow.Subscription.class);
        var body = new UpdateService.LimitedBody(3); body.onSubscribe(subscription);
        body.onNext(java.util.List.of(java.nio.ByteBuffer.wrap(new byte[]{1, 2})));
        body.onNext(java.util.List.of(java.nio.ByteBuffer.wrap(new byte[]{3, 4})));
        assertThrows(java.util.concurrent.CompletionException.class, () -> body.getBody().toCompletableFuture().join());
        org.mockito.Mockito.verify(subscription).cancel();
    }
}
