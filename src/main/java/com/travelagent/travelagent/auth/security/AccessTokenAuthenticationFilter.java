package com.travelagent.travelagent.auth.security;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.auth.exception.AuthException;
import com.travelagent.travelagent.auth.model.DecodedToken;
import com.travelagent.travelagent.auth.service.JwtTokenService;
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
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(response, "Missing access token");
            return;
        }

        try {
            String token = authorization.substring(7).trim();
            DecodedToken decodedToken = jwtTokenService.decodeAndVerify(token);
            requireAccessToken(decodedToken);

            String userType = decodedToken.stringClaim("userType");
            long userId = decodedToken.longClaim("uid");
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    new AuthenticatedUser(userId, userType, decodedToken.stringClaim("sub"), decodedToken.stringClaim("displayName")),
                    token,
                    List.of(new SimpleGrantedAuthority("ROLE_" + userType.toUpperCase())));
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
        if (!"access".equals(decodedToken.stringClaim("token_type"))) {
            throw new AuthException("Token type must be access");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.toJSONString(java.util.Map.of("code", "AUTH_ERROR", "message", message)));
    }
}
