package ru.langsot.dating.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.langsot.dating.TestData;
import ru.langsot.dating.config.AppProperties;
import ru.langsot.dating.exception.BadRequestException;
import ru.langsot.dating.exception.NotFoundException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PhotoStorageService — хранение фотографий")
class PhotoStorageServiceTest {

    @TempDir
    Path dir;

    private PhotoStorageService service() {
        return new PhotoStorageService(new AppProperties(null, null, new AppProperties.Storage(dir.toString()), null));
    }

    @Test
    @DisplayName("Т-15 PNG сохраняется под случайным ключом и удаляется")
    void storeAndDelete() {
        PhotoStorageService s = service();
        String key = s.store(TestData.png(), "image/png");
        assertThat(key).matches("[0-9a-f]{32}\\.png");
        assertThat(Files.exists(dir.resolve(key))).isTrue();
        s.delete(key);
        assertThat(Files.exists(dir.resolve(key))).isFalse();
    }

    @Test
    @DisplayName("Т-16 файл с подмененным типом (текст как image/jpeg) отклоняется")
    void wrongSignature() {
        assertThatThrownBy(() -> service().store("hello".getBytes(), "image/jpeg"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service().store(TestData.png(), "image/gif"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Т-17 файл больше 5 МБ отклоняется")
    void tooLarge() {
        byte[] big = new byte[(int) PhotoStorageService.MAX_SIZE + 1];
        System.arraycopy(TestData.png(), 0, big, 0, 8);
        assertThatThrownBy(() -> service().store(big, "image/png")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("5 МБ");
    }

    @Test
    @DisplayName("Т-18 ключ с выходом за каталог (../) не принимается")
    void pathTraversal() {
        assertThatThrownBy(() -> service().load("../../etc/passwd")).isInstanceOf(NotFoundException.class);
    }
}
