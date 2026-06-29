package org.example.healthcare.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StripeErrorMapper")
class StripeErrorMapperTest {

    @Test
    @DisplayName("maps a known decline code to its safe message")
    void mapsKnownCode() {
        assertThat(StripeErrorMapper.toUserMessage("insufficient_funds"))
                .isEqualTo("Fonduri insuficiente pe card. Vă rugăm utilizați un alt card.");
    }

    @Test
    @DisplayName("is case-insensitive on the code")
    void caseInsensitive() {
        assertThat(StripeErrorMapper.toUserMessage("CARD_DECLINED"))
                .isEqualTo(StripeErrorMapper.toUserMessage("card_declined"));
    }

    @Test
    @DisplayName("falls back to the generic message for an unknown code")
    void unknownCodeFallsBack() {
        String message = StripeErrorMapper.toUserMessage("some_unmapped_code");
        assertThat(message).contains("Payment could not be processed");
    }

    @Test
    @DisplayName("falls back to the generic message for a null code")
    void nullCodeFallsBack() {
        String message = StripeErrorMapper.toUserMessage(null);
        assertThat(message).contains("Payment could not be processed");
    }

    @Test
    @DisplayName("never returns a null message")
    void neverReturnsNull() {
        assertThat(StripeErrorMapper.toUserMessage("expired_card")).isNotNull();
        assertThat(StripeErrorMapper.toUserMessage(null)).isNotNull();
    }
}
