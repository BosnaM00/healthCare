package org.example.healthcare.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Thin facade over the Stripe Java SDK.
 *
 * <p>All methods are stubs — replace each TODO block with real Stripe SDK calls
 * once API keys and Stripe Connect are configured.
 *
 * <p>Currency: amounts are passed as {@link BigDecimal} in major units (RON / EUR).
 * The implementation converts to smallest units (bani / cents) before calling Stripe.
 */
@Service
public class StripePaymentService {

    @Value("${stripe.secret-key:sk_test_placeholder}")
    private String secretKey;

    /**
     * Creates a Stripe PaymentIntent in manual-capture mode (escrow).
     * Called at booking creation — no funds are captured yet.
     *
     * @param amount           charge amount in major currency units
     * @param currency         ISO 4217 code (e.g. "ron", "eur")
     * @param stripeCustomerId patient's Stripe customer ID
     * @return Stripe PaymentIntent ID (pi_xxx)
     */
    public String createPaymentIntent(BigDecimal amount, String currency, String stripeCustomerId) {
        // TODO: Stripe.apiKey = secretKey;
        // PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
        //     .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
        //     .setCurrency(currency)
        //     .setCustomer(stripeCustomerId)
        //     .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
        //     .build();
        // return PaymentIntent.create(params).getId();
        return "pi_stub_" + System.nanoTime();
    }

    /**
     * Captures the payment (moves funds from authorisation to escrow).
     * Called when consultation starts (IN_PROGRESS transition).
     *
     * @param paymentIntentId Stripe PaymentIntent ID
     */
    public void capturePaymentIntent(String paymentIntentId) {
        // TODO: PaymentIntent.retrieve(paymentIntentId).capture();
    }

    /**
     * Releases captured funds to the medic / clinic Stripe Connect account.
     * Called by PaymentReleaseJob after release_at passes.
     *
     * @param paymentIntentId Stripe PaymentIntent ID
     * @param stripeAccountId destination Stripe Connect account (medic or clinic)
     * @param netAmount       amount to transfer after platform fee deduction
     * @return Stripe Transfer ID (tr_xxx)
     */
    public String transferToAccount(String paymentIntentId, String stripeAccountId, BigDecimal netAmount) {
        // TODO: TransferCreateParams params = TransferCreateParams.builder()
        //     .setAmount(netAmount.multiply(BigDecimal.valueOf(100)).longValue())
        //     .setCurrency("ron")
        //     .setDestination(stripeAccountId)
        //     .setSourceTransaction(paymentIntentId)
        //     .build();
        // return Transfer.create(params).getId();
        return "tr_stub_" + System.nanoTime();
    }

    /**
     * Issues a full or partial refund on a captured PaymentIntent.
     * Called by PaymentService when a dispute resolves in the patient's favour
     * or on booking cancellation.
     *
     * @param paymentIntentId Stripe PaymentIntent ID
     * @param amount          amount to refund; null means full refund
     */
    public void refund(String paymentIntentId, BigDecimal amount) {
        // TODO: RefundCreateParams.Builder builder = RefundCreateParams.builder()
        //     .setPaymentIntent(paymentIntentId);
        // if (amount != null) builder.setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue());
        // Refund.create(builder.build());
    }

    /**
     * Cancels a PaymentIntent that has not yet been captured.
     * Called when a booking is cancelled before the consultation starts.
     *
     * @param paymentIntentId Stripe PaymentIntent ID
     */
    public void cancelPaymentIntent(String paymentIntentId) {
        // TODO: PaymentIntent.retrieve(paymentIntentId).cancel();
    }
}
