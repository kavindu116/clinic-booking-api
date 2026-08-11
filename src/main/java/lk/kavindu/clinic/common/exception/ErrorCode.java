package lk.kavindu.clinic.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Har error ekakatama stable code ekak. Client eka message eka parse
 * karanne naa, code eka balanawa — ee nisa message eka wenas kalath
 * client eka kadenne naa.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED),

    ACCESS_DENIED(HttpStatus.FORBIDDEN),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    DOCTOR_NOT_FOUND(HttpStatus.NOT_FOUND),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    SLOT_ALREADY_BOOKED(HttpStatus.CONFLICT),
    BOOKING_CONFLICT(HttpStatus.CONFLICT),

    SLOT_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    BOOKING_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
