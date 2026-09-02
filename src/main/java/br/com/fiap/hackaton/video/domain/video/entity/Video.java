package br.com.fiap.hackaton.video.domain.video.entity;

import br.com.fiap.hackaton.video.domain.shared.exception.DomainException;
import br.com.fiap.hackaton.video.domain.video.valueobject.StorageKey;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "videos")
@Getter
@NoArgsConstructor
public class Video {

  private static final int MAX_FILENAME_LENGTH = 255;

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "original_filename", nullable = false, length = MAX_FILENAME_LENGTH)
  private String originalFilename;

  @Column(name = "storage_key", nullable = false, length = 512)
  private String storageKey;

  @Column(name = "zip_key", length = 512)
  private String zipKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VideoStatus status;

  @Column(name = "frame_count")
  private Integer frameCount;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public Video(UUID userId, String originalFilename) {
    validateUserId(userId);
    validateOriginalFilename(originalFilename);

    this.id = UUID.randomUUID();
    this.userId = userId;
    this.originalFilename = originalFilename;
    this.storageKey = StorageKey.forInput(userId, this.id, originalFilename).value();
    this.status = VideoStatus.RECEIVED;
    this.attempts = 0;
    this.createdAt = LocalDateTime.now();
  }

  public void markAsQueued() {
    transitionTo(VideoStatus.QUEUED);
  }

  public void markAsProcessing() {
    transitionTo(VideoStatus.PROCESSING);
  }

  public void markAsCompleted(String zipKey, Integer frameCount) {
    validateZipKey(zipKey);
    validateFrameCount(frameCount);

    transitionTo(VideoStatus.COMPLETED);
    this.zipKey = zipKey;
    this.frameCount = frameCount;
    this.errorMessage = null;
  }

  public void markAsFailed(String errorMessage, int attempts) {
    transitionTo(VideoStatus.FAILED);
    this.errorMessage = truncateErrorMessage(errorMessage);
    this.attempts = Math.max(attempts, this.attempts);
  }

  public boolean isOwnedBy(UUID candidate) {
    return userId.equals(candidate);
  }

  public boolean isDownloadable() {
    return status == VideoStatus.COMPLETED && zipKey != null;
  }

  public boolean hasReachedFinalStatus() {
    return status.isFinal();
  }

  private void transitionTo(VideoStatus target) {
    if (!status.allowsTransitionTo(target)) {
      throw new DomainException(
          "Transicao de status invalida: de %s para %s".formatted(status, target));
    }
    this.status = target;
    this.updatedAt = LocalDateTime.now();
  }

  private void validateUserId(UUID userId) {
    if (userId == null) {
      throw new DomainException("Video precisa estar associado a um usuario");
    }
  }

  private void validateOriginalFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      throw new DomainException("Nome do arquivo nao pode ser vazio");
    }
    if (originalFilename.length() > MAX_FILENAME_LENGTH) {
      throw new DomainException(
          "Nome do arquivo deve ter no maximo %d caracteres".formatted(MAX_FILENAME_LENGTH));
    }
    if (originalFilename.contains("/") || originalFilename.contains("\\")) {
      throw new DomainException("Nome do arquivo nao pode conter separador de diretorio");
    }
    if (originalFilename.contains("..")) {
      throw new DomainException("Nome do arquivo nao pode conter navegacao de diretorio");
    }
  }

  private void validateZipKey(String zipKey) {
    if (zipKey == null || zipKey.isBlank()) {
      throw new DomainException("Video concluido precisa da chave do ZIP");
    }
  }

  private void validateFrameCount(Integer frameCount) {
    if (frameCount == null || frameCount < 0) {
      throw new DomainException("Quantidade de frames nao pode ser nula nem negativa");
    }
  }

  private String truncateErrorMessage(String errorMessage) {
    if (errorMessage == null || errorMessage.isBlank()) {
      return "Falha nao detalhada pelo processamento";
    }
    return errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
  }
}
