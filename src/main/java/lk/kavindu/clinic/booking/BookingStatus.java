package lk.kavindu.clinic.booking;

public enum BookingStatus {
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    public boolean isActive() {
        return this != CANCELLED;
    }
}
