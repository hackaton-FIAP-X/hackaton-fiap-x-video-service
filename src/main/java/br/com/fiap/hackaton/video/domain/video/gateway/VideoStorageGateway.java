package br.com.fiap.hackaton.video.domain.video.gateway;

import br.com.fiap.hackaton.video.domain.video.valueobject.StorageKey;
import java.io.InputStream;
import java.time.Duration;

public interface VideoStorageGateway {

  void store(StorageKey key, InputStream content, long sizeInBytes, String contentType);

  String presignedDownloadUrl(StorageKey key, Duration expiration);
}
