package cl.respirarysoltar.agenda.eventos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

/**
 * Productor de la cola SQS. Las credenciales las resuelve el SDK desde el rol de la
 * instancia EC2 (LabInstanceProfile), sin llaves en el código ni en la imagen.
 */
public class SqsPublicadorEventos implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(SqsPublicadorEventos.class);

    private final SqsClient sqs;
    private final String urlCola;
    private final ObjectMapper objectMapper;

    public SqsPublicadorEventos(SqsClient sqs, String urlCola, ObjectMapper objectMapper) {
        this.sqs = sqs;
        this.urlCola = urlCola;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publicar(EventoCita evento) {
        try {
            String cuerpo = objectMapper.writeValueAsString(evento);
            String messageId = sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(urlCola)
                    .messageBody(cuerpo)
                    .messageAttributes(Map.of("tipo", MessageAttributeValue.builder()
                            .dataType("String").stringValue(evento.tipo().name()).build()))
                    .build()).messageId();
            log.info("Evento {} de la cita {} publicado en SQS (messageId={})", evento.tipo(), evento.citaId(), messageId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + evento.eventoId(), e);
        }
    }
}
