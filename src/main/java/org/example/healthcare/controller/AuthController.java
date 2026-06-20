package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.auth.GoogleAuthRequest;
import org.example.healthcare.dto.auth.LoginRequest;
import org.example.healthcare.dto.auth.LoginResponse;
import org.example.healthcare.service.AuthService;
import org.example.healthcare.service.GoogleAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;

    /**
     * POST /api/v1/auth/login
     * Authenticate with email + password; returns a signed JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * POST /api/v1/auth/google
     * Exchange a Google ID token for a MediConnect JWT.
     * The ID token is obtained by the frontend via Google Sign-In.
     */
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> googleLogin(
            @Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(googleAuthService.googleLogin(request));
    }
}
