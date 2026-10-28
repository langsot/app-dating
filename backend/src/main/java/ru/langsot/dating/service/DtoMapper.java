package ru.langsot.dating.service;

import ru.langsot.dating.domain.*;
import ru.langsot.dating.dto.Dtos.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Преобразование сущностей в объекты передачи данных. */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static ProfileDto toDto(Profile p) {
        SearchPreferences pref = p.getPreferences();
        return new ProfileDto(
                p.getUser().getId(), p.getDisplayName(), p.getBirthDate(), p.getAge(), p.getGender(),
                p.getBio(), p.getCity(), pref.getPreferredGender(), pref.getMinAge(), pref.getMaxAge(),
                p.getPhotos().stream().map(ph -> new PhotoDto(ph.getId(), ph.getUrl(), ph.getPosition())).toList(),
                interestNames(p), p.getUser().getStatus() == UserStatus.HIDDEN, p.isComplete());
    }

    private static Set<String> interestNames(Profile p) {
        return p.getInterests().stream().map(Interest::getName).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static List<InterestDto> toDtos(List<Interest> list) {
        return list.stream().map(i -> new InterestDto(i.getName(), i.getCategory())).toList();
    }
}
