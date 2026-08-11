package lk.kavindu.clinic.common.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Har error response ekakma me shape ekamai. Consistent errors =
 * client-side handling ekata pahasui.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, null);
    }

    public static ApiError withFields(int status, String code, String message,
                                      String path, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, code, message, path, fieldErrors);
    }
}
