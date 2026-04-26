package org.example.healthcare.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RonAmount value object")
class RonAmountTest {

    // ── Construction ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("of(BigDecimal)")
    class OfBigDecimal {

        @Test
        @DisplayName("creates from exact two-decimal value")
        void exactValue() {
            RonAmount amount = RonAmount.of(new BigDecimal("150.00"));
            assertThat(amount.toBigDecimal()).isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("normalises scale to 2 decimal places")
        void normalisesScale() {
            RonAmount amount = RonAmount.of(new BigDecimal("150"));
            assertThat(amount.toBigDecimal()).isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("rounds to 2dp with HALF_UP")
        void halfUpRounding() {
            RonAmount amount = RonAmount.of(new BigDecimal("1.235"));
            assertThat(amount.toBigDecimal()).isEqualByComparingTo("1.24");
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> RonAmount.of(null))
                    .withMessageContaining("null");
        }

        @Test
        @DisplayName("rejects negative value")
        void rejectsNegative() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> RonAmount.of(new BigDecimal("-0.01")))
                    .withMessageContaining("negative");
        }

        @Test
        @DisplayName("accepts zero")
        void acceptsZero() {
            assertThat(RonAmount.of(BigDecimal.ZERO).toBani()).isZero();
        }
    }

    @Nested
    @DisplayName("ofBani(long)")
    class OfBani {

        @Test
        @DisplayName("converts 15000 bani to 150.00 RON")
        void convert15000() {
            RonAmount amount = RonAmount.ofBani(15000L);
            assertThat(amount.toBigDecimal()).isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("converts 1 bani to 0.01 RON")
        void convertOneBani() {
            RonAmount amount = RonAmount.ofBani(1L);
            assertThat(amount.toBigDecimal()).isEqualByComparingTo("0.01");
        }

        @Test
        @DisplayName("rejects negative bani")
        void rejectsNegative() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> RonAmount.ofBani(-1L))
                    .withMessageContaining("negative");
        }
    }

    // ── toBani() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toBani()")
    class ToBani {

        @ParameterizedTest(name = "{0} RON = {1} bani")
        @CsvSource({
            "0.00,       0",
            "0.01,       1",
            "1.00,      100",
            "150.00,  15000",
            "300.50,  30050",
            "999.99,  99999",
        })
        @DisplayName("converts standard amounts correctly")
        void standardAmounts(String ron, long expectedBani) {
            assertThat(RonAmount.of(new BigDecimal(ron)).toBani()).isEqualTo(expectedBani);
        }

        @Test
        @DisplayName("round-trips from bani back to bani")
        void roundTrip() {
            long original = 12345L;
            assertThat(RonAmount.ofBani(original).toBani()).isEqualTo(original);
        }
    }

    // ── computeFeeBani() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("computeFeeBani(int)")
    class ComputeFeeBani {

        @Test
        @DisplayName("15% of 150.00 RON = 2250 bani")
        void independentFee() {
            RonAmount amount = RonAmount.of(new BigDecimal("150.00"));
            assertThat(amount.computeFeeBani(15)).isEqualTo(2250L);
        }

        @Test
        @DisplayName("10% of 200.00 RON = 2000 bani")
        void clinicFee() {
            RonAmount amount = RonAmount.of(new BigDecimal("200.00"));
            assertThat(amount.computeFeeBani(10)).isEqualTo(2000L);
        }

        @Test
        @DisplayName("0% fee = 0 bani")
        void zeroFee() {
            RonAmount amount = RonAmount.of(new BigDecimal("150.00"));
            assertThat(amount.computeFeeBani(0)).isZero();
        }

        @Test
        @DisplayName("100% fee = full amount in bani")
        void fullFee() {
            RonAmount amount = RonAmount.of(new BigDecimal("10.00"));
            assertThat(amount.computeFeeBani(100)).isEqualTo(1000L);
        }

        @Test
        @DisplayName("rejects negative percentage")
        void rejectsNegativePct() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> RonAmount.of(new BigDecimal("100")).computeFeeBani(-1));
        }

        @Test
        @DisplayName("rejects percentage over 100")
        void rejectsOver100() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> RonAmount.of(new BigDecimal("100")).computeFeeBani(101));
        }
    }

    // ── Arithmetic ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("subtract()")
    class Subtract {

        @Test
        @DisplayName("150.00 - 22.50 = 127.50 RON")
        void subtractFee() {
            RonAmount gross = RonAmount.of(new BigDecimal("150.00"));
            RonAmount fee   = RonAmount.of(new BigDecimal("22.50"));
            assertThat(gross.subtract(fee).toBigDecimal()).isEqualByComparingTo("127.50");
        }

        @Test
        @DisplayName("subtracting equal amounts gives zero")
        void subtractToZero() {
            RonAmount a = RonAmount.of(new BigDecimal("100.00"));
            assertThat(a.subtract(a).toBani()).isZero();
        }

        @Test
        @DisplayName("rejects subtraction producing negative result")
        void rejectsNegativeResult() {
            RonAmount small = RonAmount.of(new BigDecimal("10.00"));
            RonAmount large = RonAmount.of(new BigDecimal("100.00"));
            assertThatIllegalArgumentException().isThrownBy(() -> small.subtract(large));
        }
    }

    @Nested
    @DisplayName("add()")
    class Add {

        @Test
        @DisplayName("50.00 + 100.00 = 150.00")
        void addAmounts() {
            RonAmount a = RonAmount.of(new BigDecimal("50.00"));
            RonAmount b = RonAmount.of(new BigDecimal("100.00"));
            assertThat(a.add(b).toBigDecimal()).isEqualByComparingTo("150.00");
        }
    }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Test
    @DisplayName("equal values with different scales are equal")
    void equalityWithDifferentScale() {
        RonAmount a = RonAmount.of(new BigDecimal("150.0"));
        RonAmount b = RonAmount.of(new BigDecimal("150.00"));
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("different values are not equal")
    void notEqual() {
        assertThat(RonAmount.of(new BigDecimal("150.00")))
                .isNotEqualTo(RonAmount.of(new BigDecimal("150.01")));
    }

    // ── toString ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString includes RON suffix")
    void toStringFormat() {
        assertThat(RonAmount.of(new BigDecimal("150.00")).toString()).isEqualTo("150.00 RON");
    }
}
