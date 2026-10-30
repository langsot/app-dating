package ru.langsot.dating.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.langsot.dating.dto.Dtos.ErrorDto;
import ru.langsot.dating.exception.*;

import java.util.List;

/** Преобразование исключений в ответы с понятным сообщением (требование ТЗ А.4.2.3). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> validation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        return build(HttpStatus.BAD_REQUEST, "Проверьте заполнение полей", details);
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class, IllegalStateException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<ErrorDto> badRequest(Exception e) {
        String msg = e instanceof HttpMessageNotReadableException ? "Некорректный формат запроса" : e.getMessage();
        return build(HttpStatus.BAD_REQUEST, msg, List.of());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorDto> unauthorized(UnauthorizedException e) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage(), List.of());
    }

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorDto> notFound(Exception e) {
        return build(HttpStatus.NOT_FOUND, e instanceof NotFoundException ? e.getMessage() : "Ресурс не найден", List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorDto> conflict(ConflictException e) {
        return build(HttpStatus.CONFLICT, e.getMessage(), List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorDto> tooLarge(MaxUploadSizeExceededException e) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "Размер файла должен быть не более 5 МБ", List.of());
    }

    /** Клиент закрыл соединение во время передачи ответа: отвечать уже некому, и это не ошибка сервера. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void clientDisconnected(AsyncRequestNotUsableException e) {
        log.debug("Клиент закрыл соединение: {}", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> other(Exception e) {
        log.error("Необработанная ошибка", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", List.of());
    }

    private ResponseEntity<ErrorDto> build(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status)
                .body(new ErrorDto(status.value(), status.getReasonPhrase(), message, details));
    }
}
