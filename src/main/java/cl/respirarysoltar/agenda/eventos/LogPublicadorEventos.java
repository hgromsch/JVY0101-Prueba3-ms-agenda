package cl.respirarysoltar.agenda.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Publicador para desarrollo local (sin cola): deja el evento en el log. */
public class LogPublicadorEventos implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(LogPublicadorEventos.class);

    @Override
    public void publicar(EventoCita evento) {
        log.info("[sin SQS] Evento {} de la cita {} (configure SQS_COLA_CITAS_URL para publicarlo)",
                evento.tipo(), evento.citaId());
    }
}
