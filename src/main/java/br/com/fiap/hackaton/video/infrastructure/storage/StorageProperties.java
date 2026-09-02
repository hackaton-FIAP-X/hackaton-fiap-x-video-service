package br.com.fiap.hackaton.video.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
    String endpoint, String accessKey, String secretKey, String bucket, String region) {}
