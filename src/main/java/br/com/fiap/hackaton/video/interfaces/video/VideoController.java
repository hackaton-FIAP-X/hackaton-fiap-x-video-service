package br.com.fiap.hackaton.video.interfaces.video;

import br.com.fiap.hackaton.video.application.shared.exception.BusinessException;
import br.com.fiap.hackaton.video.application.video.dto.UploadVideoResponse;
import br.com.fiap.hackaton.video.application.video.dto.VideoUploadCommand;
import br.com.fiap.hackaton.video.application.video.service.VideoService;
import br.com.fiap.hackaton.video.infrastructure.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/videos")
@RequiredArgsConstructor
@Tag(name = "Videos", description = "Upload, acompanhamento e download de videos")
public class VideoController {

  private final VideoService videoService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Enviar video para processamento",
      description =
          "Grava o video no storage e devolve 202 imediatamente. "
              + "O processamento acontece de forma assincrona, atras da fila.")
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "Video aceito para processamento"),
    @ApiResponse(
        responseCode = "400",
        description = "Arquivo ausente, vazio ou em formato invalido"),
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido"),
    @ApiResponse(responseCode = "413", description = "Arquivo acima do tamanho maximo")
  })
  public ResponseEntity<UploadVideoResponse> upload(
      @CurrentUserId UUID userId, @RequestParam("file") MultipartFile file) {

    UploadVideoResponse response = videoService.upload(userId, toCommand(file));
    return ResponseEntity.accepted().body(response);
  }

  private VideoUploadCommand toCommand(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException("Arquivo de video e obrigatorio");
    }
    try {
      return new VideoUploadCommand(
          file.getOriginalFilename(), file.getSize(), file.getInputStream());
    } catch (IOException e) {
      throw new BusinessException("Nao foi possivel ler o arquivo enviado");
    }
  }
}
