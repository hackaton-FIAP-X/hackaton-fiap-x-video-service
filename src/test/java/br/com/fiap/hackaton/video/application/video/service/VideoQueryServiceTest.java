package br.com.fiap.hackaton.video.application.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import br.com.fiap.hackaton.video.application.shared.exception.ResourceNotFoundException;
import br.com.fiap.hackaton.video.application.video.dto.VideoPageResponse;
import br.com.fiap.hackaton.video.application.video.dto.VideoResponse;
import br.com.fiap.hackaton.video.domain.video.entity.Video;
import br.com.fiap.hackaton.video.domain.video.repository.VideoRepository;
import br.com.fiap.hackaton.video.domain.video.valueobject.VideoStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class VideoQueryServiceTest {

  private static final UUID USER_ID = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

  @Mock private VideoRepository videoRepository;

  private VideoQueryService videoQueryService;

  @BeforeEach
  void setUp() {
    videoQueryService = new VideoQueryService(videoRepository);
  }

  private void repositoryReturnsEmptyPage() {
    when(videoRepository.findAllByOwner(any(), any(), any()))
        .thenAnswer(call -> new PageImpl<Video>(List.of(), call.getArgument(2), 0));
  }

  private Pageable capturedPageable() {
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    org.mockito.Mockito.verify(videoRepository).findAllByOwner(any(), any(), pageable.capture());
    return pageable.getValue();
  }

  @Test
  @DisplayName("Deve consultar sempre escopado ao dono do token")
  void deveConsultarEscopadoAoDono() {
    repositoryReturnsEmptyPage();

    videoQueryService.listOwnedBy(USER_ID, null, 0, 20);

    org.mockito.Mockito.verify(videoRepository).findAllByOwner(eq(USER_ID), eq(null), any());
  }

  @Test
  @DisplayName("Deve ordenar por data de criacao decrescente, como o indice do banco")
  void deveOrdenarPelosMaisRecentes() {
    repositoryReturnsEmptyPage();

    videoQueryService.listOwnedBy(USER_ID, null, 0, 20);

    assertThat(capturedPageable().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  @DisplayName("Deve repassar o filtro de status quando informado")
  void deveRepassarFiltroDeStatus() {
    repositoryReturnsEmptyPage();

    videoQueryService.listOwnedBy(USER_ID, VideoStatus.COMPLETED, 0, 20);

    org.mockito.Mockito.verify(videoRepository)
        .findAllByOwner(eq(USER_ID), eq(VideoStatus.COMPLETED), any());
  }

  @Test
  @DisplayName("Deve limitar o tamanho de pagina para o cliente nao pedir a base inteira")
  void deveLimitarTamanhoDePagina() {
    repositoryReturnsEmptyPage();

    videoQueryService.listOwnedBy(USER_ID, null, 0, 100_000);

    assertThat(capturedPageable().getPageSize()).isEqualTo(VideoQueryService.MAX_PAGE_SIZE);
  }

  @Test
  @DisplayName("Deve usar o tamanho padrao quando o cliente pede zero ou negativo")
  void deveUsarTamanhoPadrao() {
    repositoryReturnsEmptyPage();

    videoQueryService.listOwnedBy(USER_ID, null, 0, 0);

    assertThat(capturedPageable().getPageSize()).isEqualTo(VideoQueryService.DEFAULT_PAGE_SIZE);
  }

  @Test
  @DisplayName("Deve tratar pagina negativa como a primeira")
  void deveTratarPaginaNegativa() {
    repositoryReturnsEmptyPage();

    videoQueryService.listOwnedBy(USER_ID, null, -5, 20);

    assertThat(capturedPageable().getPageNumber()).isZero();
  }

  @Test
  @DisplayName("Deve devolver o total de elementos junto com a pagina")
  void deveDevolverTotalDeElementos() {
    Video video = new Video(USER_ID, "aula.mp4");
    when(videoRepository.findAllByOwner(any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(video), PageRequest.of(0, 20), 42));

    VideoPageResponse response = videoQueryService.listOwnedBy(USER_ID, null, 0, 20);

    assertThat(response.content()).hasSize(1);
    assertThat(response.totalElements()).isEqualTo(42);
    assertThat(response.page()).isZero();
    assertThat(response.size()).isEqualTo(20);
  }

  @Test
  @DisplayName("Deve devolver o detalhe do video do dono")
  void deveDevolverDetalheDoDono() {
    Video video = new Video(USER_ID, "aula.mp4");
    when(videoRepository.findByIdAndUserId(video.getId(), USER_ID)).thenReturn(Optional.of(video));

    VideoResponse response = videoQueryService.findOwnedBy(USER_ID, video.getId());

    assertThat(response.videoId()).isEqualTo(video.getId());
    assertThat(response.originalFilename()).isEqualTo("aula.mp4");
    assertThat(response.status()).isEqualTo(VideoStatus.RECEIVED);
    assertThat(response.downloadAvailable()).isFalse();
  }

  @Test
  @DisplayName("Deve tratar video de outro usuario como inexistente")
  void deveTratarVideoDeOutroUsuarioComoInexistente() {
    UUID videoDeOutro = UUID.randomUUID();
    when(videoRepository.findByIdAndUserId(videoDeOutro, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> videoQueryService.findOwnedBy(USER_ID, videoDeOutro))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
