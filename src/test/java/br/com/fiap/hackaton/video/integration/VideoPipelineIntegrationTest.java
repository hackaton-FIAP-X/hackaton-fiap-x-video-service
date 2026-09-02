package br.com.fiap.hackaton.video.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.hackaton.video.application.video.dto.VideoFailedEvent;
import br.com.fiap.hackaton.video.application.video.dto.VideoProcessedEvent;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import br.com.fiap.hackaton.video.infrastructure.messaging.RabbitMqConfig;
import br.com.fiap.hackaton.video.infrastructure.persistence.outbox.OutboxJpaRepository;
import br.com.fiap.hackaton.video.infrastructure.persistence.video.VideoJpaRepository;
import br.com.fiap.hackaton.video.infrastructure.security.JwtFixture;
import br.com.fiap.hackaton.video.infrastructure.security.SecurityTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Import(SecurityTestConfig.class)
class VideoPipelineIntegrationTest extends IntegrationTestSupport {

  private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private VideoJpaRepository videoJpaRepository;
  @Autowired private OutboxJpaRepository outboxJpaRepository;
  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private S3Client s3Client;
  @Autowired private ObjectMapper objectMapper;

  private String tokenOf(UUID userId) {
    return "Bearer " + JwtFixture.validToken(userId);
  }

  @BeforeEach
  void prepararAmbiente() {
    createBucketIfMissing();
    drainQueue(RabbitMqConfig.PROCESSING_QUEUE);
    outboxJpaRepository.deleteAll();
    videoJpaRepository.deleteAll();
  }

  private void createBucketIfMissing() {
    try {
      s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    } catch (S3Exception alreadyExists) {
      return;
    }
  }

  private void drainQueue(String queue) {
    while (rabbitTemplate.receive(queue) != null) {
      continue;
    }
  }

  private UUID upload(UUID owner, String filename) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart("/videos")
                    .file(
                        new MockMultipartFile(
                            "file", filename, "video/mp4", "conteudo-real".getBytes()))
                    .header(HttpHeaders.AUTHORIZATION, tokenOf(owner)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("RECEIVED"))
            .andReturn();

    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("videoId").asText());
  }

  private void publishToExchange(String routingKey, Object event) throws Exception {
    Message message =
        MessageBuilder.withBody(objectMapper.writeValueAsBytes(event))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitTemplate.send(RabbitMqConfig.VIDEO_EXCHANGE, routingKey, message);
  }

  private void awaitStatus(UUID videoId, VideoStatus expected) {
    await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(300))
        .untilAsserted(
            () ->
                assertThat(videoJpaRepository.findById(videoId))
                    .get()
                    .extracting(Video::getStatus)
                    .isEqualTo(expected));
  }

  @Test
  @DisplayName("Upload deve gravar no MinIO, publicar na fila e deixar o video em QUEUED")
  void uploadDeveChegarAteAFila() throws Exception {
    UUID videoId = upload(ALICE, "aula-01.mp4");

    Video persistido = videoJpaRepository.findById(videoId).orElseThrow();
    assertThat(persistido.getStorageKey())
        .isEqualTo("fiapx/inputs/%s/%s/aula-01.mp4".formatted(ALICE, videoId));

    assertThat(objectExists(persistido.getStorageKey())).isTrue();

    assertThat(outboxJpaRepository.findAll())
        .singleElement()
        .satisfies(
            evento -> {
              assertThat(evento.getEventType()).isEqualTo("video.uploaded");
              assertThat(evento.isPublished()).isTrue();
            });

    Message naFila = rabbitTemplate.receive(RabbitMqConfig.PROCESSING_QUEUE, 10_000);
    assertThat(naFila).isNotNull();
    String payload = new String(naFila.getBody(), StandardCharsets.UTF_8);
    assertThat(payload)
        .contains("\"videoId\":\"%s\"".formatted(videoId))
        .contains("\"userId\":\"%s\"".formatted(ALICE))
        .contains("\"originalFilename\":\"aula-01.mp4\"")
        .contains("\"fps\":1")
        .contains("\"traceId\"");
    assertThat(naFila.getMessageProperties().getReceivedRoutingKey()).isEqualTo("video.uploaded");

    awaitStatus(videoId, VideoStatus.QUEUED);
  }

  @Test
  @DisplayName("video.processed deve concluir o video e liberar o download pre-assinado")
  void processedDeveConcluirELiberarDownload() throws Exception {
    UUID videoId = upload(ALICE, "aula-02.mp4");
    awaitStatus(videoId, VideoStatus.QUEUED);

    String zipKey = "fiapx/outputs/%s/%s.zip".formatted(ALICE, videoId);
    s3Client.putObject(
        PutObjectRequest.builder().bucket(BUCKET).key(zipKey).build(),
        RequestBody.fromBytes("conteudo-do-zip".getBytes(StandardCharsets.UTF_8)));

    publishToExchange(
        "video.processed",
        new VideoProcessedEvent(videoId, ALICE, zipKey, 120, LocalDateTime.now(), "trace-it"));

    awaitStatus(videoId, VideoStatus.COMPLETED);

    mockMvc
        .perform(get("/videos/{id}", videoId).header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.frameCount").value(120))
        .andExpect(jsonPath("$.downloadAvailable").value(true));

    MvcResult redirect =
        mockMvc
            .perform(
                get("/videos/{id}/zip", videoId).header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
            .andExpect(status().isFound())
            .andReturn();

    String presignedUrl = redirect.getResponse().getHeader(HttpHeaders.LOCATION);
    assertThat(presignedUrl).contains("X-Amz-Signature").contains("X-Amz-Expires=300");
    assertThat(downloadBody(presignedUrl)).isEqualTo("conteudo-do-zip");
  }

  @Test
  @DisplayName("video.failed deve marcar a falha e manter o download bloqueado")
  void failedDeveBloquearDownload() throws Exception {
    UUID videoId = upload(ALICE, "corrompido.mp4");
    awaitStatus(videoId, VideoStatus.QUEUED);

    publishToExchange(
        "video.failed",
        new VideoFailedEvent(videoId, ALICE, "FFMPEG_ERROR", "moov atom not found", 3, "trace-it"));

    awaitStatus(videoId, VideoStatus.FAILED);

    mockMvc
        .perform(get("/videos/{id}", videoId).header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errorMessage").value("[FFMPEG_ERROR] moov atom not found"))
        .andExpect(jsonPath("$.downloadAvailable").value(false));

    mockMvc
        .perform(get("/videos/{id}/zip", videoId).header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Reentrega do mesmo video.processed nao pode alterar a linha")
  void reentregaDeveSerIdempotente() throws Exception {
    UUID videoId = upload(ALICE, "aula-03.mp4");
    awaitStatus(videoId, VideoStatus.QUEUED);

    String zipKey = "fiapx/outputs/%s/%s.zip".formatted(ALICE, videoId);
    VideoProcessedEvent event =
        new VideoProcessedEvent(videoId, ALICE, zipKey, 42, LocalDateTime.now(), "trace-it");

    publishToExchange("video.processed", event);
    awaitStatus(videoId, VideoStatus.COMPLETED);
    LocalDateTime primeiraAtualizacao =
        videoJpaRepository.findById(videoId).orElseThrow().getUpdatedAt();

    publishToExchange("video.processed", event);
    publishToExchange("video.processed", event);
    publishToExchange(
        "video.failed",
        new VideoFailedEvent(videoId, ALICE, "TARDE", "chegou tarde", 1, "trace-it"));

    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              Video atual = videoJpaRepository.findById(videoId).orElseThrow();
              assertThat(atual.getStatus()).isEqualTo(VideoStatus.COMPLETED);
              assertThat(atual.getFrameCount()).isEqualTo(42);
              assertThat(atual.getErrorMessage()).isNull();
              assertThat(atual.getUpdatedAt()).isEqualTo(primeiraAtualizacao);
            });
  }

  @Test
  @DisplayName("Listagem com Redis real deve refletir upload novo, sem servir dado velho")
  void listagemDeveRefletirUploadNovo() throws Exception {
    upload(ALICE, "aula-04.mp4");

    mockMvc
        .perform(get("/videos").header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    upload(ALICE, "aula-05.mp4");

    mockMvc
        .perform(get("/videos").header(HttpHeaders.AUTHORIZATION, tokenOf(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName("Um usuario nao enxerga o video do outro nem no banco real")
  void deveIsolarUsuariosNoBancoReal() throws Exception {
    UUID videoDaAlice = upload(ALICE, "alice.mp4");
    upload(BOB, "bob.mp4");

    mockMvc
        .perform(get("/videos").header(HttpHeaders.AUTHORIZATION, tokenOf(BOB)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].originalFilename").value("bob.mp4"));

    mockMvc
        .perform(get("/videos/{id}", videoDaAlice).header(HttpHeaders.AUTHORIZATION, tokenOf(BOB)))
        .andExpect(status().isNotFound());
  }

  private boolean objectExists(String key) {
    try {
      s3Client.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build());
      return true;
    } catch (S3Exception notFound) {
      return false;
    }
  }

  private String downloadBody(String url) throws IOException, InterruptedException {
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body();
  }
}
