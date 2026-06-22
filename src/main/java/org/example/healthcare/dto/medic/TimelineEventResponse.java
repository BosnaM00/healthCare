package org.example.healthcare.dto.medic;

import java.time.Instant;

/**
 * A single entry on the patient's clinical timeline. Derived from existing records
 * (bookings, consultations, prescriptions) rather than a dedicated table.
 *
 * <p>{@code type} is one of CONSULTATION | PRESCRIPTION | LAB_RESULT | DOCUMENT | BOOKING,
 * matching the frontend {@code TimelineEvent} union.
 */
public record TimelineEventResponse(
        String id,
        String type,
        Instant timestamp,
        String title,
        String description,
        String relatedId
) {}
