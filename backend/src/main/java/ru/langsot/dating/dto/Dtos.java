package ru.langsot.dating.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/** Объекты передачи данных REST API (record-классы). */
public final class Dtos {

    private Dtos() {
    }

    public record AuthRequest(@NotBlank String initData) {
    }

    public record AuthResponse(String token, boolean newUser, boolean profileComplete, Long userId) {
    }

    public record ErrorDto(int status, String error, String message, List<String> details) {
    }
}
