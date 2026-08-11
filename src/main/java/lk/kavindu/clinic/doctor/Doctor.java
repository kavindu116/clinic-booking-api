package lk.kavindu.clinic.doctor;

import jakarta.persistence.*;
import lk.kavindu.clinic.common.BaseEntity;
import lk.kavindu.clinic.user.User;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Doctor extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 100)
    private String specialization;

    @Column(length = 255)
    private String qualifications;

    @Column(name = "consultation_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Availability> availabilities = new ArrayList<>();
}
