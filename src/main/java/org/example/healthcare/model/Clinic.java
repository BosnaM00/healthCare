package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registered clinic entity.
 *
 * <p>commission_rate — platform percentage taken on each payment release (e.g. 0.15 = 15%).
 * <p>stripe_account_id — clinic's Stripe Connect account for fund transfers.
 * <p>cui — Romanian business registration number (unique, validated externally).
 */
@Entity
@Table(
        name = "clinics",
        uniqueConstraints = @UniqueConstraint(name = "uk_clinics_cui", columnNames = "cui")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Clinic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Romanian business registration number */
    @Column(nullable = false, unique = true, length = 20)
    private String cui;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ClinicStatus status = ClinicStatus.PENDING_APPROVAL;

    /** Stripe Connect account ID — set after onboarding flow completes */
    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    /**
     * Platform commission rate as a decimal fraction (e.g. 0.15 = 15%).
     * Applied at payment release time to calculate platform_fee.
     */
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal commissionRate = new BigDecimal("0.1500");

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "clinic", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Medic> medics = new ArrayList<>();

    @OneToMany(mappedBy = "clinic", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MedicInvitation> invitations = new ArrayList<>();
}
