package lk.kavindu.clinic.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record RegisterRequest(

        @Schema(example = "kavindu@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 255)
        String email,

        @Schema(example = "Str0ng@Pass")
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "Password must contain a letter")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain a digit")
        String password,

        @Schema(example = "Kavindu Perera")
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 150)
        String fullName,

        @Schema(example = "+94771234567")
        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Must be a valid phone number")
        String phone
) {}
