package ru.langsot.dating.dto;

import jakarta.validation.constraints.*;
import ru.langsot.dating.domain.Gender;
import ru.langsot.dating.domain.GenderPreference;
import ru.langsot.dating.domain.LikeType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Объекты передачи данных REST API (record-классы). */
public final class Dtos {

    private Dtos() {
    }

    public record AuthRequest(@NotBlank String initData) {
    }

    public record AuthResponse(String token, boolean newUser, boolean profileComplete, Long userId) {
    }

    public record PhotoDto(Long id, String url, int position) {
    }

    public record ProfileDto(
            Long userId,
            String displayName,
            LocalDate birthDate,
            int age,
            Gender gender,
            String bio,
            String city,
            GenderPreference preferredGender,
            int minAge,
            int maxAge,
            List<PhotoDto> photos,
            Set<String> interests,
            boolean hidden,
            boolean complete) {
    }

    public record ProfileUpdateRequest(
            @NotBlank @Size(max = 64) String displayName,
            @NotNull @Past LocalDate birthDate,
            @NotNull Gender gender,
            @Size(max = 500) String bio,
            @Size(max = 64) String city,
            @NotNull GenderPreference preferredGender,
            @Min(18) @Max(99) int minAge,
            @Min(18) @Max(99) int maxAge,
            @Size(max = 10) Set<@NotBlank String> interests) {
    }

    public record VisibilityRequest(boolean hidden) {
    }

    public record FeedCardDto(Long userId, String displayName, int age, String city, String bio,
                              List<String> photoUrls, Set<String> interests, boolean superLikedYou) {
    }

    public record RateRequest(@NotNull LikeType type) {
    }

    public record RateResult(boolean matched, Long matchId, MatchDto match) {
        public static RateResult noMatch() {
            return new RateResult(false, null, null);
        }
    }

    public record MatchDto(Long id, Long partnerId, String partnerName, Integer partnerAge,
                           String partnerPhotoUrl, String partnerUsername, LocalDateTime matchedAt) {
    }

    public record InterestDto(String name, String category) {
    }

    public record ErrorDto(int status, String error, String message, List<String> details) {
    }
}
