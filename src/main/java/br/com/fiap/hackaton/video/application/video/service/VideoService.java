package br.com.fiap.hackaton.video.application.video.service;

import br.com.fiap.hackaton.video.application.outbox.service.OutboxDispatcher;
import br.com.fiap.hackaton.video.application.shared.exception.BusinessException;
import br.com.fiap.hackaton.video.application.video.dto.UploadVideoResponse;
import br.com.fiap.hackaton.video.application.video.dto.VideoUploadCommand;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageGateway;
import br.com.fiap.hackaton.video.domain.video.valueobject.StorageKey;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoFormat;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

@Slf4j
@Service
public class VideoService {

  private final VideoStorageGateway storageGateway;
  private final VideoRegistrationService registrationService;
  private final OutboxDispatcher outboxDispatcher;
  private final DataSize maxUploadSize;

  public VideoService(
      VideoStorageGateway storageGateway,
      VideoRegistrationService registrationService,
      OutboxDispatcher outboxDispatcher,
      @Value("${video.upload.max-size}") DataSize maxUploadSize) {
    this.storageGateway = storageGateway;
    this.registrationService = registrationService;
    this.outboxDispatcher = outboxDispatcher;
    this.maxUploadSize = maxUploadSize;
  }

  public UploadVideoResponse upload(UUID userId, VideoUploadCommand command) {
    VideoFormat format = VideoFormat.fromFilename(command.originalFilename());
    validateSize(command.sizeInBytes());

    Video video = new Video(userId, command.originalFilename());
    storageGateway.store(
        new StorageKey(video.getStorageKey()),
        command.content(),
        command.sizeInBytes(),
        format.contentType());

    Video registered =
        registrationService.registerReceived(video, VideoRegistrationService.newTraceId());
    dispatchWithoutBlockingTheResponse();

    return UploadVideoResponse.fromEntity(registered);
  }

  private void dispatchWithoutBlockingTheResponse() {
    try {
      outboxDispatcher.dispatchPending();
    } catch (RuntimeException e) {
      log.warn("Publicacao imediata falhou; o scheduler do outbox assume: {}", e.getMessage());
    }
  }

  private void validateSize(long sizeInBytes) {
    if (sizeInBytes <= 0) {
      throw new BusinessException("Arquivo de video esta vazio");
    }
    if (sizeInBytes > maxUploadSize.toBytes()) {
      throw new BusinessException(
          "Arquivo de video excede o limite de %d MB".formatted(maxUploadSize.toMegabytes()));
    }
  }
}
