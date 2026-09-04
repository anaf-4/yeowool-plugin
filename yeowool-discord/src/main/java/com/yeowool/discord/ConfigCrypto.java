package com.yeowool.discord;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM helper for the one secret {@link DiscordConfigSync} pushes into the shared DB
 * ({@code bot-token}) — every other synced value (guild/channel/role ids) is just a Discord
 * snowflake, not a credential, so only the token is worth encrypting. Ciphertext layout is a
 * single base64 string: {@code iv(12 bytes) || ciphertext+tag}, matching Node's {@code crypto}
 * GCM convention (tag appended to the ciphertext) so the standalone JS bot can decrypt it with
 * the same key without needing a Java-specific format.
 */
final class ConfigCrypto {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private ConfigCrypto() {
    }

    static String encrypt(String plaintext, String base64Key) {
        try {
            SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "설정 값 암호화 실패 - config-encryption-key가 올바른 32바이트(base64) 키인지 확인하세요.", e);
        }
    }
}
