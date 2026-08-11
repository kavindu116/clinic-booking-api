package lk.kavindu.clinic.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserSummary user
) {
    public static AuthResponse of(String access, String refresh, long expiresIn, UserSummary user) {
        return new AuthResponse(access, refresh, "Bearer", expiresIn, user);
    }
}
