package br.com.fiap.hackaton.video.infrastructure.cache;

import br.com.fiap.hackaton.video.application.video.dto.VideoPageResponse;
import br.com.fiap.hackaton.video.application.video.gateway.VideoListingCache;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NoOpVideoListingCache {

  @Bean
  @ConditionalOnMissingBean(VideoListingCache.class)
  public VideoListingCache disabledVideoListingCache() {
    return new VideoListingCache() {

      @Override
      public Optional<VideoPageResponse> find(UUID userId, VideoStatus status, int page, int size) {
        return Optional.empty();
      }

      @Override
      public void store(
          UUID userId, VideoStatus status, int page, int size, VideoPageResponse response) {}

      @Override
      public void invalidate(UUID userId) {}
    };
  }
}
