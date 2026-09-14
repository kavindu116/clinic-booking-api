package lk.kavindu.clinic.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lk.kavindu.clinic.common.dto.ApiError;
import lk.kavindu.clinic.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.testcontainers.shaded.org.checkerframework.checker.units.qual.C;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rate-limit.enabled",havingValue = "true", matchIfMissing = true)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String KEY_PREFIX = "clinic:ratelimit:login:";

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)  {
        return !(HttpMethod.POST.matches(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI()));
    }


    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {

        String key = KEY_PREFIX +clientIp(request);
        long attempts;

        try {
            Long count = redis.opsForValue().increment(key);
            attempts = count== null ? 1L :count;

            if (attempts == 1L){
                redis.expire(key, Duration.ofSeconds(properties.windowSeconds()));
            }
        }catch (Exception ex){
            log.warn("Rate limiter unavailable, allowing request: {}", ex.getMessage());
            filterChain.doFilter(request, response);
            return;
        }
        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.loginAttempts()));
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, properties.loginAttempts() - attempts)));

        filterChain.doFilter(request, response);

    }

    private void reject(HttpServletRequest request,HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After",String.valueOf(properties.windowSeconds()));

        objectMapper.writeValue(response.getOutputStream(), ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ErrorCode.RATE_LIMITED.name(),
                "Too meny login attempts.Try again in "+ properties.windowSeconds() +"seconds",
                request.getRequestURI()
        ));
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if(ip != null && !ip.isBlank()){
            return ip.split(",")[0].trim();
        }
        return   request.getRemoteAddr();
    }
}
