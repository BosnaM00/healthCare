package org.example.healthcare.repository;

import org.example.healthcare.model.MedicStripeAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicStripeAccountRepository extends JpaRepository<MedicStripeAccount, UUID> {

    Optional<MedicStripeAccount> findByStripeAccountId(String stripeAccountId);
}
