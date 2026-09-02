package br.com.fiap.hackaton.video.application.video.service;

import br.com.fiap.hackaton.video.application.shared.exception.BusinessException;
import br.com.fiap.hackaton.video.application.video.dto.UploadVideoResponse;
import br.com.fiap.hackaton.video.application.video.dto.VideoUploadCommand;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageGateway;
import br.com.fiap.hackaton.video.domain.video.repository.VideoRepository;
import br.com.fiap.hackaton.video.domain.video.valueobject.StorageKey;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

@Service
public class VideoService {

  private final VideoRepository videoRepository;
  private final VideoStorageGateway storageGateway;
  private final DataSize maxUploadSize;

  public VideoService(
      VideoRepository videoRepository,
      VideoStorageGateway storageGateway,
      @Value("${video.upload.max-size}") DataSize maxUploadSize) {
    this.videoRepository = videoRepository;
    this.storageGateway = storageGateway;
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

    return UploadVideoResponse.fromEntity(videoRepository.save(video));
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
