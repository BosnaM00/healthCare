package org.example.healthcare.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.example.healthcare.common.exception.BusinessException;
import org.example.healthcare.dto.auth.GoogleAuthRequest;
import org.example.healthcare.dto.auth.LoginResponse;
import org.example.healthcare.model.User;
import org.example.healthcare.model.UserRole;
import org.example.healthcare.model.UserStatus;
import org.example.healthcare.repository.UserRepository;
import org.example.healthcare.security.AppUserDetails;
import org.example.healthcare.security.JwtTokenProvider;
import org.example.healthcare.service.GoogleAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${google.client.id}")
    private String googleClientId;

    @Override
    @Transactional
    public LoginResponse googleLogin(GoogleAuthRequest request) {

        // 1. Verify the ID token against Google's public keys
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(request.idToken());
        } catch (Exception e) {
            throw new BusinessException("Google token verification failed");
        }

        if (idToken == null) {
            throw new BusinessException("Invalid or expired Google ID token");
        }

        // 2. Extract claims from the verified token
        GoogleIdToken.Payload payload = idToken.getPayload();
        String googleId  = payload.getSubject();          // unique Google user identifier
        String email     = payload.getEmail();
        String firstName = (String) payload.get("given_name");
        String lastName  = (String) payload.get("family_name");

        // 3. Find or create the MediConnect user
        User user = resolveUser(googleId, email, firstName, lastName);

        // 4. Issue the internal JWT — same flow as email/password login
        AppUserDetails principal = new AppUserDetails(user);
        String token = jwtTokenProvider.generate(principal);

        return new LoginResponse(token, user.getId(), user.getRole(),
                user.getEmail(), user.getFirstName(), user.getLastName());
    }

    /**
     * Resolution order:
     *   1. Existing user already linked to this Google account → login directly.
     *   2. Existing user with matching email but no Google ID → link and login.
     *   3. No existing user → create a new PATIENT account (Google accounts are
     *      already verified, so status is set to ACTIVE immediately).
     */
    private User resolveUser(String googleId, String email,
                             String firstName, String lastName) {

        // Case 1: already linked
        Optional<User> byGoogleId = userRepository.findByGoogleId(googleId);
        if (byGoogleId.isPresent()) {
            return byGoogleId.get();
        }

        // Case 2: same email, not yet linked
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            existing.setGoogleId(googleId);
            return userRepository.save(existing);
        }

        // Case 3: brand-new user
        User newUser = User.builder()
                .email(email)
                .firstName(firstName != null ? firstName : "")
                .lastName(lastName  != null ? lastName  : "")
                .passwordHash(null)           // no local password for Google-only accounts
                .googleId(googleId)
                .role(UserRole.PATIENT)       // all new Google sign-ups default to PATIENT
                .status(UserStatus.ACTIVE)    // Google has already verified the email
                .build();

        return userRepository.save(newUser);
    }
}
