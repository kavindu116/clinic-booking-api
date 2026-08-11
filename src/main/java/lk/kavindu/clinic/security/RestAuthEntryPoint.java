package lk.kavindu.clinic.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.kavindu.clinic.common.dto.ApiError;
import lk.kavindu.clinic.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring Security eke errors (401/403) filter chain eke wenne, controllers
 * wala nemei — ee nisa @RestControllerAdvice eka ee walata weda karanne naa.
 * Ekai me class eka. Errors okkoma ekama JSON shape eken enawa.
 */
@Component
@RequiredArgsConstructor
public class RestAuthEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, request, HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_INVALID,
                "Authentication required. Provide a valid Bearer token.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action");
    }

    private void write(HttpServletResponse response, HttpServletRequest request,
                       HttpStatus status, ErrorCode code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(status.value(), code.name(), message, request.getRequestURI()));
    }
}
