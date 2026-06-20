package org.example.healthcare.service;

import org.example.healthcare.dto.auth.GoogleAuthRequest;
import org.example.healthcare.dto.auth.LoginResponse;

public interface GoogleAuthService {
    LoginResponse googleLogin(GoogleAuthRequest request);
}
