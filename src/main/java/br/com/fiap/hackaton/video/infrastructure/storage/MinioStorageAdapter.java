package br.com.fiap.hackaton.video.infrastructure.storage;

import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageException;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageGateway;
import br.com.fiap.hackaton.video.domain.video.valueobject.StorageKey;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioStorageAdapter implements VideoStorageGateway {

  private final S3Client s3Client;
  private final StorageProperties properties;

  @Override
  public void store(StorageKey key, InputStream content, long sizeInBytes, String contentType) {
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key.value())
            .contentType(contentType)
            .contentLength(sizeInBytes)
            .build();

    try {
      s3Client.putObject(request, RequestBody.fromInputStream(content, sizeInBytes));
      log.info("Video gravado no storage: key={} bytes={}", key.value(), sizeInBytes);
    } catch (SdkException e) {
      throw new VideoStorageException("Falha ao gravar o video no storage: " + key.value(), e);
    }
  }
}
