package br.com.fiap.hackaton.video.application.video.service;

import br.com.fiap.hackaton.video.application.shared.exception.ConflictException;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageGateway;
import br.com.fiap.hackaton.video.domain.video.valueobject.StorageKey;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoDownloadService {

  private final VideoQueryService videoQueryService;
  private final VideoStorageGateway storageGateway;
  private final Duration urlTimeToLive;

  public VideoDownloadService(
      VideoQueryService videoQueryService,
      VideoStorageGateway storageGateway,
      @Value("${video.download.url-ttl}") Duration urlTimeToLive) {
    this.videoQueryService = videoQueryService;
    this.storageGateway = storageGateway;
    this.urlTimeToLive = urlTimeToLive;
  }

  @Transactional(readOnly = true)
  public String presignedZipUrl(UUID userId, UUID videoId) {
    Video video = videoQueryService.requireOwnedVideo(userId, videoId);
    requireDownloadable(video);

    return storageGateway.presignedDownloadUrl(new StorageKey(video.getZipKey()), urlTimeToLive);
  }

  private void requireDownloadable(Video video) {
    if (!video.isDownloadable()) {
      throw new ConflictException(
          "Video ainda nao esta disponivel para download. Status atual: " + video.getStatus());
    }
  }
}
