package org.example.healthcare.common;

import java.util.Map;

/**
 * Maps Stripe error codes to safe, user-facing messages.
 *
 * <p>Raw Stripe error messages may contain card details, bank names, or other
 * information that must not be displayed directly to patients. All Stripe error
 * messages passed to the frontend must go through this mapper.
 *
 * <p>Stripe error code reference:
 * https://stripe.com/docs/error-codes
 */
public final class StripeErrorMapper {

    private StripeErrorMapper() {}

    /** Returns a safe, patient-facing error message for the given Stripe decline code. */
    public static String toUserMessage(String stripeCode) {
        if (stripeCode == null) return GENERIC_ERROR;
        return CODE_MAP.getOrDefault(stripeCode.toLowerCase(), GENERIC_ERROR);
    }

    private static final String GENERIC_ERROR =
            "Plata nu a putut fi procesată. Vă rugăm încercați alt card sau contactați banca. " +
            "(Payment could not be processed. Please try another card or contact your bank.)";

    private static final Map<String, String> CODE_MAP = Map.ofEntries(
            Map.entry("card_declined",
                    "Cardul a fost refuzat. Verificați detaliile sau contactați banca."),
            Map.entry("insufficient_funds",
                    "Fonduri insuficiente pe card. Vă rugăm utilizați un alt card."),
            Map.entry("expired_card",
                    "Cardul este expirat. Vă rugăm utilizați un card valid."),
            Map.entry("incorrect_cvc",
                    "Codul CVC incorect. Verificați codul de pe spatele cardului."),
            Map.entry("incorrect_number",
                    "Numărul de card incorect. Verificați datele introduse."),
            Map.entry("do_not_honor",
                    "Banca a refuzat tranzacția. Contactați banca pentru detalii."),
            Map.entry("fraudulent",
                    "Tranzacția a fost blocată din motive de securitate."),
            Map.entry("lost_card",
                    "Cardul este declarat pierdut. Contactați banca."),
            Map.entry("stolen_card",
                    "Cardul este declarat furat. Contactați banca."),
            Map.entry("processing_error",
                    "Eroare de procesare. Vă rugăm reîncercați."),
            Map.entry("authentication_required",
                    "Autentificare suplimentară necesară. Vă rugăm urmați instrucțiunile băncii."),
            Map.entry("currency_not_supported",
                    "Moneda RON nu este acceptată de acest card."),
            Map.entry("generic_decline",
                    "Plata a fost refuzată. Contactați banca sau utilizați un alt card.")
    );
}
