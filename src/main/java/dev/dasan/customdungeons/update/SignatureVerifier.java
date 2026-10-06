package dev.dasan.customdungeons.update;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Pure Ed25519 over the jar bytes, without loading any downloaded code. */
public final class SignatureVerifier {
    private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAa1hJ44B6WJGJWOKTP5U4AqueDPovNSK/radPr/mEGL8=";
    private final PublicKey key;
    public SignatureVerifier() { this(releaseKey()); }
    SignatureVerifier(PublicKey key) { this.key = key; }
    private static PublicKey releaseKey() {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
        } catch (GeneralSecurityException error) { throw new IllegalStateException("Invalid embedded release key", error); }
    }
    public boolean verify(byte[] jar, byte[] detachedSignature) throws GeneralSecurityException {
        if (detachedSignature.length != 64) return false;
        var verifier = Signature.getInstance("Ed25519"); verifier.initVerify(key);
        verifier.update(jar);
        try { return verifier.verify(detachedSignature); }
        catch (java.security.SignatureException invalid) { return false; }
    }
}
