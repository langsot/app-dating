package ru.langsot.dating.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import ru.langsot.dating.config.AppProperties;
import ru.langsot.dating.exception.BadRequestException;
import ru.langsot.dating.exception.NotFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Хранение файлов фотографий в файловой системе сервера. */
@Service
public class PhotoStorageService {

    public static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png");
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{32}\\.(jpg|png)");

    private final Path root;

    public PhotoStorageService(AppProperties props) {
        this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Сохраняет файл и возвращает ключ хранилища. */
    public String store(byte[] content, String contentType) {
        String ext = EXTENSIONS.get(contentType);
        if (ext == null || !matchesSignature(content, ext)) {
            throw new BadRequestException("Допускаются только изображения JPEG и PNG");
        }
        if (content.length == 0 || content.length > MAX_SIZE) {
            throw new BadRequestException("Размер файла должен быть не более 5 МБ");
        }
        String key = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Files.write(root.resolve(key), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return key;
    }

    public Resource load(String key) {
        Path file = resolve(key);
        if (!Files.exists(file)) {
            throw new NotFoundException("Файл не найден");
        }
        return new FileSystemResource(file);
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new NotFoundException("Файл не найден");
        }
        return root.resolve(key);
    }

    /** Проверка «магических» байтов, чтобы не принимать произвольные файлы с подменным типом. */
    static boolean matchesSignature(byte[] c, String ext) {
        if ("jpg".equals(ext)) {
            return c.length > 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF;
        }
        return c.length > 8 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G';
    }
}
