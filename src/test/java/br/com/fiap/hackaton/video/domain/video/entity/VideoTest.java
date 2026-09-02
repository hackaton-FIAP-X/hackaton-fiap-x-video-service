package br.com.fiap.hackaton.video.domain.video.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fiap.hackaton.video.domain.shared.exception.DomainException;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VideoTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final String FILENAME = "aula-01.mp4";

  private Video novoVideo() {
    return new Video(USER_ID, FILENAME);
  }

  private Video videoEmProcessamento() {
    Video video = novoVideo();
    video.markAsQueued();
    video.markAsProcessing();
    return video;
  }

  @Nested
  @DisplayName("Criacao")
  class Criacao {

    @Test
    @DisplayName("Deve nascer em RECEIVED com identificador e chave de storage")
    void deveNascerEmReceived() {
      Video video = novoVideo();

      assertThat(video.getId()).isNotNull();
      assertThat(video.getUserId()).isEqualTo(USER_ID);
      assertThat(video.getOriginalFilename()).isEqualTo(FILENAME);
      assertThat(video.getStatus()).isEqualTo(VideoStatus.RECEIVED);
      assertThat(video.getAttempts()).isZero();
      assertThat(video.getCreatedAt()).isNotNull();
      assertThat(video.getZipKey()).isNull();
      assertThat(video.getFrameCount()).isNull();
    }

    @Test
    @DisplayName("Deve montar a chave de storage no formato do contrato")
    void deveMontarChaveDeStorage() {
      Video video = novoVideo();

      assertThat(video.getStorageKey())
          .isEqualTo("fiapx/inputs/%s/%s/%s".formatted(USER_ID, video.getId(), FILENAME));
    }

    @Test
    @DisplayName("Deve gerar identificadores distintos para cada video")
    void deveGerarIdentificadoresDistintos() {
      assertThat(novoVideo().getId()).isNotEqualTo(novoVideo().getId());
    }

    @Test
    @DisplayName("Deve recusar video sem usuario")
    void deveRecusarVideoSemUsuario() {
      assertThatThrownBy(() -> new Video(null, FILENAME))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("usuario");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Deve recusar nome de arquivo vazio")
    void deveRecusarNomeVazio(String nome) {
      assertThatThrownBy(() -> new Video(USER_ID, nome))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("vazio");
    }

    @Test
    @DisplayName("Deve recusar nome de arquivo nulo")
    void deveRecusarNomeNulo() {
      assertThatThrownBy(() -> new Video(USER_ID, null)).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pasta/aula.mp4", "pasta\\aula.mp4", "../../etc/passwd"})
    @DisplayName("Deve recusar nome que escape do diretorio do usuario no bucket")
    void deveRecusarNomeComCaminho(String nome) {
      assertThatThrownBy(() -> new Video(USER_ID, nome)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("Deve recusar nome de arquivo acima de 255 caracteres")
    void deveRecusarNomeMuitoLongo() {
      String nome = "a".repeat(256);

      assertThatThrownBy(() -> new Video(USER_ID, nome))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("255");
    }
  }

  @Nested
  @DisplayName("Transicoes de status")
  class Transicoes {

    @Test
    @DisplayName("Deve percorrer o caminho feliz ate COMPLETED")
    void devePercorrerCaminhoFeliz() {
      Video video = novoVideo();

      video.markAsQueued();
      assertThat(video.getStatus()).isEqualTo(VideoStatus.QUEUED);

      video.markAsProcessing();
      assertThat(video.getStatus()).isEqualTo(VideoStatus.PROCESSING);

      video.markAsCompleted("fiapx/outputs/user/video.zip", 42);
      assertThat(video.getStatus()).isEqualTo(VideoStatus.COMPLETED);
      assertThat(video.getZipKey()).isEqualTo("fiapx/outputs/user/video.zip");
      assertThat(video.getFrameCount()).isEqualTo(42);
      assertThat(video.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Deve registrar falha com mensagem e tentativas")
    void deveRegistrarFalha() {
      Video video = videoEmProcessamento();

      video.markAsFailed("ffmpeg exit code 1", 3);

      assertThat(video.getStatus()).isEqualTo(VideoStatus.FAILED);
      assertThat(video.getErrorMessage()).isEqualTo("ffmpeg exit code 1");
      assertThat(video.getAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("Deve preencher mensagem padrao quando a falha vem sem detalhe")
    void devePreencherMensagemPadrao() {
      Video video = videoEmProcessamento();

      video.markAsFailed(null, 1);

      assertThat(video.getErrorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("Deve truncar mensagem de erro no limite da coluna")
    void deveTruncarMensagemDeErro() {
      Video video = videoEmProcessamento();

      video.markAsFailed("x".repeat(2000), 1);

      assertThat(video.getErrorMessage()).hasSize(1000);
    }

    @Test
    @DisplayName("Nao deve regredir a contagem de tentativas")
    void naoDeveRegredirTentativas() {
      Video video = videoEmProcessamento();

      video.markAsFailed("primeira falha", 3);

      assertThat(video.getAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("Nao deve pular etapas do fluxo")
    void naoDevePularEtapas() {
      Video video = novoVideo();

      assertThatThrownBy(video::markAsProcessing)
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("Transicao de status invalida");
    }

    @Test
    @DisplayName("Nao deve sair de um status final")
    void naoDeveSairDeStatusFinal() {
      Video video = videoEmProcessamento();
      video.markAsCompleted("fiapx/outputs/user/video.zip", 10);

      assertThat(video.hasReachedFinalStatus()).isTrue();
      assertThatThrownBy(() -> video.markAsFailed("tarde demais", 1))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("Deve recusar conclusao sem chave do ZIP")
    void deveRecusarConclusaoSemZip() {
      Video video = videoEmProcessamento();

      assertThatThrownBy(() -> video.markAsCompleted(null, 10))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("ZIP");
    }

    @Test
    @DisplayName("Deve recusar conclusao com contagem de frames invalida")
    void deveRecusarConclusaoComFramesInvalidos() {
      Video video = videoEmProcessamento();

      assertThatThrownBy(() -> video.markAsCompleted("fiapx/outputs/user/video.zip", -1))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("frames");
    }
  }

  @Nested
  @DisplayName("Regras de acesso")
  class Acesso {

    @Test
    @DisplayName("Deve reconhecer apenas o proprio dono")
    void deveReconhecerDono() {
      Video video = novoVideo();

      assertThat(video.isOwnedBy(USER_ID)).isTrue();
      assertThat(video.isOwnedBy(OTHER_USER_ID)).isFalse();
    }

    @Test
    @DisplayName("Deve liberar download somente apos a conclusao")
    void deveLiberarDownloadSomenteAposConclusao() {
      Video video = novoVideo();
      assertThat(video.isDownloadable()).isFalse();

      video.markAsQueued();
      video.markAsProcessing();
      assertThat(video.isDownloadable()).isFalse();

      video.markAsCompleted("fiapx/outputs/user/video.zip", 10);
      assertThat(video.isDownloadable()).isTrue();
    }

    @Test
    @DisplayName("Nao deve liberar download de video que falhou")
    void naoDeveLiberarDownloadDeVideoComFalha() {
      Video video = videoEmProcessamento();

      video.markAsFailed("ffmpeg exit code 1", 3);

      assertThat(video.isDownloadable()).isFalse();
    }
  }
}
