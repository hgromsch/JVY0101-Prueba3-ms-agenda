package cl.respirarysoltar.agenda.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqsPublicadorEventosTest {

    @Test
    void envíaElEventoComoJsonALaColaConElTipoComoAtributo() {
        SqsClient sqs = mock(SqsClient.class);
        when(sqs.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("m-1").build());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String url = "https://sqs.us-east-1.amazonaws.com/123/rys-citas-eventos";
        EventoCita evento = new EventoCita(UUID.randomUUID(), TipoEventoCita.CITA_RESERVADA, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.of(2026, 12, 1, 10, 0), null, null, Instant.now());

        new SqsPublicadorEventos(sqs, url, mapper).publicar(evento);

        ArgumentCaptor<SendMessageRequest> enviado = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(enviado.capture());
        assertEquals(url, enviado.getValue().queueUrl());
        assertEquals("CITA_RESERVADA", enviado.getValue().messageAttributes().get("tipo").stringValue());
        assertTrue(enviado.getValue().messageBody().contains("\"horario\":\"2026-12-01T10:00:00\""));
    }
}
