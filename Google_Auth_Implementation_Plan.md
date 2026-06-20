# Google Authentication — Implementation Plan
**Project:** MediConnect (healthCare backend + my-app frontend)  
**Scope:** Add "Sign in with Google" as an additional authentication method, fully compatible with the existing JWT-based auth system.  
**Constraint:** Zero changes to existing login/register flows, no breaking changes to any other feature area.

---

## Architecture Decision

The chosen approach is the **Backend ID Token Verification** pattern:

1. The frontend triggers Google Sign-In and receives a Google **ID token** (a signed JWT issued by Google).
2. The frontend sends that ID token to a new backend endpoint: `POST /api/v1/auth/google`.
3. The backend verifies the ID token's signature using Google's public keys via the `google-api-client` library.
4. The backend finds an existing user (by `google_id` or `email`) or creates a new one.
5. The backend issues the **same internal JWT** that the existing email/password flow already produces and returns the **same `LoginResponse`** shape.
6. The frontend stores the token in the existing Zustand auth store — no changes needed to any protected feature, API client, or role-based logic.

**Why this approach:**
- Reuses the entire existing JWT infrastructure (`JwtTokenProvider`, `JwtAuthFilter`, `AppUserDetails`, Zustand auth store, `api-client.ts`).
- Google-authenticated users are fully indistinguishable from email/password users after the initial handshake — all downstream code remains untouched.
- No session state or new cookie mechanism is introduced.
- Works perfectly with the custom client-side router in `App.tsx` (no redirect-based OAuth needed — the flow uses a popup).

---

## Prerequisites — Google Cloud Console Setup

These steps must be completed once before any code is written.

**Step 1 — Create or select a GCP project**
- Go to https://console.cloud.google.com
- Create a new project named `MediConnect` (or reuse an existing one).

**Step 2 — Enable the Google Identity API**
- Navigate to **APIs & Services → Library**.
- Search for **"Google Identity"** and enable it.
- Also enable **"People API"** (used internally by the sign-in flow).

**Step 3 — Create OAuth 2.0 credentials**
- Navigate to **APIs & Services → Credentials → Create Credentials → OAuth client ID**.
- Application type: **Web application**.
- Name: `MediConnect Web`.
- **Authorized JavaScript origins** (add all of these):
  - `http://localhost:5173`
  - `http://localhost:5174`
  - `http://localhost:3000`
  - Your production domain (e.g. `https://app.mediconnect.ro`)
- **Authorized redirect URIs**: Leave blank — the popup flow does not use redirects.
- Click **Create**.

**Step 4 — Save your credentials**
- Copy the **Client ID** (format: `xxxxxxxxxx-xxxx.apps.googleusercontent.com`). This goes in both backend and frontend env vars.
- Copy the **Client Secret**. This is used only if you ever do server-side code exchange — not needed in this plan.
- Store them securely (e.g. AWS Secrets Manager, `.env.local` for local dev). **Never commit them to source control.**

**Step 5 — Configure the OAuth consent screen**
- Navigate to **APIs & Services → OAuth consent screen**.
- User type: **External** (for users outside your organisation).
- Fill in App name (`MediConnect`), support email, and developer contact.
- Scopes: add `email`, `profile`, `openid` — these are the minimum required.
- Add test users if the app is not yet published (required while in "Testing" status).

---

## Backend Implementation

The backend changes are broken into 8 steps. All changes are **additive** — no existing class is deleted or fundamentally restructured.

---

### Step 1 — Add Maven Dependency (`pom.xml`)

**File:** `healthCare/pom.xml`

Add the Google API Client Library inside the `<dependencies>` block, after the existing Stripe dependency:

```xml
<!-- Google API Client — GoogleIdTokenVerifier for OAuth2 ID token validation -->
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.7.0</version>
</dependency>
```

This library provides `GoogleIdTokenVerifier`, which cryptographically verifies Google-issued ID tokens against Google's public JWKS endpoint. It handles key caching internally, so no manual HTTP calls are needed.

**Why this version:** 2.7.0 is the latest stable release of `google-api-client` and is compatible with Java 21 and Spring Boot 4.x. It does not conflict with any existing dependencies.

---

### Step 2 — Database Migration

**File to create:** `healthCare/src/main/resources/db/migration/V10__add_google_auth.sql`

```sql
-- ── Google OAuth2 support ──────────────────────────────────────────────────
-- 1. Make password_hash nullable so Google-only users can register without a
--    local password. Existing rows are unaffected (their value stays intact).
ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

-- 2. Add google_id column to store the Google subject identifier ("sub" claim).
--    VARCHAR(255) is intentionally generous — Google sub values are currently
--    21-digit numeric strings but the spec does not guarantee a fixed length.
ALTER TABLE users
    ADD COLUMN google_id VARCHAR(255);

-- 3. Unique constraint: one Google account maps to at most one MediConnect user.
ALTER TABLE users
    ADD CONSTRAINT uk_users_google_id UNIQUE (google_id);
```

**Safety notes:**
- `ALTER COLUMN ... DROP NOT NULL` is non-destructive — it only relaxes the constraint. All existing password hashes remain stored.
- The `google_id` column defaults to `NULL`, so all existing user rows are unaffected.
- The unique constraint on `google_id` prevents the same Google account being linked to two different MediConnect accounts.
- Flyway will apply this as V10, after the existing V1–V9 migrations.

---

### Step 3 — Update the `User` Entity

**File:** `healthCare/src/main/java/org/example/healthcare/model/User.java`

Two changes are needed:

**Change A — Make `passwordHash` nullable in the JPA mapping:**

```java
// BEFORE:
@Column(name = "password_hash", nullable = false)
private String passwordHash;

// AFTER:
@Column(name = "password_hash", nullable = true)   // Google-only users have no local password
private String passwordHash;
```

**Change B — Add the `googleId` field below `passwordHash`:**

```java
@Column(name = "google_id", length = 255, unique = true)
private String googleId;
```

No other changes to `User.java` are required. The Lombok `@Builder`, `@Getter`, `@Setter` annotations already cover the new field. `@Builder.Default` is not needed here since `null` is the correct default.

---

### Step 4 — Update `AppUserDetails`

**File:** `healthCare/src/main/java/org/example/healthcare/security/AppUserDetails.java`

The constructor currently calls `user.getPasswordHash()` to populate the Spring Security `password` field. For Google-only users this will be `null`. Spring Security tolerates a `null` or empty password in `UserDetails` as long as the `passwordEncoder.matches()` method is never called for that user — which it won't be, since Google users authenticate via the new endpoint, not the existing `login` endpoint.

However, to be defensive and avoid potential `NullPointerException` in any future code path, change the constructor assignment:

```java
// BEFORE:
this.password = user.getPasswordHash();

// AFTER:
this.password = user.getPasswordHash() != null ? user.getPasswordHash() : "";
```

This one-line change is the only modification needed to this class.

---

### Step 5 — Update `UserRepository`

**File:** `healthCare/src/main/java/org/example/healthcare/repository/UserRepository.java`

Add one new query method. The existing methods remain untouched:

```java
/**
 * Looks up a user by their Google subject identifier.
 * Used during Google Sign-In to check if this Google account is already linked.
 */
Optional<User> findByGoogleId(String googleId);
```

Spring Data JPA derives the SQL for this method automatically from the method name — no `@Query` annotation is needed.

---

### Step 6 — Create the DTO

**File to create:** `healthCare/src/main/java/org/example/healthcare/dto/auth/GoogleAuthRequest.java`

```java
package org.example.healthcare.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/auth/google.
 * The frontend obtains the ID token from Google Sign-In and passes it here
 * for backend verification.
 */
public record GoogleAuthRequest(
        @NotBlank(message = "Google ID token must not be blank")
        String idToken
) {}
```

The response DTO is the **existing** `LoginResponse` record — no new response class is needed, because the response shape is identical to the email/password login.

---

### Step 7 — Create the Google Auth Service

#### Interface

**File to create:** `healthCare/src/main/java/org/example/healthcare/service/GoogleAuthService.java`

```java
package org.example.healthcare.service;

import org.example.healthcare.dto.auth.GoogleAuthRequest;
import org.example.healthcare.dto.auth.LoginResponse;

public interface GoogleAuthService {
    LoginResponse googleLogin(GoogleAuthRequest request);
}
```

#### Implementation

**File to create:** `healthCare/src/main/java/org/example/healthcare/service/impl/GoogleAuthServiceImpl.java`

```java
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
```

**Key design decisions:**
- **Case 2 (email match, no Google link):** If an existing user who registered with email/password signs in with Google using the same email, their account is automatically linked. They can now use either method to log in going forward. Their existing data (bookings, payments, consultations) is fully preserved.
- **Role:** New Google sign-ups default to `PATIENT`. If a medic or admin needs Google login, an admin can update their role via the existing user management endpoint — no special handling is needed.
- **Status:** Google-authenticated users are created as `ACTIVE` (not `PENDING_VERIFICATION`) because Google has already verified email ownership. This bypasses any email verification step that email/password users might otherwise need.
- **`@Transactional`:** The method is transactional so that user creation and any subsequent DB operations are atomic.

---

### Step 8 — Add the Endpoint to `AuthController`

**File:** `healthCare/src/main/java/org/example/healthcare/controller/AuthController.java`

Add one new import and one new method. The existing `login` method is untouched:

```java
// New import to add:
import org.example.healthcare.dto.auth.GoogleAuthRequest;
import org.example.healthcare.service.GoogleAuthService;

// Add field after AuthService:
private final GoogleAuthService googleAuthService;

// New endpoint to add:
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
```

The `@RequiredArgsConstructor` Lombok annotation on the class will automatically inject `GoogleAuthService` once the field is declared — no additional `@Autowired` is needed.

---

### Step 9 — Update `SecurityConfig`

**File:** `healthCare/src/main/java/org/example/healthcare/security/SecurityConfig.java`

Add one line to the `authorizeHttpRequests` block, immediately after the existing `POST /api/v1/auth/login` permit:

```java
// Existing line (do not change):
.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()

// New line to add directly below:
.requestMatchers(HttpMethod.POST, "/api/v1/auth/google").permitAll()
```

This is the only change to `SecurityConfig`. All other security rules, CORS configuration, and the JWT filter remain exactly as they are.

---

### Step 10 — Update `application.properties`

**File:** `healthCare/src/main/resources/application.properties`

Add the following block at the end of the file, after the video/Daily.co section:

```properties
# ── Google OAuth2 ─────────────────────────────────────────────────────────────
# Client ID from Google Cloud Console (APIs & Services → Credentials).
# Used by GoogleIdTokenVerifier to validate the 'aud' claim in incoming ID tokens.
# Never commit the real value — always use an environment variable.
google.client.id=${GOOGLE_CLIENT_ID:your-google-client-id.apps.googleusercontent.com}
```

For local development, set the real value in your shell:
```bash
export GOOGLE_CLIENT_ID=xxxxxxxxxx-xxxx.apps.googleusercontent.com
```

---

## Frontend Implementation

All frontend changes are confined to the `auth` feature slice and `main.tsx`. No other feature, component, hook, or store is modified.

---

### Step 1 — Install the NPM Package

**File:** `my-app/package.json`

Run the following command in the `my-app` directory:

```bash
npm install @react-oauth/google
```

This adds `@react-oauth/google` to `dependencies`. The package supports React 19 and provides both a `GoogleOAuthProvider` context component and a `GoogleLogin` button component. It handles the entire popup-based OAuth flow internally and exposes the Google ID token through the `onSuccess` callback.

No other packages need to be installed. The `oidc-client-ts` and `react-oidc-context` packages already in `package.json` are used for a different flow and are not involved here.

---

### Step 2 — Add the Environment Variable

**File:** `my-app/.env.local`

Add one line:

```env
VITE_GOOGLE_CLIENT_ID=xxxxxxxxxx-xxxx.apps.googleusercontent.com
```

Also update `.env.example` (or `.env.local.example` if it exists) with the placeholder:

```env
VITE_GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
```

Access it in code via `import.meta.env.VITE_GOOGLE_CLIENT_ID` — consistent with how `VITE_API_URL` and `VITE_STRIPE_PUBLISHABLE_KEY` are already accessed in the project.

---

### Step 3 — Wrap the App with `GoogleOAuthProvider`

**File:** `my-app/src/main.tsx`

`GoogleOAuthProvider` must wrap the entire app so the Google context is available everywhere. Add it around the existing `QueryClientProvider` (or `StrictMode`):

```tsx
// New import to add:
import { GoogleOAuthProvider } from '@react-oauth/google'

// Wrap the existing render tree:
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <GoogleOAuthProvider clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID}>
      {/* existing providers stay exactly as they are */}
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </GoogleOAuthProvider>
  </StrictMode>
)
```

Only `GoogleOAuthProvider` is added — the existing structure is preserved.

---

### Step 4 — Add the `useGoogleAuth` Hook

**File:** `my-app/src/features/auth/hooks/use-auth.ts`

Add the following export at the end of the file. All existing exports (`useLogin`, `useMfaVerify`, `usePasswordResetRequest`, `usePasswordResetConfirm`) remain unchanged:

```typescript
// New import to add at the top of the file:
import { useGoogleLogin } from '@react-oauth/google'

// New hook to add at the bottom of the file:
export function useGoogleAuth(onSuccess?: () => void) {
  const { setUser } = useAuthStore()

  const mutation = useMutation({
    mutationFn: (idToken: string) =>
      api.post<LoginResponse>('/auth/google', { idToken }),
    onSuccess: (data) => {
      const user: User = {
        id:        data.userId,
        email:     data.email,
        role:      data.role,
        firstName: data.firstName,
        lastName:  data.lastName,
      }
      setUser(user, data.token)
      toast({
        title:       'Welcome!',
        description: `Signed in as ${data.firstName}`,
        variant:     'default',
      })
      onSuccess?.()
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        toast({
          title:       error.problem.title,
          description: error.problem.detail,
          variant:     'destructive',
        })
      }
    },
  })

  const triggerGoogleLogin = useGoogleLogin({
    onSuccess: (tokenResponse) => {
      // tokenResponse.access_token is the access token from Google.
      // However, for backend ID token verification we need the credential JWT.
      // We use the GoogleLogin component instead (see GoogleLoginButton.tsx),
      // which provides the ID token directly via credentialResponse.credential.
      // This hook is kept as a fallback for programmatic use if needed.
    },
    onError: () => {
      toast({
        title:       'Google Sign-In failed',
        description: 'Could not open the Google sign-in window. Please try again.',
        variant:     'destructive',
      })
    },
  })

  return {
    sendIdToken:   mutation.mutate,      // call with idToken string
    isPending:     mutation.isPending,
    triggerGoogle: triggerGoogleLogin,
  }
}
```

---

### Step 5 — Create the `GoogleLoginButton` Component

**File to create:** `my-app/src/features/auth/components/GoogleLoginButton.tsx`

```tsx
import { GoogleLogin, type CredentialResponse } from '@react-oauth/google'
import { useAuthStore } from '@/stores/auth.store'
import { useMutation } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { toast } from '@/hooks/use-toast'
import type { User } from '@/types'

interface LoginResponse {
  token: string
  userId: string
  role: User['role']
  email: string
  firstName: string
  lastName: string
}

interface GoogleLoginButtonProps {
  onSuccess?: () => void
}

/**
 * Renders Google's official Sign-In button.
 * On success, exchanges the Google ID token for a MediConnect JWT
 * and stores it in the auth store — identical to the email/password flow.
 */
export function GoogleLoginButton({ onSuccess }: GoogleLoginButtonProps) {
  const { setUser } = useAuthStore()

  const mutation = useMutation({
    mutationFn: (idToken: string) =>
      api.post<LoginResponse>('/auth/google', { idToken }),
    onSuccess: (data) => {
      const user: User = {
        id:        data.userId,
        email:     data.email,
        role:      data.role,
        firstName: data.firstName,
        lastName:  data.lastName,
      }
      setUser(user, data.token)
      toast({
        title:       'Welcome!',
        description: `Signed in as ${data.firstName}`,
        variant:     'default',
      })
      onSuccess?.()
    },
    onError: (error) => {
      const detail = error instanceof ApiError
        ? error.problem.detail
        : 'An unexpected error occurred. Please try again.'
      toast({
        title:       'Sign-in failed',
        description: detail,
        variant:     'destructive',
      })
    },
  })

  const handleCredentialResponse = (credentialResponse: CredentialResponse) => {
    if (!credentialResponse.credential) {
      toast({
        title:       'Google Sign-In failed',
        description: 'No credential received from Google. Please try again.',
        variant:     'destructive',
      })
      return
    }
    // credentialResponse.credential is the Google ID token (a JWT).
    // Send it to the backend for verification.
    mutation.mutate(credentialResponse.credential)
  }

  return (
    <div className="w-full flex justify-center">
      <GoogleLogin
        onSuccess={handleCredentialResponse}
        onError={() => {
          toast({
            title:       'Google Sign-In failed',
            description: 'The sign-in popup was closed or blocked. Please try again.',
            variant:     'destructive',
          })
        }}
        useOneTap={false}       // disable One Tap to avoid UX conflicts with the form
        width="360"             // approximate width to match the form inputs
        theme="outline"
        shape="rectangular"
        text="signin_with_google"
        locale="en"
      />
    </div>
  )
}
```

**Notes:**
- `useOneTap={false}` — disables the One Tap prompt that can appear automatically, which would conflict with the page's layout and UX. Users sign in explicitly by clicking the button.
- `credentialResponse.credential` — this is exactly the Google ID token (a JWT) that the backend `GoogleIdTokenVerifier` expects.
- The component is fully self-contained and does not depend on `useGoogleAuth` — it handles everything internally to keep the abstraction clean.

---

### Step 6 — Update `LoginPage.tsx`

**File:** `my-app/src/features/auth/components/LoginPage.tsx`

Two changes:

**Change A — Import the new component** (add to top of file):
```tsx
import { GoogleLoginButton } from './GoogleLoginButton'
```

**Change B — Add the Google button** above the existing "New to MediConnect?" separator section.

Locate this block in the JSX (lines 158–168 of the current file):
```tsx
<div className="relative">
  <Separator />
  <span className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 bg-[--color-background] px-2 text-xs text-[--color-text-secondary]">
    New to MediConnect?
  </span>
</div>

<Button variant="outline" className="w-full" onClick={onRegister}>
  Create an account
</Button>
```

Replace it with:
```tsx
{/* Google Sign-In — separator above */}
<div className="relative">
  <Separator />
  <span className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 bg-[--color-background] px-2 text-xs text-[--color-text-secondary]">
    or
  </span>
</div>

<GoogleLoginButton onSuccess={onSuccess} />

{/* Register separator — separator below */}
<div className="relative">
  <Separator />
  <span className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 bg-[--color-background] px-2 text-xs text-[--color-text-secondary]">
    New to MediConnect?
  </span>
</div>

<Button variant="outline" className="w-full" onClick={onRegister}>
  Create an account
</Button>
```

This inserts the Google button between the password form and the "Create an account" button, separated by an "or" label. The existing form structure, dev credentials hint, and register button are all preserved in their current positions.

---

### Step 7 — Update MSW Mock Handlers

**File:** `my-app/src/mocks/handlers/auth.handlers.ts`

Add one new handler to the `authHandlers` array. All existing handlers remain unchanged:

```typescript
// Add this at the end of the authHandlers array, before the closing bracket:

// POST /auth/google — mock Google authentication
http.post(`${BASE}/auth/google`, async ({ request }) => {
  await delay(400)
  const body = await request.json() as { idToken?: string }

  // In dev/test mode, accept any non-empty token and return the first patient mock user
  if (!body.idToken) {
    return HttpResponse.json(
      { type: 'about:blank', title: 'Bad Request', status: 400,
        detail: 'Google ID token is required.' },
      { status: 400 }
    )
  }

  // Return a mock Google-authenticated patient user
  const googleUser = mockUsers.find(u => u.role === 'PATIENT') ?? mockUsers[0]
  return HttpResponse.json({
    token:     `mock-google-jwt-token-${googleUser.id}`,
    userId:    googleUser.id,
    role:      googleUser.role,
    email:     googleUser.email,
    firstName: googleUser.firstName,
    lastName:  googleUser.lastName,
  })
}),
```

This allows the Google sign-in button to work in development even when `VITE_MOCK_API=true`, so frontend development does not depend on a running backend or real Google credentials.

---

## Environment Variables Summary

| Variable | Where | Example Value | Purpose |
|----------|-------|---------------|---------|
| `GOOGLE_CLIENT_ID` | Backend (env / Secrets Manager) | `123456-abc.apps.googleusercontent.com` | Google ID token audience verification |
| `VITE_GOOGLE_CLIENT_ID` | Frontend `.env.local` | `123456-abc.apps.googleusercontent.com` | Initializes `GoogleOAuthProvider` in the browser |

Both variables must contain the **same** Client ID value. The backend uses it to verify that the `aud` claim in the incoming ID token matches the registered application — preventing token substitution attacks.

---

## Complete File Change Summary

### Backend (`healthCare`)

| File | Change Type | Summary |
|------|-------------|---------|
| `pom.xml` | Modified | Add `google-api-client` 2.7.0 dependency |
| `db/migration/V10__add_google_auth.sql` | **New file** | Make `password_hash` nullable; add `google_id` column with unique constraint |
| `model/User.java` | Modified | Add `googleId` field; change `passwordHash` to `nullable = true` |
| `security/AppUserDetails.java` | Modified | Null-safe `passwordHash` assignment (return `""` if null) |
| `repository/UserRepository.java` | Modified | Add `findByGoogleId(String)` method |
| `dto/auth/GoogleAuthRequest.java` | **New file** | Record with `idToken` field + `@NotBlank` validation |
| `service/GoogleAuthService.java` | **New file** | Interface with `googleLogin()` method |
| `service/impl/GoogleAuthServiceImpl.java` | **New file** | Full implementation: verify token, find/create user, return JWT |
| `controller/AuthController.java` | Modified | Add `POST /auth/google` endpoint + inject `GoogleAuthService` |
| `security/SecurityConfig.java` | Modified | Permit `POST /api/v1/auth/google` |
| `resources/application.properties` | Modified | Add `google.client.id` property |

### Frontend (`my-app`)

| File | Change Type | Summary |
|------|-------------|---------|
| `package.json` | Modified | Add `@react-oauth/google` dependency |
| `.env.local` | Modified | Add `VITE_GOOGLE_CLIENT_ID` |
| `src/main.tsx` | Modified | Wrap app with `GoogleOAuthProvider` |
| `src/features/auth/hooks/use-auth.ts` | Modified | Add `useGoogleAuth` hook (additive only) |
| `src/features/auth/components/GoogleLoginButton.tsx` | **New file** | Self-contained Google Sign-In button component |
| `src/features/auth/components/LoginPage.tsx` | Modified | Import and render `GoogleLoginButton` between form and register button |
| `src/mocks/handlers/auth.handlers.ts` | Modified | Add mock handler for `POST /auth/google` |

**Total new files: 5 (3 backend, 2 frontend)**  
**Total modified files: 12 (8 backend, 4 frontend)**  
**Total deleted files: 0**

---

## Non-Breaking Guarantees

The following areas are completely unaffected by these changes:

- **Existing email/password login** — `AuthController.login()`, `AuthServiceImpl.login()`, and the frontend `useLogin()` hook are unchanged. All existing users continue to log in exactly as before.
- **JWT validation** — `JwtAuthFilter`, `JwtTokenProvider`, and the `Authorization: Bearer` header mechanism are unchanged. Google-authenticated users receive the same JWT format.
- **All protected endpoints** — No security rules are modified except adding one new permitted endpoint.
- **Booking, Payment, Consultation, Stripe, Video, Disputes, Audit Logs** — None of these feature areas are touched.
- **CASL permissions** — Roles and permissions are unchanged. Google-authenticated users have roles stored in the database and JWT claims just like email/password users.
- **MSW mock API** — The existing handlers are not modified; one additive handler is appended.
- **MFA and password reset flows** — Unchanged. Google users can ignore these flows entirely.
- **Quartz scheduler** — Unchanged.
- **Database schema for all other tables** — V10 only modifies the `users` table; all other tables are untouched.
- **CORS** — The new endpoint is served under `/api/v1/**` which is already covered by the existing CORS configuration.

---

## Testing Checklist

After implementation, verify each scenario:

**Backend integration tests:**
- [ ] `POST /api/v1/auth/google` with a valid Google ID token → returns 200 with JWT + user fields
- [ ] `POST /api/v1/auth/google` with an invalid or expired token → returns 400/401 with error body
- [ ] `POST /api/v1/auth/google` without a body → returns 400 (Bean Validation kicks in)
- [ ] `POST /api/v1/auth/google` with a valid token for a brand-new email → creates a new PATIENT user in DB
- [ ] `POST /api/v1/auth/google` with a valid token for an existing email (Case 2) → links `google_id` to existing user, existing data preserved
- [ ] `POST /api/v1/auth/google` with a valid token for an already-linked Google account (Case 1) → logs in directly, no duplicate user
- [ ] `POST /api/v1/auth/login` (email/password) → still works identically for all existing users
- [ ] All other protected endpoints → still require valid JWT, unaffected

**Frontend tests:**
- [ ] Google Sign-In button appears on `LoginPage` between the form and "Create an account"
- [ ] Clicking the button opens Google's popup
- [ ] Successful Google sign-in → user is stored in Zustand auth store → redirected to correct dashboard for their role
- [ ] Failed Google sign-in (popup closed) → toast error shown, no crash
- [ ] Email/password login → unchanged, still works
- [ ] `VITE_MOCK_API=true` + Google Sign-In → mock handler returns a mock patient user
- [ ] Token stored in `localStorage` under `mc-auth` key → persists across page refresh

**End-to-end scenarios:**
- [ ] New user: Google sign-in → lands on Patient Dashboard → can book a consultation → payment works
- [ ] Existing email/password user: Google sign-in with same email → same account, all existing bookings visible
- [ ] Admin logs in via email/password → Google button visible but unrelated to admin flow, admin capabilities unchanged
