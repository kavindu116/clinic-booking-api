package lk.kavindu.clinic.doctor.dto;

import lk.kavindu.clinic.doctor.Doctor;

import java.math.BigDecimal;

public record DoctorResponse(
        Long id,
        String fullName,
        String specialization,
        String qualifications,
        BigDecimal consultationFee,
        String bio,
        boolean active
) {
    public static DoctorResponse from(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getUser().getFullName(),
                doctor.getSpecialization(),
                doctor.getQualifications(),
                doctor.getConsultationFee(),
                doctor.getBio(),
                doctor.isActive()
        );
    }
}
