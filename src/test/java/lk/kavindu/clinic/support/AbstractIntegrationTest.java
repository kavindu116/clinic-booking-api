package lk.kavindu.clinic.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.kavindu.clinic.booking.BookingRepository;
import lk.kavindu.clinic.doctor.AvailabilityRepository;
import lk.kavindu.clinic.doctor.DoctorRepository;
import lk.kavindu.clinic.user.RefreshTokenRepository;
import lk.kavindu.clinic.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("clinicdb_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RefreshTokenRepository refreshTokenRepository;
    @Autowired protected DoctorRepository doctorRepository;
    @Autowired protected AvailabilityRepository availabilityRepository;
    @Autowired protected BookingRepository bookingRepository;

    /** FK order eka wædagath — children palamuwen. */
    @BeforeEach
    void cleanDatabase() {
        bookingRepository.deleteAll();
        availabilityRepository.deleteAll();
        doctorRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
