package br.com.fiap.hackaton.video.application.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.hackaton.video.application.video.dto.VideoFailedEvent;
import br.com.fiap.hackaton.video.application.video.dto.VideoProcessedEvent;
import br.com.fiap.hackaton.video.application.video.gateway.VideoListingCache;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.repository.VideoRepository;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VideoStatusUpdateServiceTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String TRACE_ID = "trace-123";

  @Mock private VideoRepository videoRepository;
  @Mock private VideoListingCache listingCache;

  private VideoStatusUpdateService statusUpdateService;

  @BeforeEach
  void setUp() {
    statusUpdateService = new VideoStatusUpdateService(videoRepository, listingCache);
  }

  private Video queuedVideo() {
    Video video = new Video(USER_ID, "aula.mp4");
    video.markAsQueued();
    return video;
  }

  private void repositoryLocks(Video video) {
    when(videoRepository.findByIdForUpdate(video.getId())).thenReturn(Optional.of(video));
  }

  private VideoProcessedEvent processedEvent(Video video) {
    return new VideoProcessedEvent(
        video.getId(),
        USER_ID,
        "fiapx/outputs/%s/%s.zip".formatted(USER_ID, video.getId()),
        120,
        LocalDateTime.now(),
        TRACE_ID);
  }

  private VideoFailedEvent failedEvent(Video video) {
    return new VideoFailedEvent(video.getId(), USER_ID, "FFMPEG_ERROR", "exit code 1", 3, TRACE_ID);
  }

  @Test
  @DisplayName("Deve concluir o video com a chave do ZIP e a contagem de frames")
  void deveConcluirVideo() {
    Video video = queuedVideo();
    repositoryLocks(video);

    statusUpdateService.applyProcessed(processedEvent(video));

    assertThat(video.getStatus()).isEqualTo(VideoStatus.COMPLETED);
    assertThat(video.getFrameCount()).isEqualTo(120);
    assertThat(video.getZipKey()).endsWith("%s.zip".formatted(video.getId()));
    assertThat(video.isDownloadable()).isTrue();
    verify(videoRepository).save(video);
  }

  @Test
  @DisplayName("Deve registrar a falha com codigo e mensagem do worker")
  void deveRegistrarFalha() {
    Video video = queuedVideo();
    repositoryLocks(video);

    statusUpdateService.applyFailed(failedEvent(video));

    assertThat(video.getStatus()).isEqualTo(VideoStatus.FAILED);
    assertThat(video.getErrorMessage()).isEqualTo("[FFMPEG_ERROR] exit code 1");
    assertThat(video.getAttempts()).isEqualTo(3);
    verify(videoRepository).save(video);
  }

  @Test
  @DisplayName("Deve dispensar o codigo quando o worker nao enviar")
  void deveDispensarCodigoAusente() {
    Video video = queuedVideo();
    repositoryLocks(video);

    statusUpdateService.applyFailed(
        new VideoFailedEvent(video.getId(), USER_ID, null, "estourou o timeout", 1, TRACE_ID));

    assertThat(video.getErrorMessage()).isEqualTo("estourou o timeout");
  }

  @Test
  @DisplayName("Deve tolerar evento de falha sem contagem de tentativas")
  void deveTolerarFalhaSemTentativas() {
    Video video = queuedVideo();
    repositoryLocks(video);

    statusUpdateService.applyFailed(
        new VideoFailedEvent(video.getId(), USER_ID, "X", "erro", null, TRACE_ID));

    assertThat(video.getStatus()).isEqualTo(VideoStatus.FAILED);
  }

  @Test
  @DisplayName("Deve manter a linha igual ao reentregar o mesmo video.processed tres vezes")
  void deveSerIdempotenteEmProcessed() {
    Video video = queuedVideo();
    repositoryLocks(video);
    VideoProcessedEvent event = processedEvent(video);

    statusUpdateService.applyProcessed(event);
    LocalDateTime primeiraAtualizacao = video.getUpdatedAt();

    statusUpdateService.applyProcessed(event);
    statusUpdateService.applyProcessed(event);

    assertThat(video.getStatus()).isEqualTo(VideoStatus.COMPLETED);
    assertThat(video.getFrameCount()).isEqualTo(120);
    assertThat(video.getUpdatedAt()).isEqualTo(primeiraAtualizacao);
    verify(videoRepository, times(1)).save(video);
  }

  @Test
  @DisplayName("Deve manter a linha igual ao reentregar o mesmo video.failed tres vezes")
  void deveSerIdempotenteEmFailed() {
    Video video = queuedVideo();
    repositoryLocks(video);
    VideoFailedEvent event = failedEvent(video);

    statusUpdateService.applyFailed(event);
    statusUpdateService.applyFailed(event);
    statusUpdateService.applyFailed(event);

    assertThat(video.getStatus()).isEqualTo(VideoStatus.FAILED);
    assertThat(video.getAttempts()).isEqualTo(3);
    verify(videoRepository, times(1)).save(video);
  }

  @Test
  @DisplayName("Nao deve deixar um video.failed atrasado sobrescrever uma conclusao")
  void naoDeveSobrescreverConclusaoComFalhaAtrasada() {
    Video video = queuedVideo();
    repositoryLocks(video);

    statusUpdateService.applyProcessed(processedEvent(video));
    statusUpdateService.applyFailed(failedEvent(video));

    assertThat(video.getStatus()).isEqualTo(VideoStatus.COMPLETED);
    assertThat(video.getErrorMessage()).isNull();
    assertThat(video.isDownloadable()).isTrue();
  }

  @Test
  @DisplayName("Nao deve deixar um video.processed atrasado sobrescrever uma falha")
  void naoDeveSobrescreverFalhaComConclusaoAtrasada() {
    Video video = queuedVideo();
    repositoryLocks(video);

    statusUpdateService.applyFailed(failedEvent(video));
    statusUpdateService.applyProcessed(processedEvent(video));

    assertThat(video.getStatus()).isEqualTo(VideoStatus.FAILED);
    assertThat(video.getZipKey()).isNull();
    assertThat(video.isDownloadable()).isFalse();
  }

  @Test
  @DisplayName("Deve descartar evento de video que nao existe mais, sem estourar")
  void deveDescartarEventoDeVideoInexistente() {
    UUID inexistente = UUID.randomUUID();
    when(videoRepository.findByIdForUpdate(inexistente)).thenReturn(Optional.empty());

    statusUpdateService.applyProcessed(
        new VideoProcessedEvent(inexistente, USER_ID, "k.zip", 1, LocalDateTime.now(), TRACE_ID));
    statusUpdateService.applyFailed(
        new VideoFailedEvent(inexistente, USER_ID, "X", "erro", 1, TRACE_ID));

    verify(videoRepository, never()).save(any());
  }

  @Test
  @DisplayName("Deve carregar o video com lock, para reentregas concorrentes serializarem")
  void deveCarregarComLock() {
    Video video = queuedVideo();
    repositoryLocks(video);

    statusUpdateService.applyProcessed(processedEvent(video));

    verify(videoRepository).findByIdForUpdate(video.getId());
    verify(videoRepository, never()).findById(any());
  }
}
