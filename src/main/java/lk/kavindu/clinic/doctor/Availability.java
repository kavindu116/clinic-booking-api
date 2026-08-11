package lk.kavindu.clinic.doctor;

import jakarta.persistence.*;
import lk.kavindu.clinic.common.BaseEntity;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Doctor kenekge weekly recurring schedule eka.
 * Udaharanak: Monday 09:00-12:00, 30 min slots => slots 6ak.
 *
 * Slots pre-generate karanne naa — bookings table eka ekka compare karala
 * runtime ekedi calculate karanawa (Week 2).
 */
@Entity
@Table(name = "availability")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Availability extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    /** ISO-8601: 1 = Monday .. 7 = Sunday */
    @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Builder.Default
    @Column(name = "slot_duration_minutes", nullable = false)
    private Short slotDurationMinutes = 30;

    public DayOfWeek day() {
        return DayOfWeek.of(dayOfWeek);
    }

    public void setDay(DayOfWeek day) {
        this.dayOfWeek = (short) day.getValue();
    }
}
