package org.example.healthcare.service.impl;

import org.example.healthcare.model.Medic;
import org.example.healthcare.model.VerificationStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class MedicSpecifications {

    private MedicSpecifications() {}

    public static Specification<Medic> verificationActive() {
        return (root, query, cb) ->
                cb.equal(root.get("verificationStatus"), VerificationStatus.ACTIVE);
    }

    public static Specification<Medic> hasSpecialty(UUID specialtyId) {
        return (root, query, cb) -> {
            var join = root.join("medicSpecialties");
            return cb.equal(join.get("specialty").get("id"), specialtyId);
        };
    }

    public static Specification<Medic> hasClinic(UUID clinicId) {
        return (root, query, cb) ->
                cb.equal(root.get("clinic").get("id"), clinicId);
    }

    public static Specification<Medic> isIndependent() {
        return (root, query, cb) -> cb.isNull(root.get("clinic"));
    }

    public static Specification<Medic> isInstant() {
        return (root, query, cb) ->
                cb.isTrue(root.get("availableForInstant"));
    }
}
