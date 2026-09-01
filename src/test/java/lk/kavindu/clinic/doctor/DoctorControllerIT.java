package lk.kavindu.clinic.doctor;

import lk.kavindu.clinic.support.AbstractIntegrationTest;
import lk.kavindu.clinic.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DoctorControllerIT extends AbstractIntegrationTest {

    @Autowired private TestDataFactory data;

    @Test
    @DisplayName("an unknown sort field returns 400, not 500")
    void rejectsUnknownSortField() throws Exception {
        data.doctorWithWeekdayHours("sort@clinic.lk");

        mockMvc.perform(get("/api/v1/doctors").param("sort", "string"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Cannot sort by")));
    }

    @Test
    @DisplayName("an allowed sort field works")
    void acceptsAllowedSortField() throws Exception {
        data.doctorWithWeekdayHours("sortok@clinic.lk");

        mockMvc.perform(get("/api/v1/doctors").param("sort", "consultationFee,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("doctor listing is public — no token needed")
    void listingIsPublic() throws Exception {
        data.doctorWithWeekdayHours("public@clinic.lk");

        mockMvc.perform(get("/api/v1/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("slots endpoint returns the derived grid for a working day")
    void returnsDerivedSlots() throws Exception {
        var doctor = data.doctorWithWeekdayHours("slots@clinic.lk");

        // 2026-09-07 is a Monday; the factory sets Mon-Fri 09:00-13:00, 30 min
        mockMvc.perform(get("/api/v1/doctors/" + doctor.getId() + "/slots")
                        .param("date", "2026-09-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.totalSlots").value(8))
                .andExpect(jsonPath("$.timezone").value("Asia/Colombo"))
                .andExpect(jsonPath("$.slots[0].localStart").value("09:00"))
                .andExpect(jsonPath("$.slots[0].start").value("2026-09-07T03:30:00Z"));
    }

    @Test
    @DisplayName("a day the doctor does not work returns an empty grid, not an error")
    void returnsEmptyGridOnNonWorkingDay() throws Exception {
        var doctor = data.doctorWithWeekdayHours("sunday@clinic.lk");

        mockMvc.perform(get("/api/v1/doctors/" + doctor.getId() + "/slots")
                        .param("date", "2026-09-06"))   // Sunday
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSlots").value(0));
    }

    @Test
    @DisplayName("an unknown doctor returns 404")
    void unknownDoctorReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/doctors/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCTOR_NOT_FOUND"));
    }
}