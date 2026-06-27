package com.authservice.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityTest {

    private User buildUser(Role role) {
        return User.builder()
                .email("test@example.com")
                .password("encoded")
                .role(role)
                .build();
    }

    @Test
    void getAuthorities_returnsRoleAuthority() {
        User user = buildUser(Role.ROLE_USER);
        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();
        assertThat(authorities).hasSize(1);
        assertThat(authorities.iterator().next().getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    void getUsername_returnsEmail() {
        User user = buildUser(Role.ROLE_USER);
        assertThat(user.getUsername()).isEqualTo("test@example.com");
    }

    @Test
    void accountStatusMethods_allReturnTrue() {
        User user = buildUser(Role.ROLE_USER);
        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void defaultRole_isRoleUser() {
        User user = User.builder().email("a@b.com").password("pw").build();
        assertThat(user.getRole()).isEqualTo(Role.ROLE_USER);
    }
}
