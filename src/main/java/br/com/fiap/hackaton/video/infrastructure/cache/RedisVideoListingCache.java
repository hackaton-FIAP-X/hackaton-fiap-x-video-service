package br.com.fiap.hackaton.video.infrastructure.cache;

import br.com.fiap.hackaton.video.application.video.dto.VideoPageResponse;
import br.com.fiap.hackaton.video.application.video.gateway.VideoListingCache;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "video.cache.enabled", havingValue = "true", matchIfMissing = true)
public class RedisVideoListingCache implements VideoListingCache {

  private static final String VERSION_KEY = "videos:version:%s";
  private static final String PAGE_KEY = "videos:page:%s:v%s:%s:%d:%d";
  private static final String NO_STATUS = "all";

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final Duration timeToLive;

  public RedisVideoListingCache(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      @Value("${video.cache.ttl}") Duration timeToLive) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.timeToLive = timeToLive;
  }

  @Override
  public Optional<VideoPageResponse> find(UUID userId, VideoStatus status, int page, int size) {
    try {
      String cached = redis.opsForValue().get(pageKey(userId, status, page, size));
      return cached == null
          ? Optional.empty()
          : Optional.of(objectMapper.readValue(cached, VideoPageResponse.class));
    } catch (Exception e) {
      log.warn("Leitura do cache de listagem ignorada: {}", e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public void store(
      UUID userId, VideoStatus status, int page, int size, VideoPageResponse response) {
    try {
      redis
          .opsForValue()
          .set(
              pageKey(userId, status, page, size),
              objectMapper.writeValueAsString(response),
              timeToLive);
    } catch (Exception e) {
      log.warn("Escrita no cache de listagem ignorada: {}", e.getMessage());
    }
  }

  @Override
  public void invalidate(UUID userId) {
    try {
      redis.opsForValue().increment(VERSION_KEY.formatted(userId));
    } catch (Exception e) {
      log.warn("Invalidacao do cache de listagem ignorada: {}", e.getMessage());
    }
  }

  private String pageKey(UUID userId, VideoStatus status, int page, int size) {
    String statusPart = status == null ? NO_STATUS : status.name();
    return PAGE_KEY.formatted(userId, currentVersion(userId), statusPart, page, size);
  }

  private String currentVersion(UUID userId) {
    String version = redis.opsForValue().get(VERSION_KEY.formatted(userId));
    return version == null ? "0" : version;
  }
}
