package com.studytogether.api.controller;

import com.studytogether.api.service.ExternalAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class ExternalController {
    private final ExternalAuthService externalAuthService;

    public ExternalController(ExternalAuthService externalAuthService) {
        this.externalAuthService = externalAuthService;
    }

    @GetMapping("/auth/google")
    public RedirectView googleStart(HttpServletRequest request, HttpServletResponse response) {
        return externalAuthService.googleStart(request, response);
    }

    @GetMapping("/auth/google/callback")
    public RedirectView googleCallback(HttpServletRequest request, HttpServletResponse response) {
        return externalAuthService.googleCallback(request, response);
    }
}
