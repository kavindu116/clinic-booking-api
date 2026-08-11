package lk.kavindu.clinic.user;

public enum Role {
    PATIENT,
    DOCTOR,
    ADMIN;

    /** Spring Security ekata "ROLE_" prefix eka one. */
    public String authority() {
        return "ROLE_" + name();
    }
}
