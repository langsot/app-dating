package ru.langsot.dating.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.langsot.dating.dto.Dtos.*;
import ru.langsot.dating.exception.BadRequestException;
import ru.langsot.dating.service.ProfileService;
import ru.langsot.dating.service.UserService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profileService;
    private final UserService userService;

    public ProfileController(ProfileService profileService, UserService userService) {
        this.profileService = profileService;
        this.userService = userService;
    }

    @GetMapping("/profile/me")
    public ProfileDto getMe(@CurrentUser Long userId) {
        return profileService.getMyProfile(userId);
    }

    @PutMapping("/profile/me")
    public ProfileDto update(@CurrentUser Long userId, @Valid @RequestBody ProfileUpdateRequest req) {
        return profileService.updateProfile(userId, req);
    }

    @PostMapping("/profile/me/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoDto upload(@CurrentUser Long userId, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("Файл не выбран");
        }
        return profileService.uploadPhoto(userId, file.getBytes(), file.getContentType());
    }

    @DeleteMapping("/profile/me/photos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@CurrentUser Long userId, @PathVariable Long id) {
        profileService.deletePhoto(userId, id);
    }

    @PatchMapping("/profile/me/visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void visibility(@CurrentUser Long userId, @RequestBody VisibilityRequest req) {
        userService.setHidden(userId, req.hidden());
    }

    @DeleteMapping("/profile/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@CurrentUser Long userId) {
        userService.deactivate(userId);
    }

    @GetMapping("/interests")
    public List<InterestDto> interests() {
        return profileService.listInterests();
    }
}
