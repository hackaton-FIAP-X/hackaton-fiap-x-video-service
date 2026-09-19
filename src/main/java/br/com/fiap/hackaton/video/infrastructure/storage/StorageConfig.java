package br.com.fiap.hackaton.video.infrastructure.storage;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

  @Bean
  public S3Client s3Client(StorageProperties properties) {
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    // sem endpoint = S3 padrao da regiao (AWS); com endpoint = MinIO no ambiente local
    if (StorageProperties.hasText(properties.endpoint())) {
      builder.endpointOverride(URI.create(properties.endpoint()));
    }
    return builder.build();
  }

  @Bean
  public S3Presigner s3Presigner(StorageProperties properties) {
    S3Presigner.Builder builder =
        S3Presigner.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    String publicEndpoint = properties.publicEndpointOrDefault();
    if (StorageProperties.hasText(publicEndpoint)) {
      builder.endpointOverride(URI.create(publicEndpoint));
    }
    return builder.build();
  }

  static AwsCredentialsProvider credentials(StorageProperties properties) {
    if (properties.hasStaticCredentials()) {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
    return DefaultCredentialsProvider.create();
  }
}
