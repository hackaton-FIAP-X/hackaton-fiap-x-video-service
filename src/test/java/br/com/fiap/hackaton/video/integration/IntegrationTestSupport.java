package br.com.fiap.hackaton.video.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class IntegrationTestSupport {

  protected static final String BUCKET = "fiapx";

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("fiapx_video")
          .withUsername("fiapx")
          .withPassword("fiapx");

  private static final RabbitMQContainer RABBITMQ =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private static final MinIOContainer MINIO =
      new MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2024-09-13T20-26-02Z"))
          .withUserName("fiapx")
          .withPassword("fiapx12345");

  static {
    POSTGRES.start();
    RABBITMQ.start();
    REDIS.start();
    MINIO.start();
  }

  @DynamicPropertySource
  static void containerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

    registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
    registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);

    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

    registry.add("storage.endpoint", MINIO::getS3URL);
    registry.add("storage.public-endpoint", MINIO::getS3URL);
    registry.add("storage.access-key", MINIO::getUserName);
    registry.add("storage.secret-key", MINIO::getPassword);
    registry.add("storage.bucket", () -> BUCKET);
    registry.add("storage.region", () -> "us-east-1");
  }
}
