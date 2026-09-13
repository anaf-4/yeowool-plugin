package com.yeowool.discord;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConfigCrypto} only encrypts — the standalone Node bot decrypts the
 * bot token, not this plugin — so there's no Java-side {@code decrypt} to
 * round-trip through. Instead these tests decrypt the ciphertext by hand
 * (mirroring the documented {@code iv(12 bytes) || ciphertext+tag} layout
 * the Node bot relies on) to prove {@code encrypt} actually produces
 * something recoverable, not just an opaque string that happens to be valid
 * base64.
 */
class ConfigCryptoTest {

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static String decrypt(String base64Ciphertext, String base64Key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(base64Ciphertext);
        byte[] iv = new byte[12];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        byte[] ciphertext = new byte[combined.length - iv.length];
        System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    @Test
    void encryptedValueDecryptsBackToTheOriginalPlaintext() throws Exception {
        String key = randomBase64Key();
        String token = "MTIzNDU2Nzg5MA.GaBcDe.fake-discord-bot-token-value";

        String ciphertext = ConfigCrypto.encrypt(token, key);

        assertEquals(token, decrypt(ciphertext, key));
    }

    @Test
    void sameKeyAndPlaintextProduceDifferentCiphertextEachTime() {
        String key = randomBase64Key();
        String ciphertextA = ConfigCrypto.encrypt("same-token", key);
        String ciphertextB = ConfigCrypto.encrypt("same-token", key);

        assertNotEquals(ciphertextA, ciphertextB, "IV must be re-randomized per call, or repeated tokens leak a pattern");
    }

    @Test
    void invalidKeyLengthFailsWithGuidanceInsteadOfALeakyStackTrace() {
        String tooShortKey = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ConfigCrypto.encrypt("token", tooShortKey));
        assertTrue(e.getMessage().contains("config-encryption-key"));
    }
}
