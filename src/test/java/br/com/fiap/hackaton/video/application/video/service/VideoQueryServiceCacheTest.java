package br.com.fiap.hackaton.video.application.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.hackaton.video.application.video.dto.VideoPageResponse;
import br.com.fiap.hackaton.video.application.video.gateway.VideoListingCache;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.repository.VideoRepository;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class VideoQueryServiceCacheTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Mock private VideoRepository videoRepository;
  @Mock private VideoListingCache listingCache;

  private VideoQueryService videoQueryService;

  @BeforeEach
  void setUp() {
    videoQueryService = new VideoQueryService(videoRepository, listingCache);
  }

  private VideoPageResponse cachedPage() {
    return new VideoPageResponse(List.of(), 7, 1, 0, 20);
  }

  @Test
  @DisplayName("Deve servir do cache sem tocar no banco quando ha acerto")
  void deveServirDoCacheSemTocarNoBanco() {
    when(listingCache.find(USER_ID, null, 0, 20)).thenReturn(Optional.of(cachedPage()));

    VideoPageResponse response = videoQueryService.listOwnedBy(USER_ID, null, 0, 20);

    assertThat(response.totalElements()).isEqualTo(7);
    verify(videoRepository, never()).findAllByOwner(any(), any(), any());
    verify(listingCache, never()).store(any(), any(), anyInt(), anyInt(), any());
  }

  @Test
  @DisplayName("Deve consultar o banco e gravar no cache quando ha falha de cache")
  void deveConsultarBancoEGravarNoCache() {
    when(listingCache.find(USER_ID, null, 0, 20)).thenReturn(Optional.empty());
    when(videoRepository.findAllByOwner(any(), any(), any()))
        .thenReturn(
            new PageImpl<>(List.of(new Video(USER_ID, "aula.mp4")), PageRequest.of(0, 20), 1));

    VideoPageResponse response = videoQueryService.listOwnedBy(USER_ID, null, 0, 20);

    verify(videoRepository).findAllByOwner(eq(USER_ID), eq(null), any());
    verify(listingCache).store(USER_ID, null, 0, 20, response);
  }

  @Test
  @DisplayName("Deve consultar o cache com os parametros ja normalizados")
  void deveConsultarCacheComParametrosNormalizados() {
    when(listingCache.find(USER_ID, VideoStatus.COMPLETED, 0, VideoQueryService.MAX_PAGE_SIZE))
        .thenReturn(Optional.of(cachedPage()));

    videoQueryService.listOwnedBy(USER_ID, VideoStatus.COMPLETED, -3, 100_000);

    verify(listingCache).find(USER_ID, VideoStatus.COMPLETED, 0, VideoQueryService.MAX_PAGE_SIZE);
    verify(videoRepository, never()).findAllByOwner(any(), any(), any());
  }

  @Test
  @DisplayName("Nao deve usar cache no detalhe de um video")
  void naoDeveUsarCacheNoDetalhe() {
    Video video = new Video(USER_ID, "aula.mp4");
    when(videoRepository.findByIdAndUserId(video.getId(), USER_ID)).thenReturn(Optional.of(video));

    videoQueryService.findOwnedBy(USER_ID, video.getId());

    verify(listingCache, never()).find(any(), any(), anyInt(), anyInt());
  }
}
