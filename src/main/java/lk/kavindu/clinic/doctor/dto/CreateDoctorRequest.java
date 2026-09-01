package lk.kavindu.clinic.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;


public record CreateDoctorRequest (
        @Schema(example = "dr.nimal@clinic.lk")
        @NotBlank @Email @Size(max = 255)
        String email,

        @Schema(example = "Doctor@123")
        @NotBlank @Size(min = 8, max = 72)
        String password,

        @Schema(example = "Dr. Nimal Jayasuriya")
        @NotBlank @Size(min = 2, max = 150)
        String fullName,

        @Schema(example = "+94112345678")
        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Must be a valid phone number")
        String phone,

        @Schema(example = "Pediatrics")
        @NotBlank @Size(max = 100)
        String specialization,

        @Schema(example = "MBBS, DCH")
        @Size(max = 255)
        String qualifications,

        @Schema(example = "3000.00")
        @NotNull @DecimalMin(value = "0.0") @Digits(integer = 8, fraction = 2)
        BigDecimal consultationFee,

        @Size(max = 2000)
        String bio
) {
}
