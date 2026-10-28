package ru.langsot.dating.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.langsot.dating.dto.Dtos.AuthRequest;
import ru.langsot.dating.dto.Dtos.AuthResponse;
import ru.langsot.dating.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/telegram")
    public AuthResponse login(@Valid @RequestBody AuthRequest req) {
        return authService.authenticate(req.initData());
    }
}
