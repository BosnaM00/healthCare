package org.example.healthcare.dto.medic;

import java.time.Instant;

/**
 * A single vitals reading for a patient. There is no vitals table in the current
 * schema, so this endpoint returns an empty list; the record exists so the response
 * type matches the frontend {@code VitalReading} shape.
 */
public record VitalReadingResponse(
        String id,
        String patientId,
        Instant recordedAt,
        Integer systolicBp,
        Integer diastolicBp,
        Integer heartRate,
        Double weightKg,
        Double temperatureC,
        Integer oxygenSaturation,
        Integer glucoseMgDl
) {}
