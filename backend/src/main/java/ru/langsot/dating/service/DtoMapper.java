package ru.langsot.dating.service;

import ru.langsot.dating.domain.*;
import ru.langsot.dating.dto.Dtos.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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

    public static FeedCardDto toCard(Profile p, boolean superLikedYou) {
        return new FeedCardDto(p.getUser().getId(), p.getDisplayName(), p.getAge(), p.getCity(), p.getBio(),
                p.getPhotos().stream().map(Photo::getUrl).toList(), interestNames(p), superLikedYou);
    }

    public static MatchDto toDto(Match m, Long viewerId, Optional<Profile> partnerProfile) {
        User partner = m.partnerOf(viewerId);
        String name = partnerProfile.map(Profile::getDisplayName).orElse(partner.getFirstName());
        Integer age = partnerProfile.map(Profile::getAge).orElse(null);
        String photo = partnerProfile.flatMap(p -> p.getPhotos().stream().findFirst()).map(Photo::getUrl).orElse(null);
        return new MatchDto(m.getId(), partner.getId(), name, age, photo, partner.getUsername(), m.getMatchedAt());
    }

    private static Set<String> interestNames(Profile p) {
        return p.getInterests().stream().map(Interest::getName).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static List<InterestDto> toDtos(List<Interest> list) {
        return list.stream().map(i -> new InterestDto(i.getName(), i.getCategory())).toList();
    }
}
