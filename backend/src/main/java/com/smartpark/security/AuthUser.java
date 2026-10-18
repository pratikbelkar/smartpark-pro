package com.smartpark.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Authenticated principal, available as {@code @AuthenticationPrincipal}. */
@Getter
public class AuthUser implements UserDetails {

    private final UUID id;
    private final String email;
    private final String name;
    private final List<String> roles;

    public AuthUser(UUID id, String email, String name, List<String> roles) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.roles = List.copyOf(roles);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
