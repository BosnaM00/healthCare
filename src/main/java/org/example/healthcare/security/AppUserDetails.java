package org.example.healthcare.security;

import lombok.Getter;
import org.example.healthcare.model.User;
import org.example.healthcare.model.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security principal backed by a {@link User} entity.
 * Exposes userId so controllers can extract it via {@code @AuthenticationPrincipal}.
 */
@Getter
public class AppUserDetails implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String password;
    private final UserRole role;

    public AppUserDetails(User user) {
        this.userId   = user.getId();
        this.email    = user.getEmail();
        this.password = user.getPasswordHash();
        this.role     = user.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired()     { return true; }

    @Override
    public boolean isAccountNonLocked()      { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled()               { return true; }
}
