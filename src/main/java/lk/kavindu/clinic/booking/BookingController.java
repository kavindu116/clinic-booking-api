package lk.kavindu.clinic.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.kavindu.clinic.booking.dto.BookingResponse;
import lk.kavindu.clinic.booking.dto.CreateBookingRequest;
import lk.kavindu.clinic.booking.dto.RescheduleRequest;
import lk.kavindu.clinic.common.dto.PageResponse;
import lk.kavindu.clinic.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Bookings", description = "Create, view, reschedule, and cancel appointments")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Book an appoiment slot",
            description = """
                    The slot must be one returned by `GET /doctors/{id}/slots`.
                    
                    Concurrency is handled with a PostgreSQL transaction-scoped advisory
                    lock keyed on (doctorId, slotStart), backed by a partial unique index
                    on the bookings table. If two requests race for the same slot, exactly
                    one succeeds and the other receives `409 SLOT_ALREADY_BOOKED`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Booking confirmed"),
            @ApiResponse(responseCode = "409", description = "Slot already booked",
                    content = @io.swagger.v3.oas.annotations.media.Content()),
            @ApiResponse(responseCode = "422", description = "Not a valid slot, or a booking rule was violated",
                    content = @io.swagger.v3.oas.annotations.media.Content())

    })
    public BookingResponse create(@AuthenticationPrincipal AppUserPrincipal principal,
                                  @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.create(principal, request);
    }

    @PatchMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel Booking",
    description = "Patients are subject to the clinic's cancellation window; staff are not.")
    public BookingResponse cancel(@AuthenticationPrincipal AppUserPrincipal principal,
                                  @PathVariable long bookingId) {
        return bookingService.cancel(principal, bookingId);
    }

    @PatchMapping("/{bookingId}/reschedule")
    @Operation(summary = "Move a bookig to a different slot.",
    description = "Cancels the original and creates a replacement in one trtasaction.")
    public BookingResponse reschedule(@AuthenticationPrincipal AppUserPrincipal principal,
                                      @PathVariable Long bookingId,
                                      @Valid @RequestBody RescheduleRequest request) {
        return bookingService.reschedule(principal, bookingId, request);
    }

    @GetMapping("/me")
    @Operation(summary = "List the current patient's bookings")
    public PageResponse<BookingResponse> myBookings(@AuthenticationPrincipal AppUserPrincipal principal,
                                                    @PageableDefault(size =  20) Pageable pageable) {
        return bookingService.myBookings(principal, pageable);
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get one booking",
    description = "Patients see their own;doctors see theirs;admin see all.")
    public BookingResponse get(@AuthenticationPrincipal AppUserPrincipal principal,
                               @PathVariable long bookingId) {
        return bookingService.get(principal, bookingId);
    }

    @GetMapping("/doctors/{doctorId}")
    @PreAuthorize("hasRole('ADMIN') or @doctorSecurity.isSelf(#doctorId, authentication)")
    @Operation(summary = "List a doctor's appoinments (doctor or admin)")
    public PageResponse<BookingResponse> doctorSchedule(
            @PathVariable Long doctorId,
            @PageableDefault(size =  20) Pageable pageable
    ){
        return bookingService.doctorSchedule(doctorId, pageable);
    }
}
