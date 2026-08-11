package lk.kavindu.clinic.doctor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {


    @EntityGraph(attributePaths = "user")
    Optional<Doctor> findWithUserById(Long id);

    @EntityGraph(attributePaths = "user")
    @Query("""
           SELECT d FROM Doctor d
           WHERE d.active = true
             AND (:specialization IS NULL OR LOWER(d.specialization) = LOWER(:specialization))
           """)
    Page<Doctor> findActive(@Param("specialization") String specialization, Pageable pageable);

    @Query("SELECT DISTINCT d.specialization FROM Doctor d WHERE d.active = true ORDER BY d.specialization")
    List<String> findAllSpecializations();

    Optional<Doctor> findByUserId(Long userId);
}
