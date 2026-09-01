package lk.kavindu.clinic.support;

import lk.kavindu.clinic.doctor.Availability;
import lk.kavindu.clinic.doctor.Doctor;
import lk.kavindu.clinic.doctor.DoctorRepository;
import lk.kavindu.clinic.user.Role;
import lk.kavindu.clinic.user.User;
import lk.kavindu.clinic.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;


@Component
@RequiredArgsConstructor
public class TestDataFactory {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User patient(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Str0ngPass1"))
                .fullName("Patient " + email.split("@")[0])
                .role(Role.PATIENT)
                .enabled(true)
                .build());
    }


    @Transactional
    public Doctor doctorWithWeekdayHours(String email) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Doctor@123"))
                .fullName("Dr. " + email.split("@")[0])
                .role(Role.DOCTOR)
                .enabled(true)
                .build());

        Doctor doctor = Doctor.builder()
                .user(user)
                .specialization("General Medicine")
                .qualifications("MBBS")
                .consultationFee(new BigDecimal("2500.00"))
                .active(true)
                .build();

        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            doctor.getAvailabilities().add(Availability.builder()
                    .doctor(doctor)
                    .dayOfWeek((short) day.getValue())
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(13, 0))
                    .slotDurationMinutes((short) 30)
                    .build());
        }

        return doctorRepository.save(doctor);
    }
}
