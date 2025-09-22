package com.company.app.search.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 설정
 */
@Configuration
@Slf4j
public class ElasticsearchConfig {
    
    @Value("${elasticsearch.host:localhost}")
    private String host;
    
    @Value("${elasticsearch.port:9200}")
    private int port;
    
    @Value("${elasticsearch.scheme:http}")
    private String scheme;
    
    @Bean
    public RestHighLevelClient elasticsearchClient() {
        try {
            log.info("Elasticsearch 클라이언트 초기화: {}://{}:{}", scheme, host, port);
            
            RestHighLevelClient client = new RestHighLevelClient(
                RestClient.builder(new HttpHost(host, port, scheme))
                    .setRequestConfigCallback(requestConfigBuilder ->
                        requestConfigBuilder
                            .setConnectTimeout(5000)
                            .setSocketTimeout(30000))
                    .setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder
                            .setMaxConnTotal(100)
                            .setMaxConnPerRoute(100))
            );
            
            log.info("Elasticsearch 클라이언트 초기화 완료");
            return client;
            
        } catch (Exception e) {
            log.error("Elasticsearch 클라이언트 초기화 실패", e);
            throw new RuntimeException("Elasticsearch 클라이언트 초기화 실패", e);
        }
    }
}

