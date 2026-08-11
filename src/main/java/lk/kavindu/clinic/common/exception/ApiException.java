package lk.kavindu.clinic.common.exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static ApiException of(ErrorCode code, String message) {
        return new ApiException(code, message);
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, what + " not found");
    }
}
