package com.studytogether.api.controller;

import com.studytogether.api.model.dto.response.AuthResponse;
import com.studytogether.api.model.dto.request.LoginRequest;
import com.studytogether.api.model.dto.response.OkResponse;
import com.studytogether.api.model.dto.request.RegisterRequest;
import com.studytogether.api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request, HttpServletResponse response) {
        return authService.register(request, response);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        return authService.login(request, response);
    }

    @PostMapping("/auth/logout")
    public OkResponse logout(HttpServletResponse response) {
        return authService.logout(response);
    }

    @GetMapping("/auth/me")
    public ResponseEntity<AuthResponse> me(HttpServletRequest request) {
        return authService.me(request);
    }
}
