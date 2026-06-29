package org.example.healthcare.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EncryptionService (AES-256 GCM)")
class EncryptionServiceTest {

    /** A deterministic 32-byte key, base64-encoded, for AES-256. */
    private static final String KEY_32_BYTES =
            Base64.getEncoder().encodeToString(new byte[32]);

    private final EncryptionService service = new EncryptionService(KEY_32_BYTES);

    @Test
    @DisplayName("encrypt then decrypt returns the original plaintext")
    void roundTrip() {
        String plaintext = "Pacientul prezintă febră și tuse uscată.";
        String encrypted = service.encrypt(plaintext);

        assertThat(service.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("ciphertext differs from the plaintext")
    void ciphertextIsNotPlaintext() {
        String plaintext = "consultation notes";
        assertThat(service.encrypt(plaintext)).isNotEqualTo(plaintext);
    }

    @Test
    @DisplayName("encrypting the same text twice yields different blobs (random IV)")
    void nonDeterministicCiphertext() {
        String plaintext = "same input";
        assertThat(service.encrypt(plaintext)).isNotEqualTo(service.encrypt(plaintext));
    }

    @Test
    @DisplayName("round-trips an empty string")
    void roundTripsEmptyString() {
        assertThat(service.decrypt(service.encrypt(""))).isEmpty();
    }

    @Test
    @DisplayName("rejects a key that is not 32 bytes")
    void rejectsWrongKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EncryptionService(shortKey))
                .withMessageContaining("32-byte");
    }

    @Test
    @DisplayName("decrypting tampered ciphertext throws EncryptionException")
    void rejectsTamperedCiphertext() {
        String encrypted = service.encrypt("sensitive");

        // Flip a byte inside the ciphertext so GCM authentication fails.
        byte[] blob = Base64.getDecoder().decode(encrypted);
        blob[blob.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(blob);

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(EncryptionService.EncryptionException.class);
    }
}
