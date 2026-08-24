package com.travelagent.travelagent.application.auth.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.travelagent.travelagent.application.auth.exception.AuthException;
import com.travelagent.travelagent.application.auth.config.AuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class WechatCode2SessionIdentityResolver implements WxMiniProgramIdentityResolver {

    private final AuthProperties authProperties;
    private final RestClient restClient;

    public WechatCode2SessionIdentityResolver(AuthProperties authProperties,
                                              @Qualifier("externalRestClient") RestClient restClient) {
        this.authProperties = authProperties;
        this.restClient = restClient;
    }

    @Override
    public WxSessionIdentity resolve(String code) {
        AuthProperties.WxMiniProgramProperties wx = authProperties.getWx();
        if (isBlank(wx.getAppId()) || isBlank(wx.getSecret())) {
            throw new AuthException("WeChat mini program appId/secret is not configured");
        }

        try {
            JSONObject payload = JSON.parseObject(restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(wx.getCode2SessionUrl())
                            .queryParam("appid", wx.getAppId())
                            .queryParam("secret", wx.getSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build().encode().toUri())
                    .retrieve()
                    .body(String.class));

            if (payload.containsKey("errcode") && payload.getIntValue("errcode") != 0) {
                log.warn("WeChat code2Session failed: errCode={}, errMsg={}", payload.getIntValue("errcode"), payload.getString("errmsg"));
                throw new AuthException("WeChat login failed: " + payload.getString("errmsg"));
            }
            if (isBlank(payload.getString("openid"))) {
                throw new AuthException("WeChat login failed: openId missing from code2Session response");
            }
            return new WxSessionIdentity(payload.getString("openid"), payload.getString("unionid"));
        } catch (AuthException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("WeChat code2Session request failed: exceptionType={}, causeType={}",
                    ex.getClass().getSimpleName(),
                    ex.getCause() == null ? "none" : ex.getCause().getClass().getSimpleName());
            throw new AuthException("WeChat login failed: unable to resolve openId");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
