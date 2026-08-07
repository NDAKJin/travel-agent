package com.travelagent.travelagent.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travel-agent.auth")
@Getter
@Setter
public class AuthProperties {

    private String issuer = "travel-agent";
    private String jwtSecret;
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(1);
    private WxMiniProgramProperties wx = new WxMiniProgramProperties();

    @Getter
    @Setter
    public static class WxMiniProgramProperties {

        private String appId;
        private String secret;
        private String code2SessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
    }
}
