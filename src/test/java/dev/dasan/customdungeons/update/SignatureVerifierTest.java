package dev.dasan.customdungeons.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SignatureVerifierTest {
    @TempDir Path directory;
    @Test void validInvalidAndAlteredJar() throws Exception {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var jar = directory.resolve("sample.jar");
        Files.write(jar, new byte[]{1, 2, 3});
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate()); signer.update(Files.readAllBytes(jar));
        byte[] signature = signer.sign();
        var verifier = new SignatureVerifier(pair.getPublic());
        assertTrue(verifier.verify(Files.readAllBytes(jar), signature));
        byte[] corrupted = signature.clone(); corrupted[0] ^= 1;
        assertFalse(verifier.verify(Files.readAllBytes(jar), corrupted));
        assertFalse(verifier.verify(Files.readAllBytes(jar), new byte[63]));
        Files.write(jar, new byte[]{1, 2, 4});
        assertFalse(verifier.verify(Files.readAllBytes(jar), signature));
        assertNotNull(new SignatureVerifier());
    }
}
