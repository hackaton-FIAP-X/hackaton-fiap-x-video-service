package br.com.fiap.hackaton.video.application.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.hackaton.video.domain.outbox.entity.OutboxEvent;
import br.com.fiap.hackaton.video.domain.outbox.repository.OutboxRepository;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.gateway.EventPublishException;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoEventPublisher;
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

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

  private static final UUID USER_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

  @Mock private OutboxRepository outboxRepository;
  @Mock private VideoRepository videoRepository;
  @Mock private VideoEventPublisher eventPublisher;

  private OutboxDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher = new OutboxDispatcher(outboxRepository, videoRepository, eventPublisher, 50);
  }

  private Video video() {
    return new Video(USER_ID, "aula.mp4");
  }

  private OutboxEvent eventFor(Video video) {
    return new OutboxEvent(video.getId(), "video.uploaded", "{\"videoId\":\"x\"}");
  }

  @Test
  @DisplayName("Nao deve publicar nada quando o outbox esta vazio")
  void naoDevePublicarComOutboxVazio() {
    when(outboxRepository.lockPendingBatch(anyInt())).thenReturn(List.of());

    assertThat(dispatcher.dispatchPending()).isZero();
    verify(eventPublisher, never()).publishConfirmed(any());
  }

  @Test
  @DisplayName("Deve marcar o evento como publicado e mover o video para QUEUED")
  void devePublicarEMoverParaQueued() {
    Video video = video();
    OutboxEvent event = eventFor(video);
    when(outboxRepository.lockPendingBatch(anyInt())).thenReturn(List.of(event));
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    int publicados = dispatcher.dispatchPending();

    assertThat(publicados).isEqualTo(1);
    assertThat(event.isPublished()).isTrue();
    assertThat(video.getStatus()).isEqualTo(VideoStatus.QUEUED);
    verify(outboxRepository).save(event);
    verify(videoRepository).save(video);
  }

  @Test
  @DisplayName("Deve deixar o evento pendente e o video em RECEIVED quando o broker recusa")
  void deveManterPendenteQuandoBrokerRecusa() {
    Video video = video();
    OutboxEvent event = eventFor(video);
    when(outboxRepository.lockPendingBatch(anyInt())).thenReturn(List.of(event));
    doThrow(new EventPublishException("broker fora do ar"))
        .when(eventPublisher)
        .publishConfirmed(event);

    int publicados = dispatcher.dispatchPending();

    assertThat(publicados).isZero();
    assertThat(event.isPublished()).isFalse();
    assertThat(event.getAttempts()).isEqualTo(1);
    assertThat(event.getLastError()).isEqualTo("broker fora do ar");
    assertThat(video.getStatus()).isEqualTo(VideoStatus.RECEIVED);
    verify(outboxRepository).save(event);
    verify(videoRepository, never()).save(any());
  }

  @Test
  @DisplayName("Deve seguir publicando os demais eventos quando um deles falha")
  void devePublicarOsDemaisQuandoUmFalha() {
    Video comFalha = video();
    Video comSucesso = video();
    OutboxEvent eventoComFalha = eventFor(comFalha);
    OutboxEvent eventoComSucesso = eventFor(comSucesso);

    when(outboxRepository.lockPendingBatch(anyInt()))
        .thenReturn(List.of(eventoComFalha, eventoComSucesso));
    doThrow(new EventPublishException("recusado"))
        .when(eventPublisher)
        .publishConfirmed(eventoComFalha);
    when(videoRepository.findById(comSucesso.getId())).thenReturn(Optional.of(comSucesso));

    int publicados = dispatcher.dispatchPending();

    assertThat(publicados).isEqualTo(1);
    assertThat(eventoComFalha.isPublished()).isFalse();
    assertThat(eventoComSucesso.isPublished()).isTrue();
  }

  @Test
  @DisplayName("Nao deve alterar o status de video que ja saiu de RECEIVED")
  void naoDeveAlterarVideoForaDeReceived() {
    Video video = video();
    video.markAsQueued();
    video.markAsProcessing();
    OutboxEvent event = eventFor(video);

    when(outboxRepository.lockPendingBatch(anyInt())).thenReturn(List.of(event));
    when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

    dispatcher.dispatchPending();

    assertThat(video.getStatus()).isEqualTo(VideoStatus.PROCESSING);
    verify(videoRepository, never()).save(any());
  }

  @Test
  @DisplayName("Deve confirmar o evento mesmo se o video nao existir mais")
  void deveConfirmarEventoSemVideo() {
    Video video = video();
    OutboxEvent event = eventFor(video);
    when(outboxRepository.lockPendingBatch(anyInt())).thenReturn(List.of(event));
    when(videoRepository.findById(video.getId())).thenReturn(Optional.empty());

    assertThat(dispatcher.dispatchPending()).isEqualTo(1);
    assertThat(event.isPublished()).isTrue();
  }
}
