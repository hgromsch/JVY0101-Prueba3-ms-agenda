package cl.respirarysoltar.agenda.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/** Elige el publicador según exista o no la URL de la cola (variable SQS_COLA_CITAS_URL). */
@Configuration
public class EventosConfig {

    @Bean
    public PublicadorEventos publicadorEventos(@Value("${rys.eventos.sqs.url:}") String urlCola,
                                               @Value("${rys.eventos.sqs.region:us-east-1}") String region,
                                               ObjectMapper objectMapper) {
        if (urlCola == null || urlCola.isBlank()) {
            return new LogPublicadorEventos();
        }
        SqsClient sqs = SqsClient.builder().region(Region.of(region)).build();
        return new SqsPublicadorEventos(sqs, urlCola, objectMapper);
    }
}
