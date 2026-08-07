package com.travelagent.travelagent.config;

import com.travelagent.travelagent.auth.model.AdminUser;
import com.travelagent.travelagent.auth.model.WxUser;
import com.travelagent.travelagent.auth.mapper.AdminUserMapper;
import com.travelagent.travelagent.auth.mapper.WxUserMapper;
import com.travelagent.travelagent.auth.service.PasswordHasher;
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

@Configuration
public class AuthBootstrapConfiguration {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
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
    public CommandLineRunner seedUsers(WxUserMapper wxUserMapper,
                                       AdminUserMapper adminUserMapper,
                                       PasswordHasher passwordHasher,
                                       Clock clock) {
        return args -> {
            Instant now = clock.instant();
            if (wxUserMapper.count() == 0) {
                WxUser user = new WxUser();
                user.setOpenId("wx-open-id-demo");
                user.setNickname("wx-user");
                user.setEnabled(true);
                user.setCreatedAt(now);
                user.setUpdatedAt(now);
                wxUserMapper.insert(user);
            }
            if (adminUserMapper.count() == 0) {
                AdminUser adminUser = new AdminUser();
                adminUser.setUsername("admin");
                adminUser.setPasswordHash(passwordHasher.hash("admin123"));
                adminUser.setDisplayName("ops-admin");
                adminUser.setEnabled(true);
                adminUser.setCreatedAt(now);
                adminUser.setUpdatedAt(now);
                adminUserMapper.insert(adminUser);
            }
        };
    }
}
