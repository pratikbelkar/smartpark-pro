package com.smartpark.config;

import com.smartpark.user.AccountStatus;
import com.smartpark.user.Role;
import com.smartpark.user.RoleRepository;
import com.smartpark.user.Roles;
import com.smartpark.user.User;
import com.smartpark.user.UserRepository;
import com.smartpark.user.UserRole;
import com.smartpark.user.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Secure super-admin provisioning: created from environment on first start,
 * never through public registration (register() only grants CUSTOMER).
 * Dev defaults live in application.yml and MUST be overridden in production.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("app.admin.email/password not set — no super admin provisioned.");
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(adminEmail.trim())) {
            return;
        }
        Role admin = roleRepository.findByRoleName(Roles.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role missing — migrations not applied?"));

        User user = new User();
        user.setFullName("Platform Administrator");
        user.setEmail(adminEmail.trim().toLowerCase(java.util.Locale.ROOT));
        user.setPasswordHash(passwordEncoder.encode(adminPassword));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        user = userRepository.save(user);

        UserRole link = new UserRole();
        link.setUser(user);
        link.setRole(admin);
        userRoleRepository.save(link);

        log.warn("Provisioned SUPER_ADMIN {} from environment. Rotate the password after first login.", user.getEmail());
    }
}
