package org.example.healthcare.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256 GCM encryption service for PHI fields (consultation notes, prescriptions).
 *
 * <p>Output format: base64( IV(12 bytes) || ciphertext || authTag(16 bytes) )
 *
 * <p>Production note: replace {@code app.encryption.key} with an AWS KMS data-key fetch.
 * The KMS-encrypted data-key envelope should be stored alongside the ciphertext so the key
 * can be rotated without re-encrypting all records at once.
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH  = 12;   // 96-bit IV recommended for GCM
    private static final int    TAG_BITS   = 128;  // authentication tag length

    private final SecretKey secretKey;

    /**
     * @param base64Key base64-encoded 32-byte (256-bit) AES key.
     *                  Set via {@code app.encryption.key} in application.properties.
     *                  In production this value is injected from AWS Secrets Manager.
     */
    public EncryptionService(@Value("${app.encryption.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("AES-256 requires a 32-byte key; got " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts plaintext using AES-256 GCM.
     *
     * @param plaintext UTF-8 text to encrypt
     * @return base64-encoded ciphertext blob (IV prepended)
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            byte[] blob = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, blob, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, blob, IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(blob);
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a blob produced by {@link #encrypt(String)}.
     *
     * @param base64Blob base64-encoded IV + ciphertext
     * @return original plaintext
     */
    public String decrypt(String base64Blob) {
        try {
            byte[] blob = Base64.getDecoder().decode(base64Blob);
            byte[] iv   = new byte[IV_LENGTH];
            System.arraycopy(blob, 0, iv, 0, IV_LENGTH);

            byte[] ciphertext = new byte[blob.length - IV_LENGTH];
            System.arraycopy(blob, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed", e);
        }
    }

    /** Unchecked wrapper so callers don't need to handle checked crypto exceptions */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
