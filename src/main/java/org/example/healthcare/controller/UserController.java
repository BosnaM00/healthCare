package org.example.healthcare.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.dto.user.UserRequest;
import org.example.healthcare.dto.user.UserResponse;
import org.example.healthcare.dto.user.UserStatusRequest;
import org.example.healthcare.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * POST /api/v1/users
     * Register a new user (PUBLIC).
     */
    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    /**
     * GET /api/v1/users/me
     * Get current authenticated user's profile (AUTH).
     * principalId is resolved from the JWT in a real implementation.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@RequestHeader("X-User-Id") UUID principalId) {
        return ResponseEntity.ok(userService.getCurrentUser(principalId));
    }

    /**
     * PUT /api/v1/users/me
     * Update current user's own profile (AUTH).
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @RequestHeader("X-User-Id") UUID principalId,
            @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.updateProfile(principalId, request));
    }

    /**
     * GET /api/v1/users/{id}
     * Get any user by id (ADMIN).
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    /**
     * PATCH /api/v1/users/{id}/status
     * Suspend or activate a user (ADMIN).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UserStatusRequest request) {
        return ResponseEntity.ok(userService.updateStatus(id, request));
    }
}
