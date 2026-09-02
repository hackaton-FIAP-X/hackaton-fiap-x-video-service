package br.com.fiap.hackaton.video.domain.video.valueobject;

import br.com.fiap.hackaton.video.domain.shared.exception.DomainException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum VideoFormat {
  MP4("mp4", "video/mp4"),
  AVI("avi", "video/x-msvideo"),
  MOV("mov", "video/quicktime"),
  MKV("mkv", "video/x-matroska"),
  WEBM("webm", "video/webm");

  private final String extension;
  private final String contentType;

  VideoFormat(String extension, String contentType) {
    this.extension = extension;
    this.contentType = contentType;
  }

  public String extension() {
    return extension;
  }

  public String contentType() {
    return contentType;
  }

  public static VideoFormat fromFilename(String filename) {
    if (filename == null || filename.isBlank()) {
      throw new DomainException("Nome do arquivo nao pode ser vazio");
    }

    int separator = filename.lastIndexOf('.');
    if (separator < 0 || separator == filename.length() - 1) {
      throw new DomainException("Arquivo precisa ter extensao. Formatos aceitos: " + supported());
    }

    String extension = filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(format -> format.extension.equals(extension))
        .findFirst()
        .orElseThrow(
            () ->
                new DomainException(
                    "Formato de video nao suportado: %s. Formatos aceitos: %s"
                        .formatted(extension, supported())));
  }

  public static String supported() {
    return Arrays.stream(values()).map(VideoFormat::extension).collect(Collectors.joining(", "));
  }
}
