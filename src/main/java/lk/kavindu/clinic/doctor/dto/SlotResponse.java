package lk.kavindu.clinic.doctor.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalTime;

public record SlotResponse (
        Instant start,
        Instant end,
        @JsonFormat(pattern = "HH:mm")LocalTime localStart,
        @JsonFormat(pattern = "HH:mm") LocalTime localEnd,
        boolean available){
}
