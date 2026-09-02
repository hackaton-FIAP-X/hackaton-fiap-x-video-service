package br.com.fiap.hackaton.video.domain.video.valueobject;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VideoStatusTest {

  @Test
  @DisplayName("Deve permitir o caminho feliz RECEIVED ate COMPLETED")
  void devePermitirCaminhoFeliz() {
    assertThat(VideoStatus.RECEIVED.allowsTransitionTo(VideoStatus.QUEUED)).isTrue();
    assertThat(VideoStatus.QUEUED.allowsTransitionTo(VideoStatus.PROCESSING)).isTrue();
    assertThat(VideoStatus.PROCESSING.allowsTransitionTo(VideoStatus.COMPLETED)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = VideoStatus.class,
      names = {"RECEIVED", "QUEUED", "PROCESSING"})
  @DisplayName("Deve permitir falha a partir de qualquer status nao final")
  void devePermitirFalhaDeStatusNaoFinal(VideoStatus origem) {
    assertThat(origem.allowsTransitionTo(VideoStatus.FAILED)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = VideoStatus.class,
      names = {"COMPLETED", "FAILED"})
  @DisplayName("Deve tratar COMPLETED e FAILED como estados finais")
  void deveTratarStatusFinais(VideoStatus finalStatus) {
    assertThat(finalStatus.isFinal()).isTrue();

    for (VideoStatus alvo : VideoStatus.values()) {
      assertThat(finalStatus.allowsTransitionTo(alvo)).isFalse();
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = VideoStatus.class,
      names = {"RECEIVED", "QUEUED", "PROCESSING"})
  @DisplayName("Deve tratar os demais status como nao finais")
  void deveTratarStatusNaoFinais(VideoStatus status) {
    assertThat(status.isFinal()).isFalse();
  }

  @Test
  @DisplayName("Nao deve permitir pular etapas nem voltar no fluxo")
  void naoDevePermitirTransicaoForaDoFluxo() {
    assertThat(VideoStatus.RECEIVED.allowsTransitionTo(VideoStatus.PROCESSING)).isFalse();
    assertThat(VideoStatus.RECEIVED.allowsTransitionTo(VideoStatus.COMPLETED)).isFalse();
    assertThat(VideoStatus.QUEUED.allowsTransitionTo(VideoStatus.RECEIVED)).isFalse();
    assertThat(VideoStatus.PROCESSING.allowsTransitionTo(VideoStatus.QUEUED)).isFalse();
  }

  @Test
  @DisplayName("Nao deve permitir transicao para status nulo")
  void naoDevePermitirTransicaoNula() {
    assertThat(VideoStatus.RECEIVED.allowsTransitionTo(null)).isFalse();
  }
}
