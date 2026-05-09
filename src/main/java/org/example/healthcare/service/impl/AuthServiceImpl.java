package org.example.healthcare.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.dto.auth.LoginRequest;
import org.example.healthcare.dto.auth.LoginResponse;
import org.example.healthcare.model.User;
import org.example.healthcare.repository.UserRepository;
import org.example.healthcare.security.AppUserDetails;
import org.example.healthcare.security.JwtTokenProvider;
import org.example.healthcare.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()))
            throw new BusinessException("Invalid email or password");

        AppUserDetails principal = new AppUserDetails(user);
        String token = jwtTokenProvider.generate(principal);

        return new LoginResponse(token, user.getId(), user.getRole(),
                user.getEmail(), user.getFirstName(), user.getLastName());
    }
}
