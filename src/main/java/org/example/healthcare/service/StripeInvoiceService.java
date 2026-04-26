package org.example.healthcare.service;

import java.util.UUID;

/**
 * Stub for Stripe Invoices / Stripe Tax integration.
 *
 * <p>TODO: Stripe Invoices in Phase 2
 *
 * <p>Phase 2 implementation will:
 * <ul>
 *   <li>Create a Stripe Customer for each patient (stored as {@code users.stripe_customer_id}).</li>
 *   <li>Create an InvoiceItem for each consultation.</li>
 *   <li>Finalize and send the Invoice via Stripe, storing {@code hosted_invoice_url}.</li>
 *   <li>Handle Romanian VAT via Stripe Tax (21% standard rate for digital services).</li>
 *   <li>Generate PDF invoices for download from {@code GET /patients/{id}/invoices/{invoiceId}}.</li>
 * </ul>
 *
 * <p>v1 behaviour: Stripe sends an automatic email receipt via the
 * {@code receipt_email} field on the PaymentIntent. No separate invoice is created.
 */
public interface StripeInvoiceService {

    // TODO: Stripe Invoices in Phase 2
    // String createAndFinalizeInvoice(UUID bookingId);

    // TODO: Stripe Invoices in Phase 2
    // String getInvoiceUrl(UUID bookingId);

    // TODO: Stripe Tax in Phase 2 — Romanian VAT handling
    // void applyTax(String customerId, String invoiceId);
}
