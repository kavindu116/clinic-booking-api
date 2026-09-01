package lk.kavindu.clinic.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
           SELECT COUNT(b) FROM Booking b
           WHERE b.doctor.id = :doctorId
             AND b.slotStart = :slotStart
             AND b.status <> lk.kavindu.clinic.booking.BookingStatus.CANCELLED
           """)
    long countActiveAtSlot(@Param("doctorId") Long doctorId,
                           @Param("slotStart") Instant slotStart);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT b FROM Booking b
           WHERE b.doctor.id = :doctorId
             AND b.slotStart = :slotStart
             AND b.status <> lk.kavindu.clinic.booking.BookingStatus.CANCELLED
           """)
    List<Booking> lockActiveBySlot(@Param("doctorId") Long doctorId,
                                   @Param("slotStart") Instant slotStart);

    @Query("""
           SELECT COUNT(b) FROM Booking b
           WHERE b.patient.id = :patientId
             AND b.slotStart = :slotStart
             AND b.status <> lk.kavindu.clinic.booking.BookingStatus.CANCELLED
           """)
    long countPatientBookingsAtSlot(@Param("patientId") Long patientId,
                                    @Param("slotStart") Instant slotStart);


    @Query("""
           SELECT b FROM Booking b
           WHERE b.doctor.id = :doctorId
             AND b.slotStart >= :from AND b.slotStart < :to
             AND b.status <> lk.kavindu.clinic.booking.BookingStatus.CANCELLED
           """)
    List<Booking> findActiveInRange(@Param("doctorId") Long doctorId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    @EntityGraph(attributePaths = {"doctor", "doctor.user"})
    Page<Booking> findByPatientIdOrderBySlotStartDesc(Long patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient"})
    Page<Booking> findByDoctorIdOrderBySlotStartAsc(Long doctorId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user"})
    Optional<Booking> findWithDetailsById(Long id);


    @Query("""
           SELECT COUNT(b) FROM Booking b
           WHERE b.patient.id = :patientId
             AND b.slotStart > :now
             AND b.status = lk.kavindu.clinic.booking.BookingStatus.CONFIRMED
           """)
    long countUpcomingForPatient(@Param("patientId") Long patientId, @Param("now") Instant now);
}
