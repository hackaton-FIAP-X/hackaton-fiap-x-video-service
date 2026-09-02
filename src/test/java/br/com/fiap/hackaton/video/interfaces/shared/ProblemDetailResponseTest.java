package br.com.fiap.hackaton.video.interfaces.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityTestConfig.class)
class ProblemDetailResponseTest {

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

  @BeforeEach
  void limparBase() {
    outboxJpaRepository.deleteAll();
    videoJpaRepository.deleteAll();
  }

  @Test
  @DisplayName("Deve responder 404 em application/problem+json com os campos da RFC 7807")
  void deveResponder404ComProblemDetail() throws Exception {
    UUID inexistente = UUID.randomUUID();

    mockMvc
        .perform(get("/videos/{id}", inexistente).header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://fiapx.com.br/problems/resource-not-found"))
        .andExpect(jsonPath("$.title").value("Recurso nao encontrado"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").exists())
        .andExpect(jsonPath("$.instance").value("/videos/" + inexistente))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("Deve responder 409 com o type de video ainda nao disponivel")
  void deveResponder409ComProblemDetail() throws Exception {
    Video video = videoJpaRepository.save(new Video(ALICE, "aula.mp4"));

    mockMvc
        .perform(
            get("/videos/{id}/zip", video.getId())
                .header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://fiapx.com.br/problems/video-not-ready"))
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  @DisplayName("Deve responder 400 com o type de requisicao invalida")
  void deveResponder400ComProblemDetail() throws Exception {
    mockMvc
        .perform(
            multipart("/videos")
                .file(
                    new MockMultipartFile(
                        "file", "malware.exe", "application/octet-stream", "x".getBytes()))
                .header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://fiapx.com.br/problems/invalid-request"))
        .andExpect(jsonPath("$.title").value("Requisicao invalida"));
  }

  @Test
  @DisplayName("Deve responder 400 com problem+json em status inexistente na listagem")
  void deveResponder400EmStatusInvalido() throws Exception {
    mockMvc
        .perform(
            get("/videos")
                .param("status", "INVENTADO")
                .header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Parametro invalido"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("status")));
  }

  @Test
  @DisplayName("Deve responder 401 em problem+json, e nao com corpo vazio do Spring Security")
  void deveResponder401ComProblemDetail() throws Exception {
    mockMvc
        .perform(get("/videos"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://fiapx.com.br/problems/unauthorized"))
        .andExpect(jsonPath("$.title").value("Nao autenticado"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.instance").value("/videos"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("Deve responder 401 em problem+json tambem com token invalido")
  void deveResponder401ComTokenInvalido() throws Exception {
    mockMvc
        .perform(get("/videos").header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-um-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://fiapx.com.br/problems/unauthorized"));
  }

  @Test
  @DisplayName("Nao deve vazar existencia: 404 de video alheio e identico ao de inexistente")
  void naoDeveVazarExistenciaNoProblemDetail() throws Exception {
    Video videoDaAlice = videoJpaRepository.save(new Video(ALICE, "alice.mp4"));

    mockMvc
        .perform(
            get("/videos/{id}", videoDaAlice.getId())
                .header(HttpHeaders.AUTHORIZATION, tokenOf(BOB)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://fiapx.com.br/problems/resource-not-found"))
        .andExpect(jsonPath("$.title").value("Recurso nao encontrado"));

    mockMvc
        .perform(
            get("/videos/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, tokenOf(BOB)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://fiapx.com.br/problems/resource-not-found"))
        .andExpect(jsonPath("$.title").value("Recurso nao encontrado"));
  }

  @Test
  @DisplayName("Deve responder 405 em problem+json para metodo nao suportado")
  void deveResponder405ComProblemDetail() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/videos")
                .header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
