package com.travelagent.travelagent.infrastructure.config;

import com.travelagent.travelagent.domain.auth.model.AdminUser;
import com.travelagent.travelagent.infrastructure.persistence.auth.AdminUserMapper;
import com.travelagent.travelagent.infrastructure.config.AuthProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Configuration
public class AuthBootstrapConfiguration {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecretKey(AuthProperties authProperties) {
        return new SecretKeySpec(authProperties.getJwtSecret().getBytes(), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        return jwtDecoder;
    }

    @Bean
    public CommandLineRunner bootstrapAdmin(AuthProperties authProperties,
                                       AdminUserMapper adminUserMapper,
                                       PasswordEncoder passwordEncoder,
                                       Clock clock) {
        return args -> {
            AuthProperties.BootstrapAdminProperties bootstrapAdmin = authProperties.getBootstrapAdmin();
            if (!StringUtils.hasText(bootstrapAdmin.getUsername()) || !StringUtils.hasText(bootstrapAdmin.getPassword())
                    || adminUserMapper.findByUsername(bootstrapAdmin.getUsername()) != null) {
                return;
            }
            Instant now = clock.instant();
            AdminUser adminUser = new AdminUser();
            adminUser.setUsername(bootstrapAdmin.getUsername());
            adminUser.setPasswordHash(passwordEncoder.encode(bootstrapAdmin.getPassword()));
            adminUser.setDisplayName(bootstrapAdmin.getDisplayName());
            adminUser.setEnabled(true);
            adminUser.setCreatedAt(now);
            adminUser.setUpdatedAt(now);
            adminUserMapper.insert(adminUser);
        };
    }
}
