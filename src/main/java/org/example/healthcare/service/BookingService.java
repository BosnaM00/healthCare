package org.example.healthcare.service;

import org.example.healthcare.dto.booking.BookingRequest;
import org.example.healthcare.dto.booking.BookingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BookingService {

    BookingResponse create(UUID patientId, BookingRequest request);

    BookingResponse getById(UUID id, UUID principalId);

    Page<BookingResponse> getPatientBookings(UUID patientId, Pageable pageable);

    Page<BookingResponse> getMedicBookings(UUID medicId, Pageable pageable);

    void cancel(UUID id, UUID patientId);
}
