package com.travelagent.travelagent.infrastructure.security.auth;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.domain.auth.exception.AuthException;
import com.travelagent.travelagent.domain.auth.model.DecodedToken;
import com.travelagent.travelagent.domain.auth.model.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
@RequiredArgsConstructor
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = 7;
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String USER_TYPE_CLAIM = "userType";
    private static final String USER_ID_CLAIM = "uid";
    private static final String SUBJECT_CLAIM = "sub";
    private static final String DISPLAY_NAME_CLAIM = "displayName";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String AUTH_ERROR_CODE = "AUTH_ERROR";

    private final JwtTokenService jwtTokenService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "Missing access token");
            return;
        }

        try {
            String token = authorization.substring(BEARER_PREFIX_LENGTH).trim();
            DecodedToken decodedToken = jwtTokenService.decodeAndVerify(token);
            requireAccessToken(decodedToken);

            String userType = decodedToken.stringClaim(USER_TYPE_CLAIM);
            long userId = decodedToken.longClaim(USER_ID_CLAIM);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    new AuthenticatedUser(userId, userType, decodedToken.stringClaim(SUBJECT_CLAIM), decodedToken.stringClaim(DISPLAY_NAME_CLAIM)),
                    token,
                    List.of(new SimpleGrantedAuthority(ROLE_PREFIX + userType.toUpperCase())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (AuthException ex) {
            log.warn("Access token authentication failed: path={}, message={}", request.getRequestURI(), ex.getMessage());
            writeUnauthorized(response, ex.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void requireAccessToken(DecodedToken decodedToken) {
        if (!ACCESS_TOKEN_TYPE.equals(decodedToken.stringClaim(TOKEN_TYPE_CLAIM))) {
            throw new AuthException("Token type must be access");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.toJSONString(java.util.Map.of("code", AUTH_ERROR_CODE, "message", message)));
    }
}
