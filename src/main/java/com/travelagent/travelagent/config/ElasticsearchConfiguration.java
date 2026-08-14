package com.travelagent.travelagent.config;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import java.net.URI;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfiguration {

    @Bean
    Rest5Client elasticsearchRestClient(AgentProperties agentProperties) {
        AgentProperties.ElasticsearchProperties elasticsearch = agentProperties.getElasticsearch();
        URI uri = URI.create(elasticsearch.getScheme() + "://" + elasticsearch.getHost() + ":" + elasticsearch.getPort());
        return Rest5Client.builder(new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort())).build();
    }
}
