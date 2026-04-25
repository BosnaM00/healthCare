package org.example.healthcare.service;

import org.example.healthcare.dto.user.UserRequest;
import org.example.healthcare.dto.user.UserResponse;
import org.example.healthcare.dto.user.UserStatusRequest;

import java.util.UUID;

public interface UserService {

    UserResponse register(UserRequest request);

    UserResponse getById(UUID id);

    UserResponse getCurrentUser(UUID principalId);

    UserResponse updateProfile(UUID id, UserRequest request);

    UserResponse updateStatus(UUID id, UserStatusRequest request);
}
