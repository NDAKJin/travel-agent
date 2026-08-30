package com.travelagent.travelagent.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.travelagent.travelagent.infrastructure.config.AuthProperties;
import com.travelagent.travelagent.infrastructure.security.auth.JwtTokenService;
import com.travelagent.travelagent.domain.auth.dto.AdminLoginRequest;
import com.travelagent.travelagent.domain.auth.dto.AuthResponse;
import com.travelagent.travelagent.domain.auth.dto.RefreshTokenRequest;
import com.travelagent.travelagent.domain.auth.dto.WxLoginRequest;
import com.travelagent.travelagent.domain.auth.exception.AuthException;
import com.travelagent.travelagent.infrastructure.persistence.auth.AdminUserMapper;
import com.travelagent.travelagent.infrastructure.persistence.auth.WxUserMapper;
import com.travelagent.travelagent.domain.auth.model.AdminUser;
import com.travelagent.travelagent.domain.auth.model.TokenPair;
import com.travelagent.travelagent.domain.auth.model.WxUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Mock
    private WxUserMapper wxUserMapper;

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private WxMiniProgramIdentityResolver wxMiniProgramIdentityResolver;

    @Mock
    private EmailVerificationService emailVerificationService;

    private AuthService authService;
    private JwtTokenService jwtTokenService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("test-secret-key-for-hs256-signing-123456");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(1));
        SecretKeySpec secretKey = new SecretKeySpec(properties.getJwtSecret().getBytes(), "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        jwtTokenService = new JwtTokenService(properties, clock, jwtEncoder, jwtDecoder);
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(
                wxUserMapper,
                adminUserMapper,
                jwtTokenService,
                refreshTokenStore,
                passwordEncoder,
                wxMiniProgramIdentityResolver,
                clock,
                emailVerificationService);
    }

    @Test
    void loginWxCreatesUserAndStoresRefreshToken() {
        when(wxMiniProgramIdentityResolver.resolve("test-code")).thenReturn(new WxSessionIdentity("wx-open-id-demo", null));
        when(wxUserMapper.findByOpenId("wx-open-id-demo")).thenReturn(null);
        when(wxUserMapper.insert(any())).thenAnswer(invocation -> {
            WxUser user = invocation.getArgument(0);
            user.setId(10L);
            return 1;
        });

        AuthResponse response = authService.loginWx(new WxLoginRequest("test-code", null, null));

        assertThat(response.user().id()).isEqualTo(10L);
        assertThat(response.token().accessToken()).isNotBlank();
        ArgumentCaptor<WxUser> userCaptor = ArgumentCaptor.forClass(WxUser.class);
        verify(wxUserMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(userCaptor.getValue().getUpdatedAt()).isEqualTo(FIXED_NOW);
        verify(refreshTokenStore).storeToken(anyLong(), any(), any());
    }

    @Test
    void refreshRevokesOldTokenAndStoresNewToken() {
        WxUser user = new WxUser();
        user.setId(10L);
        user.setOpenId("wx-open-id-demo");
        user.setNickname("wx-user");
        when(wxUserMapper.findById(10L)).thenReturn(user);

        TokenPair firstTokenPair = jwtTokenService.issueTokenPair(10L, "wx", "wx-open-id-demo", "wx-user");
        when(refreshTokenStore.consumeToken(10L, firstTokenPair.refreshTokenId())).thenReturn(true);

        AuthResponse refreshed = authService.refresh(new RefreshTokenRequest(firstTokenPair.refreshToken()));

        assertThat(refreshed.token().refreshToken()).isNotEqualTo(firstTokenPair.refreshToken());
        ArgumentCaptor<String> tokenIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(refreshTokenStore).storeToken(eq(10L), tokenIdCaptor.capture(), ttlCaptor.capture());
        assertThat(tokenIdCaptor.getValue()).isNotBlank();
        assertThat(ttlCaptor.getValue()).isPositive();
    }

    @Test
    void refreshRejectsInactiveTokenWhenWhitelistValidationFails() {
        TokenPair firstTokenPair = jwtTokenService.issueTokenPair(10L, "wx", "wx-open-id-demo", "wx-user");
        when(refreshTokenStore.consumeToken(10L, firstTokenPair.refreshTokenId())).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(firstTokenPair.refreshToken())))
                .isInstanceOf(AuthException.class)
                .hasMessage("Refresh token is not active");
    }

    @Test
    void adminLoginAcceptsSeededPassword() {
        AdminUser adminUser = new AdminUser();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setDisplayName("ops-admin");
        adminUser.setPasswordHash(passwordEncoder.encode("admin123"));
        adminUser.setEnabled(true);
        when(adminUserMapper.findByUsername("admin")).thenReturn(adminUser);

        AuthResponse response = authService.loginAdmin(new AdminLoginRequest("admin", "admin123"));

        assertThat(response.user().userType()).isEqualTo("admin");
        assertThat(response.token().refreshToken()).isNotBlank();
    }

    @Test
    void adminLoginRejectsInvalidPassword() {
        AdminUser adminUser = new AdminUser();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setDisplayName("ops-admin");
        adminUser.setPasswordHash(passwordEncoder.encode("admin123"));
        adminUser.setEnabled(true);
        when(adminUserMapper.findByUsername("admin")).thenReturn(adminUser);

        assertThatThrownBy(() -> authService.loginAdmin(new AdminLoginRequest("admin", "wrong-password")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid admin credentials");
    }
}
