package lk.kavindu.clinic.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.kavindu.clinic.auth.dto.*;
import lk.kavindu.clinic.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, and token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new patient account",
               security = {})   // public endpoint — Swagger eke lock icon eka ain karanawa
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @io.swagger.v3.oas.annotations.media.Content()),
            @ApiResponse(responseCode = "409", description = "Email already registered", content = @io.swagger.v3.oas.annotations.media.Content())
    })
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive access + refresh tokens", security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @io.swagger.v3.oas.annotations.media.Content())
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token", security = {})
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke all refresh tokens for the current user",
               security = @SecurityRequirement(name = "bearerAuth"))
    public void logout(@AuthenticationPrincipal AppUserPrincipal principal) {
        authService.logout(principal.getId());
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<UserSummary> me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(new UserSummary(
                principal.getId(),
                principal.getEmail(),
                principal.getFullName(),
                principal.getRole().name()));
    }
}
