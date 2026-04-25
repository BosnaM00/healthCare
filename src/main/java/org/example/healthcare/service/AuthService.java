package org.example.healthcare.service;

import org.example.healthcare.dto.auth.LoginRequest;
import org.example.healthcare.dto.auth.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}
