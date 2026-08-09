package com.travelagent.travelagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "travel-agent.amap")
public class AmapProperties {

    private String webServiceKey;
    private String city = "全国";
    private int pageSize = 10;
}
