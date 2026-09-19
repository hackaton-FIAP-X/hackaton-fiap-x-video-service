package br.com.fiap.hackaton.video.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

class StorageConfigTest {

  private final StorageConfig config = new StorageConfig();

  private static StorageProperties props(String endpoint, String accessKey, String secretKey) {
    return new StorageProperties(endpoint, null, accessKey, secretKey, "fiapx", "us-east-1");
  }

  @Test
  @DisplayName("com as duas chaves preenchidas usa credenciais fixas (MinIO local)")
  void chavesFixas() {
    assertThat(StorageConfig.credentials(props("http://minio:9000", "ak", "sk")))
        .isInstanceOf(StaticCredentialsProvider.class);
  }

  @Test
  @DisplayName("sem chaves usa a cadeia padrao da AWS (LabRole do no no Learner Lab)")
  void cadeiaPadrao() {
    assertThat(StorageConfig.credentials(props(null, "", "")))
        .isInstanceOf(DefaultCredentialsProvider.class);
    assertThat(StorageConfig.credentials(props(null, "ak", " ")))
        .isInstanceOf(DefaultCredentialsProvider.class);
  }

  @Test
  @DisplayName("cliente e presigner sobem com e sem endpoint customizado")
  void endpointOpcional() {
    config.s3Client(props("http://minio:9000", "ak", "sk")).close();
    config.s3Presigner(props("http://minio:9000", "ak", "sk")).close();
    config.s3Client(props("", "ak", "sk")).close();
    config.s3Presigner(props(null, "ak", "sk")).close();
  }

  @Test
  @DisplayName("presigner usa o endpoint publico quando informado")
  void endpointPublico() {
    var props =
        new StorageProperties(
            "http://minio:9000", "http://localhost:9000", "ak", "sk", "fiapx", "us-east-1");

    assertThat(props.publicEndpointOrDefault()).isEqualTo("http://localhost:9000");
    config.s3Presigner(props).close();
  }
}
