package lk.kavindu.clinic.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.kavindu.clinic.user.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Har request ekakatama ekwarak duwanawa. Bearer token eka validate karala
 * SecurityContext eka set karanawa.
 *
 * Wadagath: token eka DB eken load karanne naa — claims walin principal eka
 * hadanawa. Ee nisa request ekakata DB hit ekak adui.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);

        if (header == null || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length()).trim();

        try {
            Claims claims = jwtService.parse(token);

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                AppUserPrincipal principal = new AppUserPrincipal(
                        jwtService.extractUserId(claims),
                        claims.get("email", String.class),
                        claims.get("name", String.class),
                        Role.valueOf(claims.get("role", String.class)));

                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (ExpiredJwtException ex) {
            log.debug("Expired JWT on {}", request.getRequestURI());
            SecurityContextHolder.clearContext();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT on {}: {}", request.getRequestURI(), ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
