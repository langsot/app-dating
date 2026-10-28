package ru.langsot.dating.dto;

import java.util.List;

/** Объекты передачи данных REST API (record-классы). */
public final class Dtos {

    private Dtos() {
    }

    public record ErrorDto(int status, String error, String message, List<String> details) {
    }
}
