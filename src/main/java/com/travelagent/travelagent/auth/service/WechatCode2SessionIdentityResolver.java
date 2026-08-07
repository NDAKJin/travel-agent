package com.travelagent.travelagent.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelagent.travelagent.auth.exception.AuthException;
import com.travelagent.travelagent.config.AuthProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WechatCode2SessionIdentityResolver implements WxMiniProgramIdentityResolver {

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WechatCode2SessionIdentityResolver(AuthProperties authProperties, ObjectMapper objectMapper) {
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public WxSessionIdentity resolve(String code) {
        AuthProperties.WxMiniProgramProperties wx = authProperties.getWx();
        if (isBlank(wx.getAppId()) || isBlank(wx.getSecret())) {
            throw new AuthException("WeChat mini program appId/secret is not configured");
        }

        String requestUrl = wx.getCode2SessionUrl()
                + "?appid=" + encode(wx.getAppId())
                + "&secret=" + encode(wx.getSecret())
                + "&js_code=" + encode(code)
                + "&grant_type=authorization_code";

        HttpRequest request = HttpRequest.newBuilder(URI.create(requestUrl))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            WxCode2SessionResponse payload = objectMapper.readValue(response.body(), WxCode2SessionResponse.class);

            if (payload.errCode() != null && payload.errCode() != 0) {
                log.warn("WeChat code2Session failed: errCode={}, errMsg={}", payload.errCode(), payload.errMsg());
                throw new AuthException("WeChat login failed: " + payload.errMsg());
            }
            if (isBlank(payload.openId())) {
                throw new AuthException("WeChat login failed: openId missing from code2Session response");
            }
            return new WxSessionIdentity(payload.openId(), payload.unionId());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AuthException("WeChat login failed: unable to resolve openId");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record WxCode2SessionResponse(
            @JsonProperty("openid") String openId,
            @JsonProperty("unionid") String unionId,
            @JsonProperty("session_key") String sessionKey,
            @JsonProperty("errcode") Integer errCode,
            @JsonProperty("errmsg") String errMsg) {
    }
}
