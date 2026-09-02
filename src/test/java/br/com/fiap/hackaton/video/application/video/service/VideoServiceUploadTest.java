package br.com.fiap.hackaton.video.application.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.hackaton.video.application.outbox.service.OutboxDispatcher;
import br.com.fiap.hackaton.video.application.shared.exception.BusinessException;
import br.com.fiap.hackaton.video.application.video.dto.UploadVideoResponse;
import br.com.fiap.hackaton.video.application.video.dto.VideoUploadCommand;
import br.com.fiap.hackaton.video.domain.shared.exception.DomainException;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.gateway.VideoStorageGateway;
import br.com.fiap.hackaton.video.domain.video.valueobject.StorageKey;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class VideoServiceUploadTest {

  private static final UUID USER_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
  private static final DataSize MAX_SIZE = DataSize.ofMegabytes(500);

  @Mock private VideoStorageGateway storageGateway;
  @Mock private VideoRegistrationService registrationService;
  @Mock private OutboxDispatcher outboxDispatcher;

  private VideoService videoService;

  @BeforeEach
  void setUp() {
    videoService =
        new VideoService(storageGateway, registrationService, outboxDispatcher, MAX_SIZE);
  }

  private VideoUploadCommand command(String filename, long size) {
    InputStream content = new ByteArrayInputStream(new byte[] {1, 2, 3});
    return new VideoUploadCommand(filename, size, content);
  }

  private void registrationEchoesEntity() {
    when(registrationService.registerReceived(any(Video.class), anyString()))
        .thenAnswer(call -> call.getArgument(0));
  }

  @Test
  @DisplayName("Deve aceitar o upload e devolver o video em RECEIVED")
  void deveAceitarUpload() {
    registrationEchoesEntity();

    UploadVideoResponse response = videoService.upload(USER_ID, command("aula.mp4", 2_048));

    assertThat(response.videoId()).isNotNull();
    assertThat(response.status()).isEqualTo(VideoStatus.RECEIVED);
  }

  @Test
  @DisplayName("Deve gravar no storage com a chave e o content type do contrato")
  void deveGravarNoStorageComChaveDoContrato() {
    registrationEchoesEntity();

    UploadVideoResponse response = videoService.upload(USER_ID, command("aula.mp4", 2_048));

    ArgumentCaptor<StorageKey> key = ArgumentCaptor.forClass(StorageKey.class);
    ArgumentCaptor<String> contentType = ArgumentCaptor.forClass(String.class);
    verify(storageGateway)
        .store(key.capture(), any(InputStream.class), anyLong(), contentType.capture());

    assertThat(key.getValue().value())
        .isEqualTo("fiapx/inputs/%s/%s/aula.mp4".formatted(USER_ID, response.videoId()));
    assertThat(contentType.getValue()).isEqualTo("video/mp4");
  }

  @Test
  @DisplayName("Deve gravar no storage antes de registrar, para nao deixar linha sem arquivo")
  void deveGravarNoStorageAntesDeRegistrar() {
    registrationEchoesEntity();

    videoService.upload(USER_ID, command("aula.mp4", 2_048));

    InOrder ordem = inOrder(storageGateway, registrationService, outboxDispatcher);
    ordem.verify(storageGateway).store(any(), any(), anyLong(), anyString());
    ordem.verify(registrationService).registerReceived(any(Video.class), anyString());
    ordem.verify(outboxDispatcher).dispatchPending();
  }

  @Test
  @DisplayName("Deve registrar o video com o dono do token e um traceId")
  void deveRegistrarComDonoDoToken() {
    registrationEchoesEntity();

    videoService.upload(USER_ID, command("aula.mp4", 2_048));

    ArgumentCaptor<Video> video = ArgumentCaptor.forClass(Video.class);
    ArgumentCaptor<String> traceId = ArgumentCaptor.forClass(String.class);
    verify(registrationService).registerReceived(video.capture(), traceId.capture());

    assertThat(video.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(video.getValue().getStatus()).isEqualTo(VideoStatus.RECEIVED);
    assertThat(video.getValue().getOriginalFilename()).isEqualTo("aula.mp4");
    assertThat(traceId.getValue()).isNotBlank();
  }

  @Test
  @DisplayName("Deve responder ao usuario mesmo quando a publicacao imediata falha")
  void deveResponderMesmoComBrokerForaDoAr() {
    registrationEchoesEntity();
    doThrow(new IllegalStateException("broker fora do ar"))
        .when(outboxDispatcher)
        .dispatchPending();

    UploadVideoResponse response = videoService.upload(USER_ID, command("aula.mp4", 2_048));

    assertThat(response.status()).isEqualTo(VideoStatus.RECEIVED);
    verify(registrationService).registerReceived(any(Video.class), anyString());
  }

  @Test
  @DisplayName("Deve recusar formato nao suportado sem tocar no storage")
  void deveRecusarFormatoNaoSuportado() {
    assertThatThrownBy(() -> videoService.upload(USER_ID, command("malware.exe", 2_048)))
        .isInstanceOf(DomainException.class);

    verify(storageGateway, never()).store(any(), any(), anyLong(), anyString());
    verify(registrationService, never()).registerReceived(any(), anyString());
  }

  @Test
  @DisplayName("Deve recusar arquivo vazio sem tocar no storage")
  void deveRecusarArquivoVazio() {
    assertThatThrownBy(() -> videoService.upload(USER_ID, command("aula.mp4", 0)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("vazio");

    verify(storageGateway, never()).store(any(), any(), anyLong(), anyString());
  }

  @Test
  @DisplayName("Deve recusar arquivo acima do limite configurado")
  void deveRecusarArquivoAcimaDoLimite() {
    long acimaDoLimite = MAX_SIZE.toBytes() + 1;

    assertThatThrownBy(() -> videoService.upload(USER_ID, command("aula.mp4", acimaDoLimite)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("500 MB");

    verify(storageGateway, never()).store(any(), any(), anyLong(), anyString());
  }

  @Test
  @DisplayName("Deve aceitar arquivo exatamente no limite")
  void deveAceitarArquivoNoLimite() {
    registrationEchoesEntity();

    UploadVideoResponse response =
        videoService.upload(USER_ID, command("aula.mp4", MAX_SIZE.toBytes()));

    assertThat(response.status()).isEqualTo(VideoStatus.RECEIVED);
  }

  @Test
  @DisplayName("Nao deve registrar o video quando o storage falha")
  void naoDeveRegistrarQuandoStorageFalha() {
    doThrow(new IllegalStateException("minio fora do ar"))
        .when(storageGateway)
        .store(any(), any(), anyLong(), anyString());

    assertThatThrownBy(() -> videoService.upload(USER_ID, command("aula.mp4", 2_048)))
        .isInstanceOf(IllegalStateException.class);

    verify(registrationService, never()).registerReceived(any(), anyString());
  }
}
