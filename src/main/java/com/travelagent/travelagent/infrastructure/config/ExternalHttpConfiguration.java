package com.travelagent.travelagent.infrastructure.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ExternalHttpConfiguration {

    @Bean(name = "externalRestClient")
    RestClient externalRestClient(
            @Value("${travel-agent.external-http.connect-timeout:PT3S}") Duration connectTimeout,
            @Value("${travel-agent.external-http.read-timeout:PT15S}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
