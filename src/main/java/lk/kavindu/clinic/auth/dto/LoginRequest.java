package lk.kavindu.clinic.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @Schema(example = "admin@clinic.lk")
        @NotBlank @Email
        String email,

        @Schema(example = "Admin@123")
        @NotBlank
        String password
) {}
