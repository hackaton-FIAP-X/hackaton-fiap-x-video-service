package br.com.fiap.hackaton.video.interfaces.video;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoEventPublisher;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageGateway;
import br.com.fiap.hackaton.video.domain.video.valueobject.StorageKey;
import br.com.fiap.hackaton.video.infrastructure.persistence.outbox.OutboxJpaRepository;
import br.com.fiap.hackaton.video.infrastructure.persistence.video.VideoJpaRepository;
import br.com.fiap.hackaton.video.infrastructure.security.JwtFixture;
import br.com.fiap.hackaton.video.infrastructure.security.SecurityTestConfig;
import java.time.Duration;
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
class VideoDownloadControllerTest {

  private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final String PRESIGNED_URL =
      "http://localhost:9000/fiapx/fiapx/outputs/alice/video.zip?X-Amz-Expires=300";

  @Autowired private MockMvc mockMvc;
  @Autowired private VideoJpaRepository videoJpaRepository;
  @Autowired private OutboxJpaRepository outboxJpaRepository;

  @MockBean private VideoStorageGateway storageGateway;
  @MockBean private VideoEventPublisher eventPublisher;

  private String tokenOf(UUID userId) {
    return "Bearer " + JwtFixture.validToken(userId);
  }

  private Video completedVideo(UUID owner) {
    Video video = new Video(owner, "aula.mp4");
    video.markAsQueued();
    video.markAsProcessing();
    video.markAsCompleted("fiapx/outputs/%s/%s.zip".formatted(owner, video.getId()), 120);
    return videoJpaRepository.save(video);
  }

  @BeforeEach
  void limparBase() {
    outboxJpaRepository.deleteAll();
    videoJpaRepository.deleteAll();
  }

  @Test
  @DisplayName("Deve redirecionar com 302 para a URL pre-assinada")
  void deveRedirecionarParaUrlPreAssinada() throws Exception {
    Video video = completedVideo(ALICE);
    when(storageGateway.presignedDownloadUrl(any(StorageKey.class), any(Duration.class)))
        .thenReturn(PRESIGNED_URL);

    mockMvc
        .perform(
            get("/videos/{id}/zip", video.getId())
                .header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, PRESIGNED_URL));
  }

  @Test
  @DisplayName("Deve responder 409 enquanto o processamento nao terminou")
  void deveResponder409ComProcessamentoPendente() throws Exception {
    Video video = videoJpaRepository.save(new Video(ALICE, "aula.mp4"));

    mockMvc
        .perform(
            get("/videos/{id}/zip", video.getId())
                .header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("RECEIVED")));

    verify(storageGateway, never()).presignedDownloadUrl(any(), any());
  }

  @Test
  @DisplayName("Deve responder 409 em video que falhou")
  void deveResponder409EmVideoComFalha() throws Exception {
    Video video = new Video(ALICE, "corrompido.mp4");
    video.markAsQueued();
    video.markAsProcessing();
    video.markAsFailed("ffmpeg exit code 1", 3);
    videoJpaRepository.save(video);

    mockMvc
        .perform(
            get("/videos/{id}/zip", video.getId())
                .header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Deve responder 404 no ZIP de outro usuario, sem assinar nada")
  void deveResponder404NoZipDeOutroUsuario() throws Exception {
    Video video = completedVideo(ALICE);

    mockMvc
        .perform(
            get("/videos/{id}/zip", video.getId()).header(HttpHeaders.AUTHORIZATION, tokenOf(BOB)))
        .andExpect(status().isNotFound());

    verify(storageGateway, never()).presignedDownloadUrl(any(), any());
  }

  @Test
  @DisplayName("Deve responder 404 em video inexistente")
  void deveResponder404EmVideoInexistente() throws Exception {
    mockMvc
        .perform(
            get("/videos/{id}/zip", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve recusar download sem token")
  void deveRecusarDownloadSemToken() throws Exception {
    Video video = completedVideo(ALICE);

    mockMvc.perform(get("/videos/{id}/zip", video.getId())).andExpect(status().isUnauthorized());

    verify(storageGateway, never()).presignedDownloadUrl(any(), any());
  }
}
