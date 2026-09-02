package br.com.fiap.hackaton.video.domain.outbox.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fiap.hackaton.video.domain.shared.exception.DomainException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

  private static final UUID AGGREGATE_ID = UUID.randomUUID();
  private static final String PAYLOAD = "{\"videoId\":\"x\"}";

  private OutboxEvent novoEvento() {
    return new OutboxEvent(AGGREGATE_ID, "video.uploaded", PAYLOAD);
  }

  @Test
  @DisplayName("Deve nascer pendente e sem tentativas")
  void deveNascerPendente() {
    OutboxEvent event = novoEvento();

    assertThat(event.getId()).isNotNull();
    assertThat(event.getAggregateId()).isEqualTo(AGGREGATE_ID);
    assertThat(event.getEventType()).isEqualTo("video.uploaded");
    assertThat(event.getPayload()).isEqualTo(PAYLOAD);
    assertThat(event.isPublished()).isFalse();
    assertThat(event.getAttempts()).isZero();
    assertThat(event.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("Deve marcar como publicado e limpar o ultimo erro")
  void deveMarcarComoPublicado() {
    OutboxEvent event = novoEvento();
    event.registerFailure("broker fora do ar");

    event.markAsPublished();

    assertThat(event.isPublished()).isTrue();
    assertThat(event.getPublishedAt()).isNotNull();
    assertThat(event.getLastError()).isNull();
    assertThat(event.getAttempts()).isEqualTo(2);
  }

  @Test
  @DisplayName("Deve acumular tentativas a cada falha")
  void deveAcumularTentativas() {
    OutboxEvent event = novoEvento();

    event.registerFailure("primeira");
    event.registerFailure("segunda");

    assertThat(event.getAttempts()).isEqualTo(2);
    assertThat(event.getLastError()).isEqualTo("segunda");
  }

  @Test
  @DisplayName("Nao deve reprocessar evento ja publicado")
  void naoDeveReprocessarEventoPublicado() {
    OutboxEvent event = novoEvento();
    event.markAsPublished();
    int tentativasAposPublicar = event.getAttempts();

    event.markAsPublished();
    event.registerFailure("chegou tarde");

    assertThat(event.getAttempts()).isEqualTo(tentativasAposPublicar);
    assertThat(event.getLastError()).isNull();
  }

  @Test
  @DisplayName("Deve truncar o erro no limite da coluna")
  void deveTruncarErro() {
    OutboxEvent event = novoEvento();

    event.registerFailure("x".repeat(2_000));

    assertThat(event.getLastError()).hasSize(1_000);
  }

  @Test
  @DisplayName("Deve preencher mensagem padrao quando a falha vem sem detalhe")
  void devePreencherMensagemPadrao() {
    OutboxEvent event = novoEvento();

    event.registerFailure(null);

    assertThat(event.getLastError()).isNotBlank();
  }

  @Test
  @DisplayName("Deve recusar evento sem agregado, tipo ou payload")
  void deveRecusarEventoIncompleto() {
    assertThatThrownBy(() -> new OutboxEvent(null, "video.uploaded", PAYLOAD))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> new OutboxEvent(AGGREGATE_ID, "  ", PAYLOAD))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> new OutboxEvent(AGGREGATE_ID, "video.uploaded", "  "))
        .isInstanceOf(DomainException.class);
  }
}
