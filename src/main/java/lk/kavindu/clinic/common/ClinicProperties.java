package lk.kavindu.clinic.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "app.clinic")
public record ClinicProperties (
    String timeZone,
    int maxUpcomingBookingsPerPatient,
    int cancellationWindowHours,
    int maxAdvanceBookingDays,
    int minAdvanceBookingMinutes
){
    public ZoneId zone(){
        return ZoneId.of(timeZone);
    }
}
