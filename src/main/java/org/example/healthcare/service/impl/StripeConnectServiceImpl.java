package org.example.healthcare.service.impl;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.LoginLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.stripe.StripeAccountStatusResponse;
import org.example.healthcare.dto.stripe.StripeOnboardingResponse;
import org.example.healthcare.model.MedicStripeAccount;
import org.example.healthcare.model.StripeAccountStatus;
import org.example.healthcare.repository.MedicRepository;
import org.example.healthcare.repository.MedicStripeAccountRepository;
import org.example.healthcare.service.AuditLogService;
import org.example.healthcare.service.StripeConnectService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implements Stripe Connect Express onboarding for individual medics.
 *
 * <p>Flow:
 * <ol>
 *   <li>Medic calls {@code POST /medics/me/stripe/onboarding} → {@link #createOnboardingLink}.</li>
 *   <li>Browser is redirected to the Stripe-hosted onboarding page.</li>
 *   <li>On completion, Stripe redirects back to {@code connect.return-url}.</li>
 *   <li>Frontend polls {@code GET /medics/me/stripe/status} to get updated flags.</li>
 *   <li>The {@code account.updated} webhook also updates flags asynchronously.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StripeConnectServiceImpl implements StripeConnectService {

    @Value("${stripe.connect.return-url}")
    private String returnUrl;

    @Value("${stripe.connect.refresh-url}")
    private String refreshUrl;

    private final MedicRepository              medicRepository;
    private final MedicStripeAccountRepository medicStripeAccountRepository;
    private final AuditLogService              auditLogService;

    @Override
    @Transactional
    public StripeOnboardingResponse createOnboardingLink(UUID medicId) {
        var medic = medicRepository.findById(medicId)
                .orElseThrow(() -> new ResourceNotFoundException("Medic", medicId));

        // Reuse existing account if already created
        MedicStripeAccount account = medicStripeAccountRepository.findById(medicId)
                .orElse(null);

        if (account == null) {
            // Create a new Express account on Stripe
            String stripeAccountId = createExpressAccount(medic.getUser().getEmail());
            account = MedicStripeAccount.builder()
                    .medicId(medicId)
                    .medic(medic)
                    .stripeAccountId(stripeAccountId)
                    .accountStatus(StripeAccountStatus.PENDING)
                    .build();
            medicStripeAccountRepository.save(account);

            auditLogService.log(medicId, "STRIPE_ACCOUNT_CREATED", "MedicStripeAccount",
                    medicId, null, "stripeAccountId=" + stripeAccountId, null);
            log.info("Created Stripe Express account {} for medic {}", stripeAccountId, medicId);
        }

        return buildOnboardingLink(account.getStripeAccountId());
    }

    @Override
    @Transactional
    public StripeOnboardingResponse refreshOnboardingLink(UUID medicId) {
        MedicStripeAccount account = medicStripeAccountRepository.findById(medicId)
                .orElseThrow(() -> new BusinessException("No Stripe account found for medic " + medicId));
        return buildOnboardingLink(account.getStripeAccountId());
    }

    @Override
    @Transactional
    public StripeAccountStatusResponse refreshAccountStatus(UUID medicId) {
        MedicStripeAccount account = medicStripeAccountRepository.findById(medicId)
                .orElseThrow(() -> new BusinessException("No Stripe account found for medic " + medicId));

        try {
            Account stripeAccount = Account.retrieve(account.getStripeAccountId());
            updateAccountFlags(account,
                    stripeAccount.getChargesEnabled(),
                    stripeAccount.getPayoutsEnabled(),
                    stripeAccount.getDetailsSubmitted(),
                    null);
            medicStripeAccountRepository.save(account);
        } catch (StripeException e) {
            log.error("Failed to refresh Stripe account status for medic {}: {}", medicId, e.getMessage());
            throw new BusinessException("Could not refresh Stripe account status: " + e.getMessage());
        }

        return toStatusResponse(account);
    }

    @Override
    public String loginLink(UUID medicId) {
        MedicStripeAccount account = medicStripeAccountRepository.findById(medicId)
                .orElseThrow(() -> new BusinessException("No Stripe account found for medic " + medicId));

        if (!account.isChargesEnabled()) {
            throw new BusinessException("Medic Stripe account is not fully verified yet");
        }

        try {
            LoginLink link = LoginLink.createOnAccount(
                    account.getStripeAccountId(),
                    com.stripe.param.LoginLinkCreateOnAccountParams.builder().build(),
                    null);
            return link.getUrl();
        } catch (StripeException e) {
            log.error("Failed to create login link for Stripe account {}: {}", account.getStripeAccountId(), e.getMessage());
            throw new BusinessException("Could not generate Stripe dashboard link");
        }
    }

    @Override
    @Transactional
    public void syncAccountFromWebhook(String stripeAccountId,
                                       boolean chargesEnabled,
                                       boolean payoutsEnabled,
                                       boolean detailsSubmitted,
                                       String requirementsDueJson) {
        medicStripeAccountRepository.findByStripeAccountId(stripeAccountId)
                .ifPresentOrElse(
                        account -> {
                            updateAccountFlags(account, chargesEnabled, payoutsEnabled,
                                    detailsSubmitted, requirementsDueJson);
                            medicStripeAccountRepository.save(account);
                            log.info("Synced Stripe account {} via webhook: charges={}, payouts={}, details={}",
                                    stripeAccountId, chargesEnabled, payoutsEnabled, detailsSubmitted);
                        },
                        () -> log.warn("Received account.updated for unknown Stripe account {}; ignoring",
                                stripeAccountId)
                );
    }

    @Override
    @Transactional
    public void deauthorizeAccount(String stripeAccountId) {
        medicStripeAccountRepository.findByStripeAccountId(stripeAccountId)
                .ifPresentOrElse(
                        account -> {
                            account.setAccountStatus(StripeAccountStatus.REJECTED);
                            medicStripeAccountRepository.save(account);
                            auditLogService.log(null, "STRIPE_ACCOUNT_DEAUTHORIZED",
                                    "MedicStripeAccount", account.getMedicId(),
                                    "status=VERIFIED", "status=REJECTED", null);
                            log.warn("Stripe account {} deauthorized — medic {} marked REJECTED",
                                    stripeAccountId, account.getMedicId());
                        },
                        () -> log.warn("Received account.application.deauthorized for unknown account {}",
                                stripeAccountId)
                );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createExpressAccount(String email) {
        try {
            AccountCreateParams params = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setCountry("RO")
                    .setDefaultCurrency("ron")
                    .setEmail(email)
                    .setCapabilities(AccountCreateParams.Capabilities.builder()
                            .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder()
                                    .setRequested(true).build())
                            .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                                    .setRequested(true).build())
                            .build())
                    .build();
            return Account.create(params).getId();
        } catch (StripeException e) {
            log.error("Failed to create Stripe Express account for {}: {}", email, e.getMessage());
            throw new BusinessException("Could not create Stripe account: " + e.getMessage());
        }
    }

    private StripeOnboardingResponse buildOnboardingLink(String stripeAccountId) {
        try {
            AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                    .setAccount(stripeAccountId)
                    .setRefreshUrl(refreshUrl)
                    .setReturnUrl(returnUrl)
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build();
            AccountLink link = AccountLink.create(params);
            return new StripeOnboardingResponse(link.getUrl(), link.getExpiresAt());
        } catch (StripeException e) {
            log.error("Failed to create onboarding link for account {}: {}", stripeAccountId, e.getMessage());
            throw new BusinessException("Could not create Stripe onboarding link: " + e.getMessage());
        }
    }

    private void updateAccountFlags(MedicStripeAccount account,
                                    boolean chargesEnabled,
                                    boolean payoutsEnabled,
                                    boolean detailsSubmitted,
                                    String requirementsDueJson) {
        account.setChargesEnabled(chargesEnabled);
        account.setPayoutsEnabled(payoutsEnabled);
        account.setDetailsSubmitted(detailsSubmitted);
        if (requirementsDueJson != null) {
            account.setRequirementsCurrentlyDueJson(requirementsDueJson);
        }
        // Derive overall status from flags
        if (chargesEnabled && payoutsEnabled && detailsSubmitted) {
            account.setAccountStatus(StripeAccountStatus.VERIFIED);
        } else if (account.getAccountStatus() == StripeAccountStatus.REJECTED) {
            // Don't override REJECTED — deauthorize sets it permanently
        } else if (!detailsSubmitted) {
            account.setAccountStatus(StripeAccountStatus.PENDING);
        } else {
            account.setAccountStatus(StripeAccountStatus.RESTRICTED);
        }
    }

    private StripeAccountStatusResponse toStatusResponse(MedicStripeAccount account) {
        return new StripeAccountStatusResponse(
                account.getStripeAccountId(),
                account.isChargesEnabled(),
                account.isPayoutsEnabled(),
                account.isDetailsSubmitted(),
                account.getAccountStatus(),
                account.getRequirementsCurrentlyDueJson(),
                account.getLastSyncedAt());
    }
}
