package lk.kavindu.clinic.notification.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingEventPayload (
        Long eventVersion,
        Long bookingId,
        String patientName,
        String patientEmail,
        String doctorName,
        String specialization,
        BigDecimal consultationFee,
        Instant slotStart,
        Instant slotEnd,
        String clinicTimeZone,
        String notes
){
    public static final long CURRENT_VERSION = 1L;
}
