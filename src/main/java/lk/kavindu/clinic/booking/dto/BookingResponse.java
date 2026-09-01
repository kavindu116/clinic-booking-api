package lk.kavindu.clinic.booking.dto;

import lk.kavindu.clinic.booking.Booking;
import lk.kavindu.clinic.booking.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse (
 Long id,
 Long doctorId,
 String doctorName,
 String specialization,
 BigDecimal consultationFee,
 Long patientId,
 String patientName,
 Instant slotStart,
 Instant slotEnd,
 BookingStatus status,
 String notes,
 Instant createdAt
){
    public static BookingResponse from(Booking b){
        return new BookingResponse(
                b.getId(),
                b.getDoctor().getId(),
                b.getDoctor().getUser().getFullName(),
                b.getDoctor().getSpecialization(),
                b.getDoctor().getConsultationFee(),
                b.getPatient().getId(),
                b.getPatient().getFullName(),
                b.getSlotStart(),
                b.getSlotEnd(),
                b.getStatus(),
                b.getNotes(),
                b.getCreatedAt()
        );
    }

}

