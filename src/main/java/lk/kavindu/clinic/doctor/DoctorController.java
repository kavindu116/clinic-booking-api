package lk.kavindu.clinic.doctor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lk.kavindu.clinic.common.dto.PageResponse;
import lk.kavindu.clinic.doctor.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
@Validated
@Tag(name = "Doctors", description = "Doctors profiles,weekly availability,and open slots")
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping
    @Operation(summary = "List active doctors, optionally filtered by specialization",
            description = """
                       Sortable fields: `specialization`, `consultationFee`, `id`.
                       Leave `sort` empty for the default ordering.
                       """,
            security = {})
    public PageResponse<DoctorResponse> list(
            @RequestParam(required = false) String specialization,
            @PageableDefault(size = 20, sort = "specialization") Pageable pageable) {
        return doctorService.list(specialization, pageable);
    }

    @GetMapping("/specializations")
    @Operation(summary = "List every specialization currently offered", security = {})
    public List<String> specializations() {
        return doctorService.specialization();
    }

    @GetMapping("/{doctorId}")
    @Operation(summary = "Get Single doctor profile", security = {})
    public DoctorResponse get(@PathVariable Long doctorId) {
        return doctorService.get(doctorId);
    }

    @GetMapping("/{doctorId}/availability")
    @Operation(summary = "Get a doctor's Weekly availability rules", security = {})
    public List<AvailabilityResponse> availability(@PathVariable Long doctorId) {
        return doctorService.availability(doctorId);
    }

    @GetMapping("/{doctorId}/slots")
    @Operation(summary = "Get bookable slots for a doctor on a given date",
            description = """
                    Slots are derived from the doctor's weekly availability rule and
                    diffed against existing bookings — they are not stored in the database.
                    A slot with `available: false` is either already booked or too close
                    to the current time.
                    """,
            security = {})
    public DaySlotsResponse slots(
            @PathVariable Long doctorId,
            @Parameter(description = "Date in the clinic's timezone", example = "2026-08-28")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return doctorService.slotsForDate(doctorId, date);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a doctor account (admin only)",
    security = @SecurityRequirement(name = "bearerAuth"))
    public DoctorResponse create(@Valid @RequestBody CreateDoctorRequest request){
        return doctorService.create(request);
    }

    @DeleteMapping("/{doctorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a doctor (admin only)",
            description = "Soft delete — existing bookings are preserved.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public void deactivate(@PathVariable Long doctorId) {
        doctorService.deactivate(doctorId);
    }

    @PutMapping("/{doctorId}/availability")
    @PreAuthorize("hasRole('ADMIN') or @doctorSecurity.isSelf(#doctorId, authentication)")
    @Operation(summary = "Replace a doctor's weekly availability",
            description = "Sends the complete schedule — any blocks not included are removed.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public List<AvailabilityResponse> replaceAvailability(
            @PathVariable Long doctorId,
            @Valid @RequestBody @NotEmpty(message = "At least one availability block is required")
            List<@Valid AvailabilityRequest> requests) {
        return doctorService.replaceAvailability(doctorId, requests);
    }




}
