package lk.kavindu.clinic.auth.dto;

import lk.kavindu.clinic.user.User;

public record UserSummary(
        Long id,
        String email,
        String fullName,
        String role
) {
    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(),
                user.getFullName(), user.getRole().name());
    }
}
