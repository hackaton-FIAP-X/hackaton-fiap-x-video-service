package br.com.fiap.hackaton.video.domain.video.gateway;

import br.com.fiap.hackaton.video.domain.video.valueobject.StorageKey;
import java.io.InputStream;

public interface VideoStorageGateway {

  void store(StorageKey key, InputStream content, long sizeInBytes, String contentType);
}
