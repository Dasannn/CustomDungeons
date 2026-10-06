package dev.dasan.customdungeons.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SignReleaseTest {
    @TempDir Path directory;
    @Test void signingRejectsUnsafePrivateKeyPermissions() throws Exception {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path key = directory.resolve("test.key");
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        Path jar = directory.resolve("CustomDungeons-9.9.9.jar"); Files.writeString(jar, "local test bytes");
        // A fixture under the worktree keeps git discovery read-only; its signing key stays outside Git.
        Files.createDirectories(Path.of(".agent"));
        Path fixture = Files.createTempDirectory(Path.of(".agent"), "sign-permissions-").toAbsolutePath();
        try {
            Files.createDirectories(fixture.resolve("scripts")); Files.createDirectories(fixture.resolve("docs/reference"));
            Path script = fixture.resolve("scripts/sign-release.sh"); Files.copy(Path.of("scripts/sign-release.sh"), script);
            Files.writeString(fixture.resolve("docs/reference/release-signing.pub"), "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n");
            for (String mode : new String[]{"644", "640", "660", "604", "606", "777", "600", "400"}) {
                Files.setPosixFilePermissions(key, PosixFilePermissions.fromString(switch (mode) {
                    case "644" -> "rw-r--r--"; case "640" -> "rw-r-----"; case "660" -> "rw-rw----";
                    case "604" -> "rw----r--"; case "606" -> "rw----rw-"; case "777" -> "rwxrwxrwx";
                    case "600" -> "rw-------"; default -> "r--------";
                }));
                Path signature = Path.of(jar + ".sig"); Files.deleteIfExists(signature);
                var builder = new ProcessBuilder("bash", script.toString(), jar.toString()).redirectErrorStream(true);
                builder.environment().put("CD_SIGNING_KEY", key.toString());
                var process = builder.start(); assertTrue(process.waitFor(10, TimeUnit.SECONDS));
                String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (mode.equals("600") || mode.equals("400")) {
                    assertEquals(0, process.exitValue(), mode + ": " + output); assertEquals(64, Files.size(signature));
                } else {
                    assertNotEquals(0, process.exitValue(), "Insecure mode " + mode + " was accepted");
                    assertTrue(output.contains("permisos"), output); assertFalse(Files.exists(signature));
                }
            }
        } finally {
            try (var paths = Files.walk(fixture)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
