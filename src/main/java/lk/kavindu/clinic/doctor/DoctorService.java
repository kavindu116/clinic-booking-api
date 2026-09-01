package lk.kavindu.clinic.doctor;

import lk.kavindu.clinic.booking.Booking;
import lk.kavindu.clinic.booking.BookingRepository;
import lk.kavindu.clinic.common.ClinicProperties;
import lk.kavindu.clinic.common.dto.PageResponse;
import lk.kavindu.clinic.common.exception.ApiException;
import lk.kavindu.clinic.common.exception.ErrorCode;
import lk.kavindu.clinic.doctor.dto.*;
import lk.kavindu.clinic.user.Role;
import lk.kavindu.clinic.user.User;
import lk.kavindu.clinic.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorService {
    private static final Set<Short> ALLOWED_SLOT_DURATION = Set.of(
            (short) 10, (short) 15, (short) 20, (short) 30, (short) 45, (short) 60
    );

    private static final Set<String> SORTABLE = Set.of("id", "specialization", "consultationFee");

    private final DoctorRepository doctorRepository;
    private final AvailabilityRepository availabilityRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClinicProperties clinicProperties;


    @Transactional(readOnly = true)
    public PageResponse<DoctorResponse> list(String specialization, Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                        "Cannot sort by '" + order.getProperty()
                                + "'. Allowed: " + String.join(", ", SORTABLE));
            }
        });

        String filter = (specialization == null || specialization.isBlank())
                ? null : specialization.trim();

        Page<Doctor> page = (filter == null)
                ? doctorRepository.findAllActive(pageable)
                : doctorRepository.findActiveBySpecialization(filter, pageable);

        return PageResponse.from(page.map(DoctorResponse::from));
    }

    @Transactional(readOnly = true)
    public DoctorResponse get(Long doctorId) {
        return DoctorResponse.from(loadDoctor(doctorId));
    }

    @Transactional(readOnly = true)
    public List<String> specialization() {
        return doctorRepository.findAllSpecializations();
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> availability(Long doctorId) {
        loadDoctor(doctorId);
        return availabilityRepository.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId)
                .stream().map(AvailabilityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DaySlotsResponse slotsForDate(Long doctorId, LocalDate date) {
        Doctor doctor = loadDoctor(doctorId);
        ZoneId zone = clinicProperties.zone();

        List<Availability> rules = availabilityRepository.findByDoctorIdAndDayOfWeek(
                doctorId, (short) date.getDayOfWeek().getValue()
        );

        List<SlotCalculator.Slot> candidates = SlotCalculator.candidateSlots(rules, date, zone);

        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

        Set<Instant> booked = bookingRepository
                .findActiveInRange(doctorId, dayStart, dayEnd)
                .stream()
                .map(Booking::getSlotStart)
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        Instant earliest = now.plusSeconds(clinicProperties.minAdvanceBookingMinutes() * 60L);

        List<SlotResponse> slots = candidates.stream()
                .map(slot -> new SlotResponse(
                        slot.start(),
                        slot.end(),
                        slot.localStart(),
                        slot.localEnd(),
                        !booked.contains(slot.start()) && slot.start().isAfter(earliest)
                )).toList();

        return new DaySlotsResponse(
                doctor.getId(),
                doctor.getUser().getFullName(),
                date,
                date.getDayOfWeek().name(),
                zone.getId(),
                slots.size(),
                (int) slots.stream().filter(SlotResponse::available).count(),
                slots
        );
    }


    @Transactional(readOnly = true)
    public DoctorResponse create(CreateDoctorRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "An Account with this email address already exists!");
        }

        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .phone(request.phone())
                .role(Role.DOCTOR)
                .enabled(true)
                .build());

        Doctor doctor = doctorRepository.save(Doctor.builder()
                .user(user)
                .specialization(request.specialization().trim())
                .qualifications(request.qualifications())
                .consultationFee(request.consultationFee())
                .bio(request.bio())
                .active(true)
                .build());

        log.info("Created doctor id={} for userId={}", doctor.getId(), user.getId());
        return DoctorResponse.from(doctor);

    }

    @Transactional
    public void deactivate(Long doctorId) {
       Doctor doctor = loadDoctor(doctorId);
       doctor.setActive(false);
       log.info("Deactivated doctor id={}", doctor.getId());
    }

    @Transactional
    public List<AvailabilityResponse> replaceAvailability(Long doctorId, List<AvailabilityRequest> requests ) {
        Doctor doctor = loadDoctor(doctorId);
        validateAvailability(requests);

        availabilityRepository.deleteByDoctorId(doctorId);
        availabilityRepository.flush();

        List<Availability> saved = requests.stream()
                .map(r -> Availability.builder()
                        .doctor(doctor)
                        .dayOfWeek(r.dayOfWeek())
                        .startTime(r.startTime())
                        .endTime(r.endTime())
                        .slotDurationMinutes(r.slotDurationMinutes())
                        .build())
                .map(availabilityRepository::save)
                .toList();

        log.info("Replaced availability of doctor id={} : {} block(s)", doctor.getId(), saved.size());
        return  saved.stream().map(AvailabilityResponse::from).toList();
    }


    private void validateAvailability(List<AvailabilityRequest> requests) {
        for (AvailabilityRequest request : requests) {
            if (!request.endTime().isAfter(request.startTime())) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                        "End time must be after start time for Day " + request.dayOfWeek());
            }
            if (!ALLOWED_SLOT_DURATION.contains(request.slotDurationMinutes())) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                        "Slot duration minutes must be one of 10,20,30,45,60");
            }
            long blockMinutes = Duration.between(request.startTime(), request.endTime()).toMinutes();
            if (blockMinutes < request.slotDurationMinutes()) {
                throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                        "Block on day " + request.dayOfWeek() + " is shorter than one slot");
            }
        }

        for (int i = 0; i < requests.size(); i++) {
            for (int j = i + 1; j < requests.size(); j++) {
                AvailabilityRequest request = requests.get(i);
                AvailabilityRequest request2 = requests.get(j);

                if (request.dayOfWeek().equals(request2.dayOfWeek()) && overlaps(request2, request)) {
                    throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                            "Overlapping availability blocks on day " + request.dayOfWeek());
                }
            }
        }

    }

    private boolean overlaps(AvailabilityRequest request1, AvailabilityRequest request2) {
        LocalTime req1Start = request1.startTime(), req1End = request1.endTime();
        LocalTime req2Start = request2.startTime(), req2End = request2.endTime();
        return req1Start.isBefore(req2End) && req2Start.isBefore(req1End);
    }

    Doctor loadDoctor(Long doctorId) {
        return doctorRepository.findWithUserById(doctorId)
                .orElseThrow(() -> ApiException.of(ErrorCode.DOCTOR_NOT_FOUND,
                        "Doctor Not Found"));
    }
}
