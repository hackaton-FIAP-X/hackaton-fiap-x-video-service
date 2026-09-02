package br.com.fiap.hackaton.video.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
    String endpoint,
    String publicEndpoint,
    String accessKey,
    String secretKey,
    String bucket,
    String region) {

  public String publicEndpointOrDefault() {
    return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
  }
}
