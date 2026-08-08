package com.travelagent.travelagent.config;

import java.net.URI;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "travel-agent.agent.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagBootstrapConfiguration {

    @Bean
    Rest5Client scenicRestClient(AgentProperties agentProperties) {
        AgentProperties.ElasticsearchProperties elasticsearch = agentProperties.getRag().getElasticsearch();
        URI uri = URI.create(elasticsearch.getScheme() + "://" + elasticsearch.getHost() + ":" + elasticsearch.getPort());
        log.info("Creating Elasticsearch client: scheme={}, host={}, port={}, index={}",
                uri.getScheme(), uri.getHost(), uri.getPort(), elasticsearch.getIndexName());
        return Rest5Client.builder(new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort())).build();
    }

    @Bean
    @Lazy
    VectorStore scenicVectorStore(Rest5Client scenicRestClient,
                                  EmbeddingModel embeddingModel,
                                  AgentProperties agentProperties) {
        AgentProperties.RagProperties ragProperties = agentProperties.getRag();
        log.info("Initializing scenic vector store: index={}, embeddingDimensions={}, topK={}, similarityThreshold={}",
                ragProperties.getElasticsearch().getIndexName(), ragProperties.getEmbeddingDimensions(),
                ragProperties.getTopK(), ragProperties.getSimilarityThreshold());
        resetScenicIndexIfNeeded(scenicRestClient, ragProperties);
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName(ragProperties.getElasticsearch().getIndexName());
        options.setDimensions(ragProperties.getEmbeddingDimensions());
        return ElasticsearchVectorStore.builder(scenicRestClient, embeddingModel)
                .initializeSchema(true)
                .options(options)
                .build();
    }

    private void resetScenicIndexIfNeeded(Rest5Client scenicRestClient, AgentProperties.RagProperties ragProperties) {
        String indexName = ragProperties.getElasticsearch().getIndexName();
        Request request = new Request("DELETE", "/" + indexName);
        request.addParameter(Rest5Client.IGNORE_RESPONSE_CODES_PARAM, "404");
        try {
            Response response = scenicRestClient.performRequest(request);
            if (response.getStatusCode() != 404) {
                return;
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to reset scenic index: " + indexName, exception);
        }
    }
}
