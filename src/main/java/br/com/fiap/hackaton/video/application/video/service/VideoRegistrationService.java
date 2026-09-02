package br.com.fiap.hackaton.video.application.video.service;

import br.com.fiap.hackaton.video.application.shared.exception.BusinessException;
import br.com.fiap.hackaton.video.application.video.dto.VideoUploadedEvent;
import br.com.fiap.hackaton.video.application.video.gateway.VideoListingCache;
import br.com.fiap.hackaton.video.domain.outbox.entity.OutboxEvent;
import br.com.fiap.hackaton.video.domain.outbox.repository.OutboxRepository;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.repository.VideoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoRegistrationService {

  private final VideoRepository videoRepository;
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;
  private final VideoListingCache listingCache;

  @Transactional
  public Video registerReceived(Video video, String traceId) {
    Video saved = videoRepository.save(video);
    outboxRepository.save(buildUploadedEvent(saved, traceId));
    listingCache.invalidate(saved.getUserId());
    return saved;
  }

  private OutboxEvent buildUploadedEvent(Video video, String traceId) {
    VideoUploadedEvent event = VideoUploadedEvent.fromEntity(video, traceId);
    return new OutboxEvent(video.getId(), VideoUploadedEvent.EVENT_TYPE, serialize(event));
  }

  private String serialize(VideoUploadedEvent event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new BusinessException("Nao foi possivel serializar o evento video.uploaded");
    }
  }

  public static String newTraceId() {
    return UUID.randomUUID().toString();
  }
}
