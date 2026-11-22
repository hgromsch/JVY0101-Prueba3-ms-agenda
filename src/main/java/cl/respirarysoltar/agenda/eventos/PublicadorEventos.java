package cl.respirarysoltar.agenda.eventos;

/** Puerto de salida para publicar eventos de dominio (SQS en la nube, log en local). */
public interface PublicadorEventos {
    void publicar(EventoCita evento);
}
