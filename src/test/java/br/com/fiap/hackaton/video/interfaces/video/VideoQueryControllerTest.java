package br.com.fiap.hackaton.video.interfaces.video;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoEventPublisher;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageGateway;
import br.com.fiap.hackaton.video.infrastructure.persistence.outbox.OutboxJpaRepository;
import br.com.fiap.hackaton.video.infrastructure.persistence.video.VideoJpaRepository;
import br.com.fiap.hackaton.video.infrastructure.security.JwtFixture;
import br.com.fiap.hackaton.video.infrastructure.security.SecurityTestConfig;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityTestConfig.class)
class VideoQueryControllerTest {

  private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private VideoJpaRepository videoJpaRepository;
  @Autowired private OutboxJpaRepository outboxJpaRepository;

  @MockBean private VideoStorageGateway storageGateway;
  @MockBean private VideoEventPublisher eventPublisher;

  private String tokenOf(UUID userId) {
    return "Bearer " + JwtFixture.validToken(userId);
  }

  private Video persistedVideo(UUID owner, String filename) {
    return videoJpaRepository.save(new Video(owner, filename));
  }

  private Video completedVideo(UUID owner, String filename) {
    Video video = new Video(owner, filename);
    video.markAsQueued();
    video.markAsProcessing();
    video.markAsCompleted("fiapx/outputs/%s/%s.zip".formatted(owner, video.getId()), 120);
    return videoJpaRepository.save(video);
  }

  private Video failedVideo(UUID owner, String filename) {
    Video video = new Video(owner, filename);
    video.markAsQueued();
    video.markAsProcessing();
    video.markAsFailed("ffmpeg exit code 1", 3);
    return videoJpaRepository.save(video);
  }

  @BeforeEach
  void limparBase() {
    outboxJpaRepository.deleteAll();
    videoJpaRepository.deleteAll();
  }

  @Test
  @DisplayName("Deve listar apenas os videos do dono do token")
  void deveListarApenasVideosDoDono() throws Exception {
    persistedVideo(ALICE, "alice-01.mp4");
    persistedVideo(ALICE, "alice-02.mp4");
    persistedVideo(BOB, "bob-01.mp4");

    mockMvc
        .perform(get("/videos").header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(
            jsonPath("$.content[*].originalFilename")
                .value(org.hamcrest.Matchers.containsInAnyOrder("alice-01.mp4", "alice-02.mp4")));
  }

  @Test
  @DisplayName("Deve devolver lista vazia para usuario sem videos")
  void deveDevolverListaVaziaParaUsuarioSemVideos() throws Exception {
    persistedVideo(ALICE, "alice-01.mp4");

    mockMvc
        .perform(get("/videos").header(HttpHeaders.AUTHORIZATION, tokenOf(BOB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @DisplayName("Deve filtrar a listagem por status")
  void deveFiltrarPorStatus() throws Exception {
    persistedVideo(ALICE, "recebido.mp4");
    completedVideo(ALICE, "concluido.mp4");
    failedVideo(ALICE, "falhou.mp4");

    mockMvc
        .perform(
            get("/videos")
                .param("status", "COMPLETED")
                .header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].originalFilename").value("concluido.mp4"));
  }

  @Test
  @DisplayName("Deve paginar a listagem")
  void devePaginarListagem() throws Exception {
    for (int i = 1; i <= 5; i++) {
      persistedVideo(ALICE, "aula-0%d.mp4".formatted(i));
    }

    mockMvc
        .perform(
            get("/videos")
                .param("page", "0")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  @DisplayName("Deve recusar status inexistente com 400")
  void deveRecusarStatusInvalido() throws Exception {
    mockMvc
        .perform(
            get("/videos")
                .param("status", "INVENTADO")
                .header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Deve recusar listagem sem token")
  void deveRecusarListagemSemToken() throws Exception {
    mockMvc.perform(get("/videos")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Deve devolver o detalhe do proprio video")
  void deveDevolverDetalheDoProprioVideo() throws Exception {
    Video video = completedVideo(ALICE, "aula-01.mp4");

    mockMvc
        .perform(
            get("/videos/{id}", video.getId()).header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.videoId").value(video.getId().toString()))
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.frameCount").value(120))
        .andExpect(jsonPath("$.downloadAvailable").value(true));
  }

  @Test
  @DisplayName("Deve expor a mensagem de erro no detalhe de um video que falhou")
  void deveExporMensagemDeErro() throws Exception {
    Video video = failedVideo(ALICE, "corrompido.mp4");

    mockMvc
        .perform(
            get("/videos/{id}", video.getId()).header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.errorMessage").value("ffmpeg exit code 1"))
        .andExpect(jsonPath("$.downloadAvailable").value(false));
  }

  @Test
  @DisplayName("Deve responder 404, e nao 403, no video de outro usuario")
  void deveResponder404NoVideoDeOutroUsuario() throws Exception {
    Video videoDaAlice = persistedVideo(ALICE, "alice-01.mp4");

    mockMvc
        .perform(
            get("/videos/{id}", videoDaAlice.getId())
                .header(HttpHeaders.AUTHORIZATION, tokenOf(BOB)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve responder 404 igual para video inexistente e video alheio")
  void deveResponderIgualParaInexistenteEAlheio() throws Exception {
    Video videoDaAlice = persistedVideo(ALICE, "alice-01.mp4");
    UUID inexistente = UUID.randomUUID();

    mockMvc
        .perform(
            get("/videos/{id}", videoDaAlice.getId())
                .header(HttpHeaders.AUTHORIZATION, tokenOf(BOB)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Not Found"));

    mockMvc
        .perform(get("/videos/{id}", inexistente).header(HttpHeaders.AUTHORIZATION, tokenOf(BOB)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Not Found"));
  }

  @Test
  @DisplayName("Deve recusar detalhe sem token")
  void deveRecusarDetalheSemToken() throws Exception {
    Video video = persistedVideo(ALICE, "alice-01.mp4");

    mockMvc.perform(get("/videos/{id}", video.getId())).andExpect(status().isUnauthorized());
  }
}
