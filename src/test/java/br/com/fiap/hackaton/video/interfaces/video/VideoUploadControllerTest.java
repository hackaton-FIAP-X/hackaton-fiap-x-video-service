package br.com.fiap.hackaton.video.interfaces.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.hackaton.video.domain.outbox.entity.OutboxEvent;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.gateway.EventPublishException;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoEventPublisher;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageGateway;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import br.com.fiap.hackaton.video.infrastructure.persistence.outbox.OutboxJpaRepository;
import br.com.fiap.hackaton.video.infrastructure.persistence.video.VideoJpaRepository;
import br.com.fiap.hackaton.video.infrastructure.security.JwtFixture;
import br.com.fiap.hackaton.video.infrastructure.security.SecurityTestConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityTestConfig.class)
class VideoUploadControllerTest {

  private static final UUID USER_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

  @Autowired private MockMvc mockMvc;
  @Autowired private VideoJpaRepository videoJpaRepository;
  @Autowired private OutboxJpaRepository outboxJpaRepository;

  @MockBean private VideoStorageGateway storageGateway;
  @MockBean private VideoEventPublisher eventPublisher;

  private String token() {
    return "Bearer " + JwtFixture.validToken(USER_ID);
  }

  private MockMultipartFile videoFile(String filename, byte[] content) {
    return new MockMultipartFile("file", filename, "video/mp4", content);
  }

  private void upload(String filename) throws Exception {
    mockMvc
        .perform(
            multipart("/videos")
                .file(videoFile(filename, "conteudo-do-video".getBytes()))
                .header(HttpHeaders.AUTHORIZATION, token()))
        .andExpect(status().isAccepted());
  }

  @BeforeEach
  void limparBase() {
    outboxJpaRepository.deleteAll();
    videoJpaRepository.deleteAll();
  }

  @Test
  @DisplayName("Deve aceitar o upload com 202 e status RECEIVED")
  void deveAceitarUpload() throws Exception {
    mockMvc
        .perform(
            multipart("/videos")
                .file(videoFile("aula-01.mp4", "conteudo-do-video".getBytes()))
                .header(HttpHeaders.AUTHORIZATION, token()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.videoId").isNotEmpty())
        .andExpect(jsonPath("$.status").value("RECEIVED"));
  }

  @Test
  @DisplayName("Deve persistir o video escopado ao dono do token")
  void devePersistirVideoDoDono() throws Exception {
    upload("aula-01.mp4");

    List<Video> persistidos = videoJpaRepository.findAll();
    assertThat(persistidos).hasSize(1);
    assertThat(persistidos.getFirst().getUserId()).isEqualTo(USER_ID);
    assertThat(persistidos.getFirst().getStorageKey())
        .startsWith("fiapx/inputs/%s/".formatted(USER_ID))
        .endsWith("/aula-01.mp4");
  }

  @Test
  @DisplayName("Deve gravar o evento no outbox e publica-lo na mesma requisicao")
  void deveGravarEPublicarEvento() throws Exception {
    upload("aula-01.mp4");

    List<OutboxEvent> eventos = outboxJpaRepository.findAll();
    assertThat(eventos).hasSize(1);
    assertThat(eventos.getFirst().getEventType()).isEqualTo("video.uploaded");
    assertThat(eventos.getFirst().isPublished()).isTrue();
    assertThat(eventos.getFirst().getPayload())
        .contains("\"storageKey\"", "\"originalFilename\":\"aula-01.mp4\"", "\"fps\":1")
        .contains("\"userId\":\"%s\"".formatted(USER_ID));

    assertThat(videoJpaRepository.findAll().getFirst().getStatus()).isEqualTo(VideoStatus.QUEUED);
  }

  @Test
  @DisplayName("Deve aceitar o upload e reter o evento quando o broker esta fora do ar")
  void deveReterEventoComBrokerForaDoAr() throws Exception {
    doThrow(new EventPublishException("broker fora do ar"))
        .when(eventPublisher)
        .publishConfirmed(any(OutboxEvent.class));

    upload("aula-01.mp4");

    List<OutboxEvent> eventos = outboxJpaRepository.findAll();
    assertThat(eventos).hasSize(1);
    assertThat(eventos.getFirst().isPublished()).isFalse();
    assertThat(eventos.getFirst().getAttempts()).isEqualTo(1);

    assertThat(videoJpaRepository.findAll().getFirst().getStatus()).isEqualTo(VideoStatus.RECEIVED);
  }

  @Test
  @DisplayName("Nao deve perder nenhum upload feito com o broker fora do ar")
  void naoDevePerderUploadsComBrokerForaDoAr() throws Exception {
    doThrow(new EventPublishException("broker fora do ar"))
        .when(eventPublisher)
        .publishConfirmed(any(OutboxEvent.class));

    for (int i = 1; i <= 5; i++) {
      upload("aula-0%d.mp4".formatted(i));
    }

    assertThat(videoJpaRepository.findAll()).hasSize(5);
    assertThat(outboxJpaRepository.countByPublishedAtIsNull()).isEqualTo(5);
  }

  @Test
  @DisplayName("Deve recusar upload sem token")
  void deveRecusarUploadSemToken() throws Exception {
    mockMvc
        .perform(multipart("/videos").file(videoFile("aula-01.mp4", "conteudo".getBytes())))
        .andExpect(status().isUnauthorized());

    assertThat(videoJpaRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Deve recusar formato que o worker nao processa")
  void deveRecusarFormatoInvalido() throws Exception {
    mockMvc
        .perform(
            multipart("/videos")
                .file(
                    new MockMultipartFile(
                        "file", "malware.exe", "application/octet-stream", "x".getBytes()))
                .header(HttpHeaders.AUTHORIZATION, token()))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("nao suportado")));

    assertThat(videoJpaRepository.findAll()).isEmpty();
    assertThat(outboxJpaRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Deve recusar arquivo vazio")
  void deveRecusarArquivoVazio() throws Exception {
    mockMvc
        .perform(
            multipart("/videos")
                .file(videoFile("aula-01.mp4", new byte[0]))
                .header(HttpHeaders.AUTHORIZATION, token()))
        .andExpect(status().isBadRequest());

    assertThat(videoJpaRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Deve recusar requisicao sem o campo file")
  void deveRecusarRequisicaoSemArquivo() throws Exception {
    mockMvc
        .perform(multipart("/videos").header(HttpHeaders.AUTHORIZATION, token()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Deve recusar nome de arquivo que tente escapar do diretorio do usuario")
  void deveRecusarPathTraversal() throws Exception {
    mockMvc
        .perform(
            multipart("/videos")
                .file(videoFile("../../outro-usuario/aula.mp4", "conteudo".getBytes()))
                .header(HttpHeaders.AUTHORIZATION, token()))
        .andExpect(status().isBadRequest());

    assertThat(videoJpaRepository.findAll()).isEmpty();
  }
}
