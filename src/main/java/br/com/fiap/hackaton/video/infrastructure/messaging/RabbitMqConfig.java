package br.com.fiap.hackaton.video.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

  public static final String VIDEO_EXCHANGE = "fiapx.video";
  public static final String VIDEO_DLX = "fiapx.video.dlx";

  public static final String PROCESSING_QUEUE = "video.processing";
  public static final String PROCESSING_DLQ = "video.processing.dlq";
  public static final String STATUS_QUEUE = "video.status";
  public static final String STATUS_DLQ = "video.status.dlq";

  public static final String UPLOADED_ROUTING_KEY = "video.uploaded";
  public static final String PROCESSED_ROUTING_KEY = "video.processed";
  public static final String FAILED_ROUTING_KEY = "video.failed";

  @Bean
  public TopicExchange videoExchange() {
    return new TopicExchange(VIDEO_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange videoDeadLetterExchange() {
    return new DirectExchange(VIDEO_DLX, true, false);
  }

  @Bean
  public Queue processingQueue() {
    return QueueBuilder.durable(PROCESSING_QUEUE)
        .quorum()
        .deadLetterExchange(VIDEO_DLX)
        .deadLetterRoutingKey(PROCESSING_QUEUE)
        .build();
  }

  @Bean
  public Queue processingDeadLetterQueue() {
    return QueueBuilder.durable(PROCESSING_DLQ).quorum().build();
  }

  @Bean
  public Binding processingBinding() {
    return BindingBuilder.bind(processingQueue()).to(videoExchange()).with(UPLOADED_ROUTING_KEY);
  }

  @Bean
  public Binding processingDeadLetterBinding() {
    return BindingBuilder.bind(processingDeadLetterQueue())
        .to(videoDeadLetterExchange())
        .with(PROCESSING_QUEUE);
  }

  @Bean
  public Queue statusQueue() {
    return QueueBuilder.durable(STATUS_QUEUE)
        .quorum()
        .deadLetterExchange(VIDEO_DLX)
        .deadLetterRoutingKey(STATUS_QUEUE)
        .build();
  }

  @Bean
  public Queue statusDeadLetterQueue() {
    return QueueBuilder.durable(STATUS_DLQ).quorum().build();
  }

  @Bean
  public Binding processedBinding() {
    return BindingBuilder.bind(statusQueue()).to(videoExchange()).with(PROCESSED_ROUTING_KEY);
  }

  @Bean
  public Binding failedBinding() {
    return BindingBuilder.bind(statusQueue()).to(videoExchange()).with(FAILED_ROUTING_KEY);
  }

  @Bean
  public Binding statusDeadLetterBinding() {
    return BindingBuilder.bind(statusDeadLetterQueue())
        .to(videoDeadLetterExchange())
        .with(STATUS_QUEUE);
  }
}
