package ru.langsot.dating.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.langsot.dating.domain.*;
import ru.langsot.dating.dto.Dtos.*;
import ru.langsot.dating.exception.BadRequestException;
import ru.langsot.dating.exception.NotFoundException;
import ru.langsot.dating.repository.InterestRepository;
import ru.langsot.dating.repository.ProfileRepository;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/** Управление анкетой пользователя (ВИ-2, ВИ-6). */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profiles;
    private final InterestRepository interests;
    private final PhotoStorageService storage;
    private final UserService userService;

    public ProfileService(ProfileRepository profiles, InterestRepository interests,
                          PhotoStorageService storage, UserService userService) {
        this.profiles = profiles;
        this.interests = interests;
        this.storage = storage;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public ProfileDto getMyProfile(Long userId) {
        return DtoMapper.toDto(find(userId));
    }

    /** Создает анкету при первом сохранении или изменяет существующую. */
    @Transactional
    public ProfileDto updateProfile(Long userId, ProfileUpdateRequest req) {
        if (req.minAge() > req.maxAge()) {
            throw new BadRequestException("Минимальный возраст больше максимального");
        }
        Profile profile = profiles.findByUserId(userId).orElse(null);
        try {
            if (profile == null) {
                profile = new Profile(userService.get(userId), req.displayName(), req.birthDate(), req.gender());
            }
            profile.update(req.displayName(), req.birthDate(), req.gender(), req.bio(), req.city());
            profile.setPreferences(new SearchPreferences(req.preferredGender(), req.minAge(), req.maxAge()));
            profile.setInterests(resolveInterests(req.interests()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        ProfileDto saved = DtoMapper.toDto(profiles.save(profile));
        log.info("Анкета пользователя {} сохранена, заполнена: {}", userId, saved.complete());
        return saved;
    }

    @Transactional
    public PhotoDto uploadPhoto(Long userId, byte[] content, String contentType) {
        Profile profile = find(userId);
        if (profile.getPhotos().size() >= Profile.MAX_PHOTOS) {
            throw new BadRequestException("Можно загрузить не более " + Profile.MAX_PHOTOS + " фотографий");
        }
        String key = storage.store(content, contentType);
        Photo photo = profile.addPhoto(key);
        profiles.flush();
        log.info("Фото {} добавлено пользователем {}, размер {} байт", photo.getId(), userId, content.length);
        return new PhotoDto(photo.getId(), photo.getUrl(), photo.getPosition());
    }

    @Transactional
    public void deletePhoto(Long userId, Long photoId) {
        Profile profile = find(userId);
        try {
            Photo removed = profile.removePhoto(photoId);
            storage.delete(removed.getStorageKey());
            log.info("Фото {} удалено пользователем {}", photoId, userId);
        } catch (NoSuchElementException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    /** Безвозвратно удаляет учетную запись со всеми данными и файлами фотографий. */
    @Transactional
    public void deleteAccount(Long userId) {
        List<String> keys = profiles.findByUserId(userId)
                .map(p -> p.getPhotos().stream().map(Photo::getStorageKey).toList())
                .orElse(List.of());
        userService.delete(userId);
        keys.forEach(storage::delete);
        log.info("Учетная запись {} удалена, файлов фото: {}", userId, keys.size());
    }

    @Transactional(readOnly = true)
    public List<InterestDto> listInterests() {
        return DtoMapper.toDtos(interests.findAllByOrderByCategoryAscNameAsc());
    }

    private Set<Interest> resolveInterests(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return new HashSet<>();
        }
        Set<Interest> found = interests.findByNameIn(names);
        if (found.size() != names.size()) {
            Set<String> known = found.stream().map(Interest::getName).collect(Collectors.toSet());
            Set<String> unknown = new HashSet<>(names);
            unknown.removeAll(known);
            throw new BadRequestException("Неизвестные интересы: " + String.join(", ", unknown));
        }
        return found;
    }

    private Profile find(Long userId) {
        return profiles.findByUserId(userId).orElseThrow(() -> new NotFoundException("Анкета не создана"));
    }
}
