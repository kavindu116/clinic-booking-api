package lk.kavindu.clinic.booking;

import lk.kavindu.clinic.booking.dto.BookingResponse;
import lk.kavindu.clinic.booking.dto.CreateBookingRequest;
import lk.kavindu.clinic.booking.dto.RescheduleRequest;
import lk.kavindu.clinic.common.ClinicProperties;
import lk.kavindu.clinic.common.dto.PageResponse;
import lk.kavindu.clinic.common.exception.ApiException;
import lk.kavindu.clinic.common.exception.ErrorCode;
import lk.kavindu.clinic.doctor.*;
import lk.kavindu.clinic.security.AppUserPrincipal;
import lk.kavindu.clinic.user.Role;
import lk.kavindu.clinic.user.User;
import lk.kavindu.clinic.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository;
    private final DoctorRepository doctorRepository;
    private final AvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;
    private final SlotLock slotLock;
    private final ClinicProperties clinic;

    @Transactional
    public BookingResponse create(AppUserPrincipal principal, CreateBookingRequest request) {
        User patient = loadUser(principal.getId());
        Doctor doctor = loadDoctor(request.doctorId());
        Instant slotStart = request.slotStrat();

        if (!doctor.isActive()) {
            throw ApiException.of(ErrorCode.SLOT_NOT_AVAILABLE,
                    "This doctor is not currently accepting appointments");
        }

        SlotCalculator.Slot slot = resolveSlot(doctor, slotStart);
        validateTiming(slotStart);
        validatePatientLimits(patient.getId(), slotStart);

        slotLock.acquire(doctor.getId(), slotStart);

        if (bookingRepository.countActiveAtSlot(doctor.getId(), slotStart) > 0) {
            throw ApiException.of(ErrorCode.SLOT_ALREADY_BOOKED,
                    "This time slot has just been taken.Please choose another slot");
        }

        Booking booking = bookingRepository.save(Booking.builder()
                .patient(patient)
                .doctor(doctor)
                .slotStart(slotStart)
                .slotEnd(slot.end())
                .status(BookingStatus.CONFIRMED)
                .notes(request.notes())
                .build());

        log.info("Booking crated: id={} patientId={} doctorId={} slot={}",
                booking.getId(), patient.getId(), doctor.getId(), slotStart);

        return toResponse(booking, patient, doctor);


    }

    @Transactional
    public BookingResponse cancel(AppUserPrincipal principal, Long bookingId) {
        Booking booking = loadBooking(bookingId);
        requireAccess(principal, booking);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.of(ErrorCode.BOOKING_RULE_VIOLATION,
                    "This booking is already cancelled");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw ApiException.of(ErrorCode.BOOKING_RULE_VIOLATION,
                    "A completed appointment cannot be cancelled");
        }

        if (principal.getRole() == Role.PATIENT) {
            Duration unitSlot = Duration.between(Instant.now(), booking.getSlotStart());
            if (unitSlot.toHours() < clinic.cancellationWindowHours()) {
                throw ApiException.of(ErrorCode.BOOKING_RULE_VIOLATION,
                        "Appointments must br cancelled at least "
                                + clinic.cancellationWindowHours() + " hours in advance. Please call the clinic.");
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        log.info("Booking canceled : id={} by userid={}", bookingId, principal.getId());

        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse reschedule(AppUserPrincipal principal, Long bookingId, RescheduleRequest request) {
        Booking existing = loadBooking(bookingId);
        requireAccess(principal, existing);

        if (existing.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.of(ErrorCode.BOOKING_RULE_VIOLATION,
                    "Only a confirmed appointment can be rescheduled");
        }

        if (existing.getSlotStart().equals(request.slotStart())) {
            throw ApiException.of(ErrorCode.BOOKING_RULE_VIOLATION,
                    "The booking is already at that time.");
        }

        Doctor doctor = existing.getDoctor();
        User patient = existing.getPatient();
        Instant newStart = request.slotStart();

        SlotCalculator.Slot slot = resolveSlot(doctor, newStart);
        validateTiming(newStart);

        existing.setStatus(BookingStatus.CANCELLED);
        bookingRepository.flush();

        slotLock.acquire(doctor.getId(), newStart);

        if (bookingRepository.countActiveAtSlot(doctor.getId(), newStart) > 0) {
            throw ApiException.of(ErrorCode.SLOT_ALREADY_BOOKED,
                    "This time slot has been taken.Please choose another slot");
        }
        if (bookingRepository.countPatientBookingsAtSlot(patient.getId(), newStart) > 0) {
            throw ApiException.of(ErrorCode.BOOKING_RULE_VIOLATION,
                    "You already have another appointment at that time.");
        }

        Booking moved = bookingRepository.save(Booking.builder()
                .patient(patient)
                .doctor(doctor)
                .slotStart(slot.start())
                .slotEnd(slot.end())
                .status(BookingStatus.CONFIRMED)
                .notes(existing.getNotes())
                .build());

        log.info("Booking Rescheduled: oldId={} newId={} slot={}",bookingId, moved.getId(), newStart);

        return toResponse(moved, patient, doctor);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> myBookings(AppUserPrincipal principal,
                                                    Pageable pageable) {
        return PageResponse.from(bookingRepository
                .findByPatientIdOrderBySlotStartDesc(principal.getId(), pageable)
                .map(BookingResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> doctorSchedule(Long doctorId,Pageable pageable) {
        return PageResponse.from(bookingRepository.findByDoctorIdOrderBySlotStartAsc(doctorId,pageable)
                .map(BookingResponse::from));
    }

    @Transactional(readOnly = true)
    public BookingResponse get(AppUserPrincipal principal, Long bookingId) {
        Booking booking = loadBooking(bookingId);
        requireAccess(principal, booking);
        return BookingResponse.from(booking);
    }


    private void requireAccess(AppUserPrincipal principal, Booking booking) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (principal.getRole() == Role.PATIENT && booking.getPatient().getId().equals(principal.getId())) {
            return;
        }
        if (principal.getRole() == Role.DOCTOR && booking.getDoctor().getId().equals(principal.getId())) {
            return;
        }
        throw ApiException.of(ErrorCode.BOOKING_NOT_FOUND, "Booking not found");
    }

    private Booking loadBooking(Long id) {
        return bookingRepository.findWithDetailsById(id)
                .orElseThrow(() -> ApiException.of(ErrorCode.BOOKING_NOT_FOUND,
                        "Booking not found with id " + id));
    }

    private BookingResponse toResponse(Booking booking, User patient, Doctor doctor) {
        return new BookingResponse(
                booking.getId(),
                doctor.getId(),
                doctor.getUser().getFullName(),
                doctor.getSpecialization(),
                doctor.getConsultationFee(),
                patient.getId(),
                patient.getFullName(),
                booking.getSlotStart(),
                booking.getSlotEnd(),
                booking.getStatus(),
                booking.getNotes(),
                booking.getCreatedAt()
        );
    }

    private void validatePatientLimits(Long patientId, Instant slotStart) {
        long upcoming = bookingRepository.countUpcomingForPatient(patientId, Instant.now());
        if (upcoming >= clinic.maxUpcomingBookingsPerPatient()) {
            throw ApiException.of(ErrorCode.BOOKING_RULE_VIOLATION,
                    "You already have " + upcoming + "upcoming appointments " +
                            "(limit is " + clinic.maxUpcomingBookingsPerPatient() + ")");
        }

        if (bookingRepository.countPatientBookingsAtSlot(patientId, slotStart) > 0) {
            throw ApiException.of(ErrorCode.BOOKING_RULE_VIOLATION, "" +
                    "You already have another appointment at that time");
        }
    }

    private void validateTiming(Instant slotStart) {
        Instant now = Instant.now();

        if (slotStart.isBefore(now)) {
            throw ApiException.of(ErrorCode.BOOKING_RULE_VIOLATION,
                    "Cannot book an appointment in the past");
        }

        Instant earliest = now.plus(Duration.ofMinutes(clinic.minAdvanceBookingMinutes()));
        if (slotStart.isBefore(earliest)) {
            throw ApiException.of(ErrorCode.BOOKING_RULE_VIOLATION,
                    "Appointments must be booked at least "
                            + clinic.minAdvanceBookingMinutes() + " minutes in advance");
        }

        Instant latest = now.plus(Duration.ofDays(clinic.maxAdvanceBookingDays()));
        if (slotStart.isAfter(latest)) {
            throw ApiException.of(ErrorCode.BOOKING_RULE_VIOLATION,
                    "Appointments can only be booked up to "
                            + clinic.maxAdvanceBookingDays() + " days in advance");
        }
    }

    private SlotCalculator.Slot resolveSlot(Doctor doctor, Instant slotStart) {
        ZoneId zone = clinic.zone();
        LocalDate date = slotStart.atZone(zone).toLocalDate();

        List<Availability> rules = availabilityRepository.findByDoctorIdAndDayOfWeek(
                doctor.getId(), (short) date.getDayOfWeek().getValue());

        if (rules.isEmpty()) {
            throw ApiException.of(ErrorCode.SLOT_NOT_AVAILABLE,
                    "This doctor does not consult on " + date.getDayOfWeek().name());
        }

        return SlotCalculator.candidateSlots(rules, date, zone).stream()
                .filter(slot -> slot.start().equals(slotStart))
                .findFirst()
                .orElseThrow(() -> ApiException.of(ErrorCode.SLOT_NOT_AVAILABLE,
                        "That time is not valid slot for this doctor. "
                                + "Use GET /api/v1/doctors" + doctor.getId()
                                + "/slots to see available times."));
    }

    private Doctor loadDoctor(Long id) {
        return doctorRepository.findWithUserById(id).orElseThrow(
                () -> ApiException.of(ErrorCode.DOCTOR_NOT_FOUND, "Doctor not found")
        );
    }

    private User loadUser(Long id) {
        return userRepository.findById(id).orElseThrow(
                () -> ApiException.of(ErrorCode.USER_NOT_FOUND, "User not found")
        );
    }
}
