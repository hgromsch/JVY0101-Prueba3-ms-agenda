# ms-agenda

Agendamiento, reprogramación y cancelación de citas. Cubre **RF-04 y RF-05**. Protege el
invariante de negocio más crítico del sistema: **nunca más de un paciente por horario con
el mismo psicólogo**. La reserva se hace de forma atómica en Redis (`SETNX` + TTL) con doble
verificación en la base de datos; ver `CitaService.reservar`.

**Puerto:** 8083 · **Base de datos:** `agenda_db` · **Swagger:** http://localhost:8083/swagger-ui.html

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/citas` | Agenda una cita (201; 409 si el horario está tomado) |
| GET | `/api/citas?pacienteId=&psicologoId=&estado=` | Lista citas con filtros opcionales |
| GET | `/api/citas/{id}` | Obtiene una cita (404 si no existe) |
| POST | `/api/citas/{id}/cancelar` | Cancela la cita y libera el slot |
| POST | `/api/citas/{id}/reprogramar` | Reprograma a un nuevo horario (re-reserva atómica) |
| GET | `/api/citas/{id}/historial` | Auditoría de cambios de estado |

Estados: `CONFIRMADA`, `REPROGRAMADA`, `CANCELADA`, `REALIZADA`.

## Cómo correr localmente

```bash
# Opción A — todo el sistema en contenedores (repo rys-infraestructura, clonado al lado de este)
cd ../rys-infraestructura && docker compose up -d --build
curl http://localhost:8083/api/citas

# Opción B — solo este servicio con Maven (requiere PostgreSQL y Redis locales)
mvn spring-boot:run

# Opción C — solo la imagen Docker de este servicio
docker build -t rys/ms-agenda:local .
docker run --rm -p 8083:8083 -e DB_HOST=host.docker.internal rys/ms-agenda:local
curl http://localhost:8083/actuator/health
```

## Arquitectura

Capas `api` → `service` → `repository` → `domain`. En AWS llamará de forma síncrona con
Circuit Breaker a `ms-psicologos` (valida disponibilidad) y a `ms-pagos` (cobra la sesión),
y **publica sus eventos de dominio en Amazon SQS** (`rys-citas-eventos`), que consume la función
serverless `fn-confirmacion-cita` (paquete `eventos/`).
Relación JPA destacada: `Cita` **OneToMany** `HistorialEstadoCita` (auditoría, **ManyToOne** inversa).

## Imagen Docker (EP3)

`Dockerfile` **multi-stage**: la etapa `build` (Maven + JDK 21) descarga dependencias en una capa aparte (caché)
y separa el jar en las capas de Spring Boot; la etapa `runtime` usa solo **JRE 21 sobre Alpine**, corre con un
**usuario sin privilegios** (UID 10001), expone el puerto 8083, define un `HEALTHCHECK` y ajusta la JVM al
límite de memoria del contenedor (`MaxRAMPercentage`). `.dockerignore` deja fuera `target/`, `.git/` y el resto.
Tamaño aproximado: ~145 MB comprimida (≈400 MB descomprimida, de los cuales ~180 MB son el JRE compartido).

## Despliegue en la nube (EP3)

Corre en el clúster **Docker Swarm** de AWS como el servicio `rys_ms-agenda` (definido en
`rys-infraestructura/docker-compose.yml`), detrás del **API Gateway**:
`https://<api>.execute-api.us-east-1.amazonaws.com/prod/agenda/api/citas` (cabecera `x-api-key`).

### Pipeline CI/CD (`.github/workflows/ci-cd.yml`)

| Etapa | Cuándo | Qué hace |
|---|---|---|
| 1 · Build y pruebas | push y pull request a `main`/`develop` | `mvn verify` + reporte de pruebas |
| 2 · Recursos cloud | push a `main` | Crea/verifica el repositorio ECR `rys/ms-agenda` (análisis de vulnerabilidades al subir) y verifica la cola SQS |
| 3 · Imagen | push a `main` | `docker build` multi-stage y push a Amazon ECR con etiqueta = commit (+ `latest`) |
| 4 · Despliegue | push a `main` | `docker service update --image …` por SSH al manager: rolling update `start-first` con rollback automático |
| 5 · Prueba de humo | push a `main` | Por el API Gateway: `/actuator/info` muestra la versión del commit, `/actuator/health` = UP, `GET /api/citas` = 200 y sin API key = 403 |

Secrets del repositorio: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` (Learner Lab, se renuevan
en cada sesión), `SWARM_HOST`, `SWARM_SSH_KEY`, `API_URL`, `API_KEY`.

## Correcciones aplicadas sobre la EP2

- **Configuración externa:** la conexión a la base de datos (y a Redis) estaba fija en `localhost`, lo que impedía
  ejecutar el servicio en contenedores; ahora se toma de `DB_HOST`, `DB_PORT`, `DB_NAME`, `REDIS_HOST` (con
  valores por defecto para desarrollo).
- **Salud del servicio:** se agregó Spring Boot Actuator (`/actuator/health/liveness`, `/readiness`, `/info` con la
  versión desplegada), que usan el `HEALTHCHECK` de Docker, Swarm y la prueba de humo del pipeline.
- **Apagado ordenado** (`server.shutdown: graceful`): al escalar hacia abajo o actualizar, termina las peticiones en curso.
- **Secretos fuera del código:** la clave de la base de datos llega como secreto de Docker Swarm
  (`/run/secrets/spring.datasource.password`, leído con `spring.config.import: configtree`).
- `spring.jpa.open-in-view: false` y pool de conexiones acotado (`DB_POOL_SIZE`, 4 por réplica) para que
  varias réplicas compartan la misma instancia RDS.
- **Eventos de dominio a la cola (antes eran `TODO`):** al reservar, reprogramar o cancelar se publica
  `CITA_RESERVADA` / `CITA_REPROGRAMADA` / `CITA_CANCELADA` en **Amazon SQS** (`rys-citas-eventos`), recién
  **después del commit** (`@TransactionalEventListener(AFTER_COMMIT)`), para que la función serverless
  `fn-confirmacion-cita` confirme al paciente de forma asíncrona. Sin `SQS_COLA_CITAS_URL` (local) el evento va al log.
- **Bloqueo huérfano en Redis:** si la base de datos fallaba después del `SETNX`, el horario quedaba bloqueado
  10 minutos sin cita; ahora se libera (compensación). Pruebas nuevas en `CitaServiceTest` y `SqsPublicadorEventosTest`.
