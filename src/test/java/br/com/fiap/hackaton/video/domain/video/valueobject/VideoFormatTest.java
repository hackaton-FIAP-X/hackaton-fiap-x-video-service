package br.com.fiap.hackaton.video.domain.video.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fiap.hackaton.video.domain.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VideoFormatTest {

  @ParameterizedTest
  @CsvSource({
    "aula.mp4, MP4",
    "aula.avi, AVI",
    "aula.mov, MOV",
    "aula.mkv, MKV",
    "aula.webm, WEBM"
  })
  @DisplayName("Deve reconhecer os formatos aceitos pelo worker")
  void deveReconhecerFormatosAceitos(String filename, VideoFormat esperado) {
    assertThat(VideoFormat.fromFilename(filename)).isEqualTo(esperado);
  }

  @ParameterizedTest
  @ValueSource(strings = {"AULA.MP4", "Aula.Mp4", "aula.MOV"})
  @DisplayName("Deve ignorar a caixa da extensao")
  void deveIgnorarCaixaDaExtensao(String filename) {
    assertThat(VideoFormat.fromFilename(filename)).isNotNull();
  }

  @Test
  @DisplayName("Deve considerar apenas a ultima extensao do nome")
  void deveConsiderarUltimaExtensao() {
    assertThat(VideoFormat.fromFilename("aula.exe.mp4")).isEqualTo(VideoFormat.MP4);
  }

  @ParameterizedTest
  @ValueSource(strings = {"malware.exe", "documento.pdf", "planilha.xlsx", "aula.mp3"})
  @DisplayName("Deve recusar formato que o worker nao processa")
  void deveRecusarFormatoNaoSuportado(String filename) {
    assertThatThrownBy(() -> VideoFormat.fromFilename(filename))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("nao suportado");
  }

  @ParameterizedTest
  @ValueSource(strings = {"semextensao", "terminaemponto."})
  @DisplayName("Deve recusar nome sem extensao utilizavel")
  void deveRecusarNomeSemExtensao(String filename) {
    assertThatThrownBy(() -> VideoFormat.fromFilename(filename))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("extensao");
  }

  @Test
  @DisplayName("Deve recusar nome vazio ou nulo")
  void deveRecusarNomeVazio() {
    assertThatThrownBy(() -> VideoFormat.fromFilename(null)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> VideoFormat.fromFilename("  ")).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("Deve declarar o content type correspondente ao formato")
  void deveDeclararContentType() {
    assertThat(VideoFormat.MP4.contentType()).isEqualTo("video/mp4");
    assertThat(VideoFormat.WEBM.contentType()).isEqualTo("video/webm");
  }

  @Test
  @DisplayName("Deve listar os formatos aceitos para a mensagem de erro")
  void deveListarFormatosAceitos() {
    assertThat(VideoFormat.supported()).contains("mp4", "avi", "mov", "mkv", "webm");
  }
}
