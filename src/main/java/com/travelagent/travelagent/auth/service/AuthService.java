package com.travelagent.travelagent.auth.service;

import com.travelagent.travelagent.auth.dto.AdminLoginRequest;
import com.travelagent.travelagent.auth.dto.AuthResponse;
import com.travelagent.travelagent.auth.dto.AuthTokenResponse;
import com.travelagent.travelagent.auth.dto.AuthUserResponse;
import com.travelagent.travelagent.auth.dto.LogoutRequest;
import com.travelagent.travelagent.auth.dto.RefreshTokenRequest;
import com.travelagent.travelagent.auth.dto.WxLoginRequest;
import com.travelagent.travelagent.auth.exception.AuthException;
import com.travelagent.travelagent.auth.mapper.AdminUserMapper;
import com.travelagent.travelagent.auth.mapper.WxUserMapper;
import com.travelagent.travelagent.auth.model.AdminUser;
import com.travelagent.travelagent.auth.model.AuthenticatedAccount;
import com.travelagent.travelagent.auth.model.DecodedToken;
import com.travelagent.travelagent.auth.model.TokenPair;
import com.travelagent.travelagent.auth.model.WxUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final WxUserMapper wxUserMapper;
    private final AdminUserMapper adminUserMapper;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordHasher passwordHasher;
    private final WxMiniProgramIdentityResolver wxMiniProgramIdentityResolver;
    private final Clock clock;

    public AuthResponse loginWx(WxLoginRequest request) {
        WxSessionIdentity identity = wxMiniProgramIdentityResolver.resolve(request.code());
        log.info("Processing WX login: openId={}", identity.openId());
        WxUser user = Optional.ofNullable(wxUserMapper.findByOpenId(identity.openId()))
                .orElseGet(() -> createWxUser(identity.openId(), request));
        if (!user.isEnabled()) {
            log.warn("WX login rejected because user is disabled: userId={}, openId={}", user.getId(), user.getOpenId());
            throw new AuthException("Wx user is disabled");
        }
        log.info("WX login succeeded: userId={}, openId={}", user.getId(), user.getOpenId());
        return issueTokenResponse(new AuthenticatedAccount(user.getId(), "wx", user.getOpenId(), user.getNickname()));
    }

    public AuthResponse loginAdmin(AdminLoginRequest request) {
        log.info("Processing admin login: username={}", request.username());
        AdminUser user = Optional.ofNullable(adminUserMapper.findByUsername(request.username()))
                .orElseThrow(() -> new AuthException("Admin user not found"));
        if (!user.isEnabled()) {
            log.warn("Admin login rejected because user is disabled: userId={}, username={}", user.getId(), user.getUsername());
            throw new AuthException("Admin user is disabled");
        }
        if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
            log.warn("Admin login rejected because password does not match: username={}", request.username());
            throw new AuthException("Invalid admin credentials");
        }
        log.info("Admin login succeeded: userId={}, username={}", user.getId(), user.getUsername());
        return issueTokenResponse(new AuthenticatedAccount(user.getId(), "admin", user.getUsername(), user.getDisplayName()));
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        log.info("Refreshing auth token");
        DecodedToken decodedToken = jwtTokenService.decodeAndVerify(request.refreshToken());
        requireRefreshToken(decodedToken);
        long userId = decodedToken.longClaim("uid");
        String userType = decodedToken.stringClaim("userType");
        String currentTokenId = decodedToken.stringClaim("jti");
        if (!refreshTokenStore.consumeToken(userId, currentTokenId)) {
            log.warn("Refresh token validation failed: userId={}, userType={}, tokenId={}", userId, userType, currentTokenId);
            throw new AuthException("Refresh token is not active");
        }
        AuthenticatedAccount account = loadEnabledAccount(userType, userId);
        TokenPair nextTokenPair = jwtTokenService.issueTokenPair(account.id(), account.userType(), account.subject(), account.displayName());
        Duration ttl = Duration.between(clock.instant(), nextTokenPair.refreshTokenExpiresAt());
        refreshTokenStore.storeToken(userId, nextTokenPair.refreshTokenId(), ttl);
        log.info("Refresh token rotation succeeded: userId={}, userType={}", userId, userType);
        return toAuthResponse(account, nextTokenPair);
    }

    public void logout(LogoutRequest request) {
        log.info("Log out.");
        DecodedToken decodedToken = jwtTokenService.decodeAndVerify(request.refreshToken());
        requireRefreshToken(decodedToken);
        long userId = decodedToken.longClaim("uid");
        String tokenId = decodedToken.stringClaim("jti");
        log.info("Revoking refresh token: userId={}, tokenId={}", userId, tokenId);
        refreshTokenStore.revokeToken(userId, tokenId);
    }

    private AuthResponse issueTokenResponse(AuthenticatedAccount account) {
        TokenPair tokenPair = jwtTokenService.issueTokenPair(account.id(), account.userType(), account.subject(), account.displayName());
        Duration ttl = Duration.between(clock.instant(), tokenPair.refreshTokenExpiresAt());
        refreshTokenStore.storeToken(account.id(), tokenPair.refreshTokenId(), ttl);
        log.debug("Issued token pair: userId={}, userType={}, refreshTokenTtlSeconds={}",
                account.id(),
                account.userType(),
                ttl.getSeconds());
        return toAuthResponse(account, tokenPair);
    }

    private AuthenticatedAccount loadAccount(String userType, long userId) {
        return switch (userType) {
            case "wx" -> Optional.ofNullable(wxUserMapper.findById(userId))
                    .map(this::toWxAccount)
                    .orElseThrow(() -> new AuthException("Wx user not found"));
            case "admin" -> Optional.ofNullable(adminUserMapper.findById(userId))
                    .map(this::toAdminAccount)
                    .orElseThrow(() -> new AuthException("Admin user not found"));
            default -> throw new AuthException("Unsupported account type");
        };
    }

    private AuthenticatedAccount loadEnabledAccount(String userType, long userId) {
        AuthenticatedAccount account = loadAccount(userType, userId);
        boolean enabled = switch (userType) {
            case "wx" -> wxUserMapper.findById(userId).isEnabled();
            case "admin" -> adminUserMapper.findById(userId).isEnabled();
            default -> false;
        };
        if (!enabled) {
            throw new AuthException("Account is disabled");
        }
        return account;
    }

    private WxUser createWxUser(String openId, WxLoginRequest request) {
        Instant now = clock.instant();
        WxUser user = new WxUser();
        user.setOpenId(openId);
        user.setNickname(request.nickname() == null || request.nickname().isBlank() ? "wx-user" : request.nickname());
        user.setAvatarUrl(request.avatarUrl());
        user.setEnabled(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        wxUserMapper.insert(user);
        log.info("Created WX user: userId={}, openId={}", user.getId(), user.getOpenId());
        return user;
    }

    private void requireRefreshToken(DecodedToken decodedToken) {
        String tokenType = decodedToken.stringClaim("token_type");
        if (!Objects.equals("refresh", tokenType)) {
            log.warn("Rejected token because tokenType is not refresh: tokenType={}", tokenType);
            throw new AuthException("Token type must be refresh");
        }
    }

    private AuthenticatedAccount toWxAccount(WxUser user) {
        return new AuthenticatedAccount(user.getId(), "wx", user.getOpenId(), user.getNickname());
    }

    private AuthenticatedAccount toAdminAccount(AdminUser user) {
        return new AuthenticatedAccount(user.getId(), "admin", user.getUsername(), user.getDisplayName());
    }

    private AuthResponse toAuthResponse(AuthenticatedAccount account, TokenPair tokenPair) {
        return new AuthResponse(
                new AuthUserResponse(account.id(), account.userType(), account.subject(), account.displayName()),
                new AuthTokenResponse(
                        tokenPair.accessToken(),
                        tokenPair.accessTokenExpiresAt(),
                        tokenPair.refreshToken(),
                        tokenPair.refreshTokenExpiresAt()));
    }
}
