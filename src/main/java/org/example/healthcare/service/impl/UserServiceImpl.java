package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.common.exception.ResourceNotFoundException;
import org.example.healthcare.dto.user.UserRequest;
import org.example.healthcare.dto.user.UserResponse;
import org.example.healthcare.dto.user.UserStatusRequest;
import org.example.healthcare.model.User;
import org.example.healthcare.model.UserStatus;
import org.example.healthcare.repository.UserRepository;
import org.example.healthcare.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(UserRequest request) {
        if (userRepository.existsByEmail(request.email()))
            throw new BusinessException("Email already registered");

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .status(UserStatus.PENDING_VERIFICATION)
                .stripeCustomerId(request.stripeCustomerId())
                .build();

        return toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public UserResponse getCurrentUser(UUID principalId) {
        return toResponse(findOrThrow(principalId));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UUID id, UserRequest request) {
        User user = findOrThrow(id);
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStripeCustomerId(request.stripeCustomerId());
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateStatus(UUID id, UserStatusRequest request) {
        User user = findOrThrow(id);
        user.setStatus(request.status());
        return toResponse(user);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getStripeCustomerId(),
                user.getCreatedAt()
        );
    }
}
