package lk.kavindu.clinic.doctor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

    List<Availability> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(Long doctorId);

    List<Availability> findByDoctorIdAndDayOfWeek(Long doctorId, Short dayOfWeek);

    void deleteByDoctorId(Long doctorId);
}
