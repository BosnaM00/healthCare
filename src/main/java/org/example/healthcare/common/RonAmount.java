package org.example.healthcare.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value object representing a monetary amount in Romanian leu (RON).
 *
 * <p>Stripe expects amounts in the smallest currency unit (bani, 1 RON = 100 bani).
 * This class provides a single, tested conversion path so no other code ever converts
 * RON to bani manually.
 *
 * <p>Rules enforced by this class:
 * <ul>
 *   <li>Never use {@code double} or {@code float} for money — only {@link BigDecimal} and {@code long}.</li>
 *   <li>Rounding mode is {@link RoundingMode#HALF_UP} for consistent results.</li>
 *   <li>Negative amounts are rejected at construction time.</li>
 *   <li>Scale is normalised to 2 decimal places on input.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * RonAmount amount = RonAmount.of(new BigDecimal("150.00"));
 * long bani = amount.toBani();          // 15000
 * BigDecimal ron = amount.toBigDecimal(); // 150.00
 * }</pre>
 */
public final class RonAmount {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal BANI_PER_RON = HUNDRED;

    private final BigDecimal value;

    private RonAmount(BigDecimal value) {
        this.value = value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Creates a {@code RonAmount} from a {@link BigDecimal} in major units (RON).
     *
     * @param ron amount in RON; must be non-null and non-negative
     * @throws IllegalArgumentException if {@code ron} is null or negative
     */
    public static RonAmount of(BigDecimal ron) {
        if (ron == null) throw new IllegalArgumentException("RON amount must not be null");
        if (ron.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("RON amount must not be negative: " + ron);
        return new RonAmount(ron);
    }

    /**
     * Creates a {@code RonAmount} from an amount already expressed in bani (minor units).
     *
     * @param bani amount in bani; must be non-negative
     * @throws IllegalArgumentException if {@code bani} is negative
     */
    public static RonAmount ofBani(long bani) {
        if (bani < 0) throw new IllegalArgumentException("Bani amount must not be negative: " + bani);
        return new RonAmount(BigDecimal.valueOf(bani).divide(BANI_PER_RON, 2, RoundingMode.HALF_UP));
    }

    /**
     * Converts to Stripe minor units (bani).
     *
     * <p>Uses {@link BigDecimal#movePointRight(int)} with {@link RoundingMode#HALF_UP} scale
     * as mandated by the project's money-handling rules.
     *
     * @return the amount in bani as a {@code long}
     * @throws ArithmeticException if the conversion would lose precision beyond 2 decimal places
     */
    public long toBani() {
        return value.movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
    }

    /**
     * Returns the amount in RON as a {@link BigDecimal} with 2 decimal places.
     */
    public BigDecimal toBigDecimal() {
        return value;
    }

    /**
     * Computes the platform fee in bani given a percentage (0–100).
     *
     * @param feePct fee percentage, e.g. {@code 15} for 15%
     * @return fee amount in bani
     */
    public long computeFeeBani(int feePct) {
        if (feePct < 0 || feePct > 100)
            throw new IllegalArgumentException("Fee percentage must be 0–100, got: " + feePct);
        BigDecimal fee = value
                .multiply(BigDecimal.valueOf(feePct))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return fee.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Returns a new {@code RonAmount} representing {@code this - other}.
     *
     * @throws IllegalArgumentException if the result would be negative
     */
    public RonAmount subtract(RonAmount other) {
        BigDecimal result = this.value.subtract(other.value);
        if (result.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Subtraction would produce a negative amount");
        return new RonAmount(result);
    }

    /** Returns a new {@code RonAmount} representing {@code this + other}. */
    public RonAmount add(RonAmount other) {
        return new RonAmount(this.value.add(other.value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RonAmount other)) return false;
        return value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString() + " RON";
    }
}
