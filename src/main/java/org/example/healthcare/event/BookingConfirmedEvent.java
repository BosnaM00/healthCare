package org.example.healthcare.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published by {@code BookingServiceImpl} immediately after a booking is persisted
 * and its Stripe PaymentIntent is created in RESERVED state.
 *
 * <p>Listened to by {@code ConsultationServiceImpl}, which creates the
 * {@code Consultation} entity in {@code SCHEDULED} status and calls
 * {@code VideoProvider.createRoom} so the room URL is ready before the medic
 * clicks Start.
 *
 * <p>Using Spring ApplicationEvents decouples the booking module from the video
 * module and makes the room-creation step safely re-runnable on event replay.
 */
public class BookingConfirmedEvent extends ApplicationEvent {

    private final UUID bookingId;
    private final UUID patientUserId;
    private final UUID medicUserId;

    public BookingConfirmedEvent(Object source, UUID bookingId, UUID patientUserId, UUID medicUserId) {
        super(source);
        this.bookingId     = bookingId;
        this.patientUserId = patientUserId;
        this.medicUserId   = medicUserId;
    }

    public UUID getBookingId()     { return bookingId; }
    public UUID getPatientUserId() { return patientUserId; }
    public UUID getMedicUserId()   { return medicUserId; }
}
