package br.com.fiap.hackaton.video.infrastructure.messaging;

import br.com.fiap.hackaton.video.application.video.dto.VideoFailedEvent;
import br.com.fiap.hackaton.video.application.video.dto.VideoProcessedEvent;
import br.com.fiap.hackaton.video.application.video.service.VideoStatusUpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoStatusConsumer {

  private final VideoStatusUpdateService statusUpdateService;
  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitMqConfig.STATUS_QUEUE)
  public void onStatusEvent(Message message) {
    String routingKey = message.getMessageProperties().getReceivedRoutingKey();
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);

    switch (routingKey) {
      case VideoProcessedEvent.EVENT_TYPE ->
          statusUpdateService.applyProcessed(parse(payload, VideoProcessedEvent.class, routingKey));
      case VideoFailedEvent.EVENT_TYPE ->
          statusUpdateService.applyFailed(parse(payload, VideoFailedEvent.class, routingKey));
      default -> throw rejected("Routing key desconhecida na fila de status: " + routingKey, null);
    }
  }

  private <T> T parse(String payload, Class<T> type, String routingKey) {
    try {
      T event = objectMapper.readValue(payload, type);
      requireVideoId(event, routingKey);
      return event;
    } catch (AmqpRejectAndDontRequeueException e) {
      throw e;
    } catch (Exception e) {
      throw rejected("Payload invalido para " + routingKey, e);
    }
  }

  private void requireVideoId(Object event, String routingKey) {
    boolean missingId =
        switch (event) {
          case VideoProcessedEvent processed -> processed.videoId() == null;
          case VideoFailedEvent failed -> failed.videoId() == null;
          default -> true;
        };

    if (missingId) {
      throw rejected("Evento " + routingKey + " sem videoId", null);
    }
  }

  private AmqpRejectAndDontRequeueException rejected(String reason, Throwable cause) {
    log.error("Mensagem enviada para a DLQ: {}", reason, cause);
    return new AmqpRejectAndDontRequeueException(reason, cause);
  }
}
