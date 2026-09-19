package br.com.fiap.hackaton.video.application.video.service;

import br.com.fiap.hackaton.video.application.video.dto.VideoFailedEvent;
import br.com.fiap.hackaton.video.application.video.dto.VideoFailureNotification;
import br.com.fiap.hackaton.video.application.video.dto.VideoProcessedEvent;
import br.com.fiap.hackaton.video.application.video.gateway.VideoListingCache;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.repository.VideoRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoStatusUpdateService {

  private final VideoRepository videoRepository;
  private final VideoListingCache listingCache;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void applyProcessed(VideoProcessedEvent event) {
    lockPendingVideo(event.videoId(), VideoProcessedEvent.EVENT_TYPE)
        .ifPresent(
            video -> {
              video.markAsCompleted(event.zipKey(), event.frameCount());
              videoRepository.save(video);
              listingCache.invalidate(video.getUserId());
              log.info(
                  "Video {} concluido com {} frames [trace={}]",
                  video.getId(),
                  event.frameCount(),
                  event.traceId());
            });
  }

  @Transactional
  public void applyFailed(VideoFailedEvent event) {
    lockPendingVideo(event.videoId(), VideoFailedEvent.EVENT_TYPE)
        .ifPresent(
            video -> {
              video.markAsFailed(describe(event), attemptsOf(event));
              videoRepository.save(video);
              listingCache.invalidate(video.getUserId());
              notifyOwner(video, event);
              log.warn(
                  "Video {} falhou: {} [trace={}]",
                  video.getId(),
                  video.getErrorMessage(),
                  event.traceId());
            });
  }

  /**
   * PLT-8: publica o aviso como evento de aplicacao. Quem envia o e-mail so roda depois do commit
   * (FailureNotificationListener), entao uma falha de SMTP nunca desfaz o status FAILED nem faz a
   * mensagem voltar para a fila. So chega aqui na primeira vez que o video falha: reentregas de um
   * video ja FAILED sao descartadas em lockPendingVideo, e o usuario nao recebe aviso repetido.
   */
  private void notifyOwner(Video video, VideoFailedEvent event) {
    if (!video.canBeNotified()) {
      log.info("Video {} falhou sem e-mail do dono; aviso nao enviado", video.getId());
      return;
    }
    eventPublisher.publishEvent(
        new VideoFailureNotification(
            video.getId(),
            video.getOwnerEmail(),
            video.getOriginalFilename(),
            video.getErrorMessage(),
            event.traceId()));
  }

  private Optional<Video> lockPendingVideo(UUID videoId, String eventType) {
    Optional<Video> video = videoRepository.findByIdForUpdate(videoId);

    if (video.isEmpty()) {
      log.warn("Evento {} descartado: video {} nao existe", eventType, videoId);
      return Optional.empty();
    }
    if (video.get().hasReachedFinalStatus()) {
      log.info(
          "Evento {} ignorado: video {} ja esta em {}",
          eventType,
          videoId,
          video.get().getStatus());
      return Optional.empty();
    }
    return video;
  }

  private String describe(VideoFailedEvent event) {
    if (event.errorCode() == null || event.errorCode().isBlank()) {
      return event.errorMessage();
    }
    return "[%s] %s".formatted(event.errorCode(), event.errorMessage());
  }

  private int attemptsOf(VideoFailedEvent event) {
    return event.attempts() == null ? 0 : event.attempts();
  }
}
