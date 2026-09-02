package br.com.fiap.hackaton.video.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.fiap.hackaton.video.application.video.dto.VideoFailedEvent;
import br.com.fiap.hackaton.video.application.video.dto.VideoProcessedEvent;
import br.com.fiap.hackaton.video.application.video.service.VideoStatusUpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class VideoStatusConsumerTest {

  private static final UUID VIDEO_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Mock private VideoStatusUpdateService statusUpdateService;

  private VideoStatusConsumer consumer;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    consumer = new VideoStatusConsumer(statusUpdateService, objectMapper);
  }

  private Message message(String routingKey, String payload) {
    MessageProperties properties = new MessageProperties();
    properties.setReceivedRoutingKey(routingKey);
    return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
  }

  @Test
  @DisplayName("Deve encaminhar video.processed para a conclusao")
  void deveEncaminharProcessed() {
    String payload =
        """
        {"videoId":"%s","userId":"%s","zipKey":"fiapx/outputs/a/b.zip",
         "frameCount":120,"finishedAt":"2026-09-02T01:00:00","traceId":"t-1"}
        """
            .formatted(VIDEO_ID, USER_ID);

    consumer.onStatusEvent(message("video.processed", payload));

    ArgumentCaptor<VideoProcessedEvent> event = ArgumentCaptor.forClass(VideoProcessedEvent.class);
    verify(statusUpdateService).applyProcessed(event.capture());
    assertThat(event.getValue().videoId()).isEqualTo(VIDEO_ID);
    assertThat(event.getValue().frameCount()).isEqualTo(120);
    assertThat(event.getValue().zipKey()).isEqualTo("fiapx/outputs/a/b.zip");
  }

  @Test
  @DisplayName("Deve encaminhar video.failed para o registro de falha")
  void deveEncaminharFailed() {
    String payload =
        """
        {"videoId":"%s","userId":"%s","errorCode":"FFMPEG_ERROR",
         "errorMessage":"exit code 1","attempts":3,"traceId":"t-2"}
        """
            .formatted(VIDEO_ID, USER_ID);

    consumer.onStatusEvent(message("video.failed", payload));

    ArgumentCaptor<VideoFailedEvent> event = ArgumentCaptor.forClass(VideoFailedEvent.class);
    verify(statusUpdateService).applyFailed(event.capture());
    assertThat(event.getValue().errorCode()).isEqualTo("FFMPEG_ERROR");
    assertThat(event.getValue().attempts()).isEqualTo(3);
  }

  @Test
  @DisplayName("Deve tolerar campos extras que o worker venha a acrescentar")
  void deveTolerarCamposExtras() {
    String payload =
        """
        {"videoId":"%s","userId":"%s","zipKey":"k.zip","frameCount":10,
         "finishedAt":"2026-09-02T01:00:00","traceId":"t","campoNovoDoWorker":"x"}
        """
            .formatted(VIDEO_ID, USER_ID);

    consumer.onStatusEvent(message("video.processed", payload));

    verify(statusUpdateService).applyProcessed(any());
  }

  @Test
  @DisplayName("Deve mandar para a DLQ um payload que nao e JSON, sem retentar")
  void deveMandarPayloadInvalidoParaDlq() {
    assertThatThrownBy(() -> consumer.onStatusEvent(message("video.processed", "isto nao e json")))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);

    verify(statusUpdateService, never()).applyProcessed(any());
  }

  @Test
  @DisplayName("Deve mandar para a DLQ um evento sem videoId")
  void deveMandarEventoSemVideoIdParaDlq() {
    assertThatThrownBy(
            () -> consumer.onStatusEvent(message("video.processed", "{\"frameCount\":10}")))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class)
        .hasMessageContaining("videoId");

    verify(statusUpdateService, never()).applyProcessed(any());
  }

  @Test
  @DisplayName("Deve mandar para a DLQ uma routing key desconhecida")
  void deveMandarRoutingKeyDesconhecidaParaDlq() {
    assertThatThrownBy(() -> consumer.onStatusEvent(message("video.inventado", "{}")))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class)
        .hasMessageContaining("desconhecida");

    verify(statusUpdateService, never()).applyProcessed(any());
    verify(statusUpdateService, never()).applyFailed(any());
  }
}
