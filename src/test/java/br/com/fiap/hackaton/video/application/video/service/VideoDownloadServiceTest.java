package br.com.fiap.hackaton.video.application.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.hackaton.video.application.shared.exception.ConflictException;
import br.com.fiap.hackaton.video.application.shared.exception.ResourceNotFoundException;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageGateway;
import br.com.fiap.hackaton.video.domain.video.repository.VideoRepository;
import br.com.fiap.hackaton.video.domain.video.valueobject.StorageKey;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VideoDownloadServiceTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final String PRESIGNED_URL =
      "http://localhost:9000/fiapx/out.zip?X-Amz-Signature=abc";

  @Mock private VideoRepository videoRepository;
  @Mock private VideoStorageGateway storageGateway;

  private VideoDownloadService videoDownloadService;

  @BeforeEach
  void setUp() {
    videoDownloadService =
        new VideoDownloadService(new VideoQueryService(videoRepository), storageGateway, TTL);
  }

  private Video completedVideo() {
    Video video = new Video(USER_ID, "aula.mp4");
    video.markAsQueued();
    video.markAsProcessing();
    video.markAsCompleted("fiapx/outputs/%s/%s.zip".formatted(USER_ID, video.getId()), 120);
    return video;
  }

  private void repositoryReturns(Video video) {
    when(videoRepository.findByIdAndUserId(video.getId(), USER_ID)).thenReturn(Optional.of(video));
  }

  @Test
  @DisplayName("Deve assinar o download do ZIP de um video concluido")
  void deveAssinarDownloadDeVideoConcluido() {
    Video video = completedVideo();
    repositoryReturns(video);
    when(storageGateway.presignedDownloadUrl(any(StorageKey.class), eq(TTL)))
        .thenReturn(PRESIGNED_URL);

    String url = videoDownloadService.presignedZipUrl(USER_ID, video.getId());

    assertThat(url).isEqualTo(PRESIGNED_URL);
  }

  @Test
  @DisplayName("Deve assinar exatamente a chave do ZIP gravada pelo worker")
  void deveAssinarAChaveDoZip() {
    Video video = completedVideo();
    repositoryReturns(video);
    when(storageGateway.presignedDownloadUrl(any(StorageKey.class), any()))
        .thenReturn(PRESIGNED_URL);

    videoDownloadService.presignedZipUrl(USER_ID, video.getId());

    ArgumentCaptor<StorageKey> key = ArgumentCaptor.forClass(StorageKey.class);
    verify(storageGateway).presignedDownloadUrl(key.capture(), any());
    assertThat(key.getValue().value()).isEqualTo(video.getZipKey());
  }

  @Test
  @DisplayName("Deve usar o tempo de validade configurado")
  void deveUsarTempoDeValidadeConfigurado() {
    Video video = completedVideo();
    repositoryReturns(video);
    when(storageGateway.presignedDownloadUrl(any(), any())).thenReturn(PRESIGNED_URL);

    videoDownloadService.presignedZipUrl(USER_ID, video.getId());

    ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
    verify(storageGateway).presignedDownloadUrl(any(), ttl.capture());
    assertThat(ttl.getValue()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  @DisplayName("Deve recusar download de video ainda em RECEIVED")
  void deveRecusarDownloadDeVideoEmReceived() {
    Video video = new Video(USER_ID, "aula.mp4");
    repositoryReturns(video);

    assertThatThrownBy(() -> videoDownloadService.presignedZipUrl(USER_ID, video.getId()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("RECEIVED");

    verify(storageGateway, never()).presignedDownloadUrl(any(), any());
  }

  @Test
  @DisplayName("Deve recusar download de video em PROCESSING")
  void deveRecusarDownloadDeVideoEmProcessamento() {
    Video video = new Video(USER_ID, "aula.mp4");
    video.markAsQueued();
    video.markAsProcessing();
    repositoryReturns(video);

    assertThatThrownBy(() -> videoDownloadService.presignedZipUrl(USER_ID, video.getId()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("PROCESSING");
  }

  @Test
  @DisplayName("Deve recusar download de video que falhou")
  void deveRecusarDownloadDeVideoComFalha() {
    Video video = new Video(USER_ID, "aula.mp4");
    video.markAsQueued();
    video.markAsProcessing();
    video.markAsFailed("ffmpeg exit code 1", 3);
    repositoryReturns(video);

    assertThatThrownBy(() -> videoDownloadService.presignedZipUrl(USER_ID, video.getId()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("FAILED");

    verify(storageGateway, never()).presignedDownloadUrl(any(), any());
  }

  @Test
  @DisplayName("Deve tratar video de outro usuario como inexistente, sem assinar nada")
  void deveTratarVideoAlheioComoInexistente() {
    UUID videoAlheio = UUID.randomUUID();
    when(videoRepository.findByIdAndUserId(videoAlheio, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> videoDownloadService.presignedZipUrl(USER_ID, videoAlheio))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(storageGateway, never()).presignedDownloadUrl(any(), any());
  }
}
