package br.com.fiap.hackaton.video.domain.video.gateway;

import br.com.fiap.hackaton.video.domain.outbox.entity.OutboxEvent;

public interface VideoEventPublisher {

  void publishConfirmed(OutboxEvent event);
}
