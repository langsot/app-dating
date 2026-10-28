package ru.langsot.dating.web;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.langsot.dating.service.PhotoStorageService;

import java.time.Duration;

/** Выдача файлов фотографий. Ключ файла — случайный UUID, поэтому адрес нельзя подобрать. */
@RestController
public class PhotoController {

    private final PhotoStorageService storage;

    public PhotoController(PhotoStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/api/photos/{key}")
    public ResponseEntity<Resource> get(@PathVariable String key) {
        Resource file = storage.load(key);
        MediaType type = key.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(file);
    }
}
