package lk.kavindu.clinic.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lk.kavindu.clinic.common.dto.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Har exception ekakma me thanata enawa. Controllers wala try/catch naa.
 *
 * Wadagath: internal exception messages client ekata leak wenne naa.
 * Stack traces log ekata witharai — attacker kenekta internal structure
 * eka penne naa.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
        ErrorCode code = ex.getCode();
        log.debug("API exception [{}]: {}", code, ex.getMessage());
        return ResponseEntity
                .status(code.status())
                .body(ApiError.of(code.status().value(), code.name(), ex.getMessage(), req.getRequestURI()));
    }

    /** @Valid fail unama — field ekakata message ekak denawa. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        return ResponseEntity.badRequest().body(ApiError.withFields(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.VALIDATION_FAILED.name(),
                "Request validation failed",
                req.getRequestURI(),
                fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest req) {
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.MALFORMED_REQUEST.name(),
                "Request body is missing or malformed",
                req.getRequestURI()));
    }

    /**
     * DB constraint eka break unama. Me project eke lokuma use case eka:
     * uq_active_booking_slot partial unique index eka. Race condition ekakedi
     * application check eka pass unath, DB eka meken nawaththanawa.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex,
                                                    HttpServletRequest req) {
        String root = ex.getMostSpecificCause().getMessage();
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), root);

        ErrorCode code = ErrorCode.BOOKING_CONFLICT;
        String message = "The request conflicts with existing data";

        if (root != null) {
            if (root.contains("uq_active_booking_slot")) {
                code = ErrorCode.SLOT_ALREADY_BOOKED;
                message = "This time slot was just booked by someone else. Please pick another slot.";
            } else if (root.contains("uq_users_email")) {
                code = ErrorCode.EMAIL_ALREADY_EXISTS;
                message = "An account with this email already exists";
            }
        }
        return ResponseEntity.status(code.status())
                .body(ApiError.of(code.status().value(), code.name(), message, req.getRequestURI()));
    }

    /** @Version conflict — dennek ekama row eka ekawara update kalama. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                         HttpServletRequest req) {
        log.warn("Optimistic lock conflict on {}", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                HttpStatus.CONFLICT.value(),
                ErrorCode.BOOKING_CONFLICT.name(),
                "This record was modified by another request. Please retry.",
                req.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                ErrorCode.INVALID_CREDENTIALS.name(),
                "Invalid email or password",
                req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                ErrorCode.ACCESS_DENIED.name(),
                "You do not have permission to perform this action",
                req.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex,
                                                     HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                HttpStatus.NOT_FOUND.value(),
                ErrorCode.RESOURCE_NOT_FOUND.name(),
                "Endpoint not found",
                req.getRequestURI()));
    }

    /** Balaporoththu novu ewa. Client ekata details denne naa. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred",
                req.getRequestURI()));
    }
}
