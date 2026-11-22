package cl.respirarysoltar.agenda.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica el evento recién DESPUÉS del commit: si la transacción se revierte, no sale ningún
 * mensaje a la cola (no se notifica una cita que no existe). Un fallo de SQS no revierte la
 * reserva: la cita ya está guardada y el error queda en el log para reintento/alerta.
 */
@Component
public class EventosCitaListener {

    private static final Logger log = LoggerFactory.getLogger(EventosCitaListener.class);

    private final PublicadorEventos publicador;

    public EventosCitaListener(PublicadorEventos publicador) {
        this.publicador = publicador;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alConfirmarTransaccion(EventoCita evento) {
        try {
            publicador.publicar(evento);
        } catch (RuntimeException e) {
            log.error("No se pudo publicar el evento {} de la cita {}: {}", evento.tipo(), evento.citaId(), e.getMessage());
        }
    }
}
