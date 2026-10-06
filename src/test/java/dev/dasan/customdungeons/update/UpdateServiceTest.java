package dev.dasan.customdungeons.update;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class UpdateServiceTest {
    @TempDir Path directory;
    final AtomicLong clock = new AtomicLong(1000);
    final UpdateService.Settings settings = new UpdateService.Settings(true, "Dasannn/CustomDungeons", URI.create("https://api.github.com/"));
    final List<URI> requests = new ArrayList<>();
    String version = "1.0.2";
    String assetName = "CustomDungeons-1.0.2.jar";
    String assetUrl = "https://github.com/download.jar";
    byte[] jar;
    byte[] signature;
    KeyPair pair;
    String descriptor = "name: CustomDungeons\nversion: '1.0.2'\n";
    void sign() throws Exception {
        pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        jar = JarInspectorTest.jar(descriptor);
        var signer = Signature.getInstance("Ed25519"); signer.initSign(pair.getPrivate()); signer.update(jar);
        signature = signer.sign();
    }
    UpdateService service() throws Exception {
        if (jar == null) sign();
        return service(new SignatureVerifier(pair.getPublic()));
    }
    UpdateService service(SignatureVerifier verifier) throws Exception {
        return service(verifier, (source, target) -> Files.move(source, target,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING));
    }
    UpdateService service(SignatureVerifier verifier, UpdateService.AtomicMover mover) throws Exception {
        return new UpdateService("1.0.1", directory.resolve("update/CustomDungeons.jar"),
                verifier, (uri, max) -> {
                    requests.add(uri);
                    if (uri.getPath().endsWith("/latest")) return ("""
                            {"tag_name":"v%s","html_url":"https://github.com/notes","assets":[
                            {"name":"%s","size":%d,"browser_download_url":"%s"},
                            {"name":"%s.sig","size":64,"browser_download_url":"https://github.com/download.sig"}]}
                            """).formatted(version, assetName, jar.length, assetUrl, assetName).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    return uri.getPath().endsWith(".sig") ? signature : jar;
                }, clock::get, mover);
    }
    String key(java.util.concurrent.CompletableFuture<UpdateService.Result> future) { return future.join().key(); }
    void assertNothingInstalled() throws Exception {
        assertFalse(Files.exists(directory.resolve("update/CustomDungeons.jar")));
        if (Files.exists(directory.resolve("update"))) {
            try (var files = Files.list(directory.resolve("update"))) { assertEquals(0, files.count()); }
        }
    }
    @Test void prepareDoesNotDownloadAndConfirmationStagesVerifiedJar() throws Exception {
        try (var service = service()) {
            assertEquals("update.available", key(service.check(settings)));
            assertEquals("update.confirm-required", key(service.prepare("console", settings)));
            assertEquals(2, requests.size());
            assertEquals("update.expired", key(service.confirm("other", settings)));
            clock.addAndGet(60_000);
            assertEquals("update.staged", key(service.confirm("console", settings)));
            assertArrayEquals(jar, Files.readAllBytes(directory.resolve("update/CustomDungeons.jar")));
            assertEquals("update.expired", key(service.confirm("console", settings)));
        }
    }
    @Test void checkReportsNewVersionWithoutRequiringDownloadableAssets() throws Exception {
        assetName = "unexpected.jar";
        try (var service = service()) {
            assertEquals("update.available", key(service.check(settings)));
            assertEquals(1, requests.size());
            assertEquals("update.invalid-release", key(service.prepare("console", settings)));
            assertNothingInstalled();
        }
    }
    @Test void expiresAfterSixtySeconds() throws Exception {
        try (var service = service()) {
            service.prepare("console", settings).join(); clock.addAndGet(60_001);
            assertEquals("update.expired", key(service.confirm("console", settings)));
            assertEquals(1, requests.size()); assertNothingInstalled();
        }
    }
    @Test void noDownloadOrConfirmationWhenCurrentOrOlderIncludingBuildMetadata() throws Exception {
        for (String remote : List.of("1.0.1", "1.0.0", "1.0.1+build", "1.0.1-rc.1")) {
            version = remote;
            try (var service = service()) {
                requests.clear();
                assertEquals("update.up-to-date", key(service.prepare("console", settings)));
                assertEquals("update.expired", key(service.confirm("console", settings)));
                assertEquals(1, requests.size()); assertNothingInstalled();
            }
        }
    }
    @Test void signatureMustPassBeforeStagingAndTempIsRemoved() throws Exception {
        sign(); jar[jar.length - 1] ^= 1;
        try (var service = service()) {
            service.prepare("console", settings).join();
            assertEquals("update.invalid-signature", key(service.confirm("console", settings)));
            assertNothingInstalled();
        }
    }
    @Test void validSignatureDoesNotBypassDescriptorValidation() throws Exception {
        descriptor = "name: Other\nversion: '1.0.2'\n";
        try (var service = service()) {
            service.prepare("console", settings).join();
            assertEquals("update.invalid-jar", key(service.confirm("console", settings)));
            assertNothingInstalled();
        }
    }
    @Test void rejectsUntrustedNamesHttpAndOversizedAssets() throws Exception {
        for (int scenario = 0; scenario < 3; scenario++) {
            assetName = scenario == 0 ? "../CustomDungeons-1.0.2.jar" : "CustomDungeons-1.0.2.jar";
            assetUrl = scenario == 1 ? "http://github.com/download.jar" : "https://github.com/download.jar";
            sign();
            if (scenario == 2) jar = new byte[UpdateService.MAX_JAR_BYTES + 1];
            try (var service = service()) {
                requests.clear();
                assertNotEquals("update.confirm-required", key(service.prepare("console", settings)));
                assertEquals(1, requests.size()); assertNothingInstalled();
            }
        }
    }
    @Test void disabledAndInsecureApiPerformNoNetwork() throws Exception {
        try (var service = service()) {
            assertEquals("update.disabled", key(service.check(new UpdateService.Settings(false, settings.repository(), settings.apiBase()))));
            assertEquals("update.insecure-url", key(service.check(new UpdateService.Settings(true, settings.repository(), URI.create("http://localhost/")))));
            assertEquals("update.invalid-release", key(service.check(new UpdateService.Settings(true, "../evil", settings.apiBase()))));
            assertTrue(requests.isEmpty());
        }
    }
    @Test void busyServiceRejectsAdditionalWorkAndClosePreventsStaging() throws Exception {
        sign();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var service = new UpdateService("1.0.1", directory.resolve("update/CustomDungeons.jar"),
                new SignatureVerifier(pair.getPublic()), (uri, max) -> {
                    entered.countDown(); release.await(); return new byte[0];
                }, clock::get)) {
            var first = service.check(settings);
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("update.busy", key(service.check(settings)));
            service.close();
            assertEquals("update.disabled", key(service.confirm("console", settings)));
            assertEquals("update.disabled", first.get(5, java.util.concurrent.TimeUnit.SECONDS).key());
            assertNothingInstalled();
        } finally { release.countDown(); }
    }
    @Test void failuresPreservePreviouslyStagedJar() throws Exception {
        sign(); signature[0] ^= 1;
        Path target = directory.resolve("update/CustomDungeons.jar");
        Files.createDirectories(target.getParent()); Files.writeString(target, "previously verified");
        try (var service = service()) {
            service.prepare("console", settings).join();
            assertEquals("update.invalid-signature", key(service.confirm("console", settings)));
            assertEquals("previously verified", Files.readString(target));
        }
    }
    @Test void replacingVerifiedFileCannotInstallUnsignedJar() throws Exception {
        sign();
        byte[] replacement = JarInspectorTest.jar(descriptor + "# unsigned replacement\n");
        var verifier = org.mockito.Mockito.spy(new SignatureVerifier(pair.getPublic()));
        Path installed = directory.resolve("update/CustomDungeons.jar");
        org.mockito.Mockito.doAnswer(invocation -> {
            boolean valid = (boolean) invocation.callRealMethod();
            // No verified temporary exists anymore. A local actor can still replace the destination.
            assertFalse(Files.exists(directory.resolve("update-tmp")));
            Files.createDirectories(installed.getParent()); Files.write(installed, replacement);
            return valid;
        }).when(verifier).verify(org.mockito.ArgumentMatchers.any(byte[].class), org.mockito.ArgumentMatchers.any(byte[].class));
        try (var service = service(verifier)) {
            service.prepare("console", settings).join();
            assertEquals("update.staged", key(service.confirm("console", settings)));
            byte[] staged = Files.readAllBytes(installed);
            assertArrayEquals(jar, staged);
            assertTrue(new SignatureVerifier(pair.getPublic()).verify(staged, signature));
            assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(installed));
        }
    }
    @Test void replacementBeforeAtomicMoveFailsHashCheckAndIsRemoved() throws Exception {
        replacementAtMoveIsRejected("before");
    }
    @Test void replacementAfterAtomicMoveFailsHashCheckAndIsRemoved() throws Exception {
        replacementAtMoveIsRejected("after");
    }
    @Test void symlinkAfterAtomicMoveIsRemovedWithoutFollowingIt() throws Exception {
        replacementAtMoveIsRejected("symlink");
    }
    private void replacementAtMoveIsRejected(String attack) throws Exception {
        sign();
        byte[] replacement = JarInspectorTest.jar(descriptor + "# unsigned replacement\n");
        assertFalse(new SignatureVerifier(pair.getPublic()).verify(replacement, signature));
        Path victim = directory.resolve("outside-update.jar"); Files.write(victim, replacement);
        var intercepted = new java.util.concurrent.atomic.AtomicBoolean();
        try (var service = service(new SignatureVerifier(pair.getPublic()), (source, target) -> {
            assertEquals(target.getParent(), source.getParent());
            assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(source));
            assertArrayEquals(jar, Files.readAllBytes(source));
            if (attack.equals("before")) Files.write(source, replacement);
            Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (attack.equals("after")) Files.write(target, replacement);
            if (attack.equals("symlink")) { Files.delete(target); Files.createSymbolicLink(target, victim); }
            intercepted.set(true);
        })) {
            service.prepare("console", settings).join();
            assertEquals("update.io-failed", key(service.confirm("console", settings)));
            assertTrue(intercepted.get());
            assertArrayEquals(replacement, Files.readAllBytes(victim));
            assertNothingInstalled();
            try (var files = Files.list(directory.resolve("update"))) { assertEquals(0, files.count()); }
        }
    }
    @Test void changedSettingsInvalidateConfirmation() throws Exception {
        try (var service = service()) {
            service.prepare("console", settings).join();
            assertEquals("update.expired", key(service.confirm("console", new UpdateService.Settings(true, "other/repo", settings.apiBase()))));
            assertEquals(1, requests.size()); assertNothingInstalled();
        }
    }
}
