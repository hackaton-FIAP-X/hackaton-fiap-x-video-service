package br.com.fiap.hackaton.video.domain.outbox.entity;

import br.com.fiap.hackaton.video.domain.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor
public class OutboxEvent {

  private static final int MAX_ERROR_LENGTH = 1000;

  @Id private UUID id;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, length = 60)
  private String eventType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error", length = MAX_ERROR_LENGTH)
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  public OutboxEvent(UUID aggregateId, String eventType, String payload) {
    if (aggregateId == null) {
      throw new DomainException("Evento de outbox precisa referenciar um agregado");
    }
    if (eventType == null || eventType.isBlank()) {
      throw new DomainException("Evento de outbox precisa de um tipo");
    }
    if (payload == null || payload.isBlank()) {
      throw new DomainException("Evento de outbox precisa de um payload");
    }

    this.id = UUID.randomUUID();
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.attempts = 0;
    this.createdAt = LocalDateTime.now();
  }

  public void markAsPublished() {
    if (isPublished()) {
      return;
    }
    this.publishedAt = LocalDateTime.now();
    this.attempts++;
    this.lastError = null;
  }

  public void registerFailure(String error) {
    if (isPublished()) {
      return;
    }
    this.attempts++;
    this.lastError = truncate(error);
  }

  public boolean isPublished() {
    return publishedAt != null;
  }

  private String truncate(String error) {
    if (error == null || error.isBlank()) {
      return "Falha nao detalhada pelo broker";
    }
    return error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
  }
}
