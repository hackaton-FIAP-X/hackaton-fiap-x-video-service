package br.com.fiap.hackaton.video.infrastructure.storage;

import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageException;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageGateway;
import br.com.fiap.hackaton.video.domain.video.valueobject.StorageKey;
import java.io.InputStream;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioStorageAdapter implements VideoStorageGateway {

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
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

  @Override
  public String presignedDownloadUrl(StorageKey key, Duration expiration) {
    GetObjectRequest download =
        GetObjectRequest.builder().bucket(properties.bucket()).key(key.value()).build();

    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(expiration)
            .getObjectRequest(download)
            .build();

    try {
      return s3Presigner.presignGetObject(presignRequest).url().toExternalForm();
    } catch (SdkException e) {
      throw new VideoStorageException("Falha ao assinar o download de: " + key.value(), e);
    }
  }
}
