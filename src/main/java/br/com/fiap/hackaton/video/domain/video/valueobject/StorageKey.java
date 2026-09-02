package br.com.fiap.hackaton.video.domain.video.valueobject;

import br.com.fiap.hackaton.video.domain.shared.exception.DomainException;
import java.util.UUID;

public record StorageKey(String value) {

  private static final String INPUT_PREFIX = "fiapx/inputs";
  private static final String OUTPUT_PREFIX = "fiapx/outputs";

  public StorageKey {
    if (value == null || value.isBlank()) {
      throw new DomainException("Chave de storage nao pode ser vazia");
    }
    if (value.length() > 512) {
      throw new DomainException("Chave de storage deve ter no maximo 512 caracteres");
    }
  }

  public static StorageKey forInput(UUID userId, UUID videoId, String originalFilename) {
    return new StorageKey("%s/%s/%s/%s".formatted(INPUT_PREFIX, userId, videoId, originalFilename));
  }

  public static StorageKey forOutput(UUID userId, UUID videoId) {
    return new StorageKey("%s/%s/%s.zip".formatted(OUTPUT_PREFIX, userId, videoId));
  }

  @Override
  public String toString() {
    return value;
  }
}
