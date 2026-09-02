package br.com.fiap.hackaton.video.infrastructure.messaging;

import br.com.fiap.hackaton.video.domain.outbox.entity.OutboxEvent;
import br.com.fiap.hackaton.video.domain.video.gateway.EventPublishException;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoEventPublisher;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RabbitVideoEventPublisher implements VideoEventPublisher {

  private final RabbitTemplate rabbitTemplate;
  private final long confirmTimeoutMillis;

  public RabbitVideoEventPublisher(
      RabbitTemplate rabbitTemplate,
      @Value("${video.outbox.confirm-timeout-ms:5000}") long confirmTimeoutMillis) {
    this.rabbitTemplate = rabbitTemplate;
    this.confirmTimeoutMillis = confirmTimeoutMillis;
  }

  @Override
  public void publishConfirmed(OutboxEvent event) {
    CorrelationData correlation = new CorrelationData(event.getId().toString());

    try {
      rabbitTemplate.send(
          RabbitMqConfig.VIDEO_EXCHANGE, event.getEventType(), toMessage(event), correlation);
    } catch (AmqpException e) {
      throw new EventPublishException("Broker indisponivel ao publicar " + event.getId(), e);
    }

    awaitConfirmation(event, correlation);
  }

  private Message toMessage(OutboxEvent event) {
    return MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
        .setContentEncoding(StandardCharsets.UTF_8.name())
        .setMessageId(event.getId().toString())
        .setHeader("eventType", event.getEventType())
        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
        .build();
  }

  private void awaitConfirmation(OutboxEvent event, CorrelationData correlation) {
    try {
      CorrelationData.Confirm confirm =
          correlation.getFuture().get(confirmTimeoutMillis, TimeUnit.MILLISECONDS);

      if (!confirm.isAck()) {
        throw new EventPublishException(
            "Broker recusou o evento %s: %s".formatted(event.getId(), confirm.getReason()));
      }
      log.debug("Evento {} confirmado pelo broker", event.getId());
    } catch (TimeoutException e) {
      throw new EventPublishException(
          "Broker nao confirmou o evento %s em %d ms"
              .formatted(event.getId(), confirmTimeoutMillis),
          e);
    } catch (ExecutionException e) {
      throw new EventPublishException("Falha ao confirmar o evento " + event.getId(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EventPublishException("Confirmacao do evento interrompida: " + event.getId(), e);
    }
  }
}
