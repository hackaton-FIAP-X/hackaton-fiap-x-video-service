package br.com.fiap.hackaton.video.application.outbox.service;

import br.com.fiap.hackaton.video.application.video.gateway.VideoListingCache;
import br.com.fiap.hackaton.video.domain.outbox.entity.OutboxEvent;
import br.com.fiap.hackaton.video.domain.outbox.repository.OutboxRepository;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.gateway.EventPublishException;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoEventPublisher;
import br.com.fiap.hackaton.video.domain.video.repository.VideoRepository;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OutboxDispatcher {

  private final OutboxRepository outboxRepository;
  private final VideoRepository videoRepository;
  private final VideoEventPublisher eventPublisher;
  private final VideoListingCache listingCache;
  private final int batchSize;

  public OutboxDispatcher(
      OutboxRepository outboxRepository,
      VideoRepository videoRepository,
      VideoEventPublisher eventPublisher,
      VideoListingCache listingCache,
      @Value("${video.outbox.batch-size:50}") int batchSize) {
    this.outboxRepository = outboxRepository;
    this.videoRepository = videoRepository;
    this.eventPublisher = eventPublisher;
    this.listingCache = listingCache;
    this.batchSize = batchSize;
  }

  @Transactional
  public int dispatchPending() {
    List<OutboxEvent> pending = outboxRepository.lockPendingBatch(batchSize);
    if (pending.isEmpty()) {
      return 0;
    }

    int published = 0;
    for (OutboxEvent event : pending) {
      if (publish(event)) {
        published++;
      }
    }

    log.info("Outbox despachado: {} de {} eventos confirmados", published, pending.size());
    return published;
  }

  private boolean publish(OutboxEvent event) {
    try {
      eventPublisher.publishConfirmed(event);
      event.markAsPublished();
      outboxRepository.save(event);
      moveVideoToQueued(event.getAggregateId());
      return true;
    } catch (EventPublishException e) {
      log.warn("Evento {} continua pendente: {}", event.getId(), e.getMessage());
      event.registerFailure(e.getMessage());
      outboxRepository.save(event);
      return false;
    }
  }

  private void moveVideoToQueued(UUID videoId) {
    videoRepository
        .findById(videoId)
        .filter(video -> video.getStatus() == VideoStatus.RECEIVED)
        .ifPresent(this::queue);
  }

  private void queue(Video video) {
    video.markAsQueued();
    videoRepository.save(video);
    listingCache.invalidate(video.getUserId());
  }
}
