package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks the Stripe Connect Express account for an individual medic.
 *
 * <p>One row per medic; PK is the medic's UUID so joins are free.
 *
 * <p>Flags ({@code chargesEnabled}, {@code payoutsEnabled}, {@code detailsSubmitted})
 * are kept in sync by the {@code account.updated} webhook and by
 * {@code StripeConnectService.refreshAccountStatus()}.
 *
 * <p>Clinic-level Stripe accounts are stored on the {@link Clinic} entity directly
 * ({@code Clinic.stripeAccountId}); this entity is only for individual medics.
 */
@Entity
@Table(
        name = "medic_stripe_accounts",
        indexes = @Index(name = "idx_medic_stripe_accounts_account_id",
                         columnList = "stripe_account_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicStripeAccount {

    /** Same UUID as the owning {@link Medic} — no surrogate key needed. */
    @Id
    private UUID medicId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medic_id")
    private Medic medic;

    /** Stripe Express account ID (acct_…) */
    @Column(name = "stripe_account_id", nullable = false, unique = true)
    private String stripeAccountId;

    /** True when the connected account can accept charges */
    @Column(name = "charges_enabled", nullable = false)
    @Builder.Default
    private boolean chargesEnabled = false;

    /** True when the connected account can receive payouts */
    @Column(name = "payouts_enabled", nullable = false)
    @Builder.Default
    private boolean payoutsEnabled = false;

    /** True when the account holder has submitted all required details */
    @Column(name = "details_submitted", nullable = false)
    @Builder.Default
    private boolean detailsSubmitted = false;

    /** JSON array of currently-due requirements from Stripe (account.updated payload) */
    @Column(name = "requirements_currently_due_json", columnDefinition = "TEXT")
    private String requirementsCurrentlyDueJson;

    /** Derived status — computed from the three boolean flags on each sync */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    @Builder.Default
    private StripeAccountStatus accountStatus = StripeAccountStatus.PENDING;

    /** Last time flags were refreshed from Stripe (via webhook or explicit sync) */
    @UpdateTimestamp
    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;
}
