package lk.kavindu.clinic.config;

import lk.kavindu.clinic.doctor.Availability;
import lk.kavindu.clinic.doctor.Doctor;
import lk.kavindu.clinic.doctor.DoctorRepository;
import lk.kavindu.clinic.user.Role;
import lk.kavindu.clinic.user.User;
import lk.kavindu.clinic.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmailIgnoreCase("admin@clinic.lk")) {
            log.info("Seed data already present, skipping");
            return;
        }

        userRepository.save(User.builder()
                .email("admin@clinic.lk")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .fullName("Clinic Administrator")
                .role(Role.ADMIN)
                .enabled(true)
                .build());

        userRepository.save(User.builder()
                .email("patient@clinic.lk")
                .passwordHash(passwordEncoder.encode("Patient@123"))
                .fullName("Nimal Perera")
                .phone("+94771234567")
                .role(Role.PATIENT)
                .enabled(true)
                .build());

        seedDoctor("dr.silva@clinic.lk", "Dr. Anusha Silva", "Cardiology",
                "MBBS, MD (Cardiology)", new BigDecimal("3500.00"));
        seedDoctor("dr.fernando@clinic.lk", "Dr. Ruwan Fernando", "Dermatology",
                "MBBS, Dip. Dermatology", new BigDecimal("2500.00"));

        log.info("""

                ==========================================================
                  Seed data loaded. Dev credentials:
                    admin@clinic.lk    / Admin@123
                    patient@clinic.lk  / Patient@123
                    dr.silva@clinic.lk / Doctor@123
                  Swagger UI: http://localhost:8080/swagger-ui.html
                ==========================================================
                """);
    }

    private void seedDoctor(String email, String name, String specialization,
                            String qualifications, BigDecimal fee) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Doctor@123"))
                .fullName(name)
                .role(Role.DOCTOR)
                .enabled(true)
                .build());

        Doctor doctor = Doctor.builder()
                .user(user)
                .specialization(specialization)
                .qualifications(qualifications)
                .consultationFee(fee)
                .active(true)
                .build();

        // Mon-Fri, 09:00-13:00, 30 min slots
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

        doctorRepository.save(doctor);
    }
}
