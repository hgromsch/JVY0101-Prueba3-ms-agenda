package cl.respirarysoltar.agenda.eventos;

import cl.respirarysoltar.agenda.domain.Cita;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Evento de dominio de una cita. Se publica en la cola SQS rys-citas-eventos y lo consume
 * la función serverless fn-confirmacion-cita (comprobante en S3 + notificación al paciente).
 */
public record EventoCita(
        UUID eventoId,
        TipoEventoCita tipo,
        UUID citaId,
        UUID pacienteId,
        UUID psicologoId,
        LocalDateTime horario,
        LocalDateTime horarioAnterior,
        String motivo,
        Instant ocurridoEn
) {
    public static EventoCita de(TipoEventoCita tipo, Cita cita, LocalDateTime horarioAnterior, String motivo) {
        return new EventoCita(UUID.randomUUID(), tipo, cita.getId(), cita.getPacienteId(), cita.getPsicologoId(),
                cita.getHorario(), horarioAnterior, motivo, Instant.now());
    }
}
