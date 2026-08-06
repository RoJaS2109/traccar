# CLAUDE.md — Traccar (Traccar Server)

Fork privado de [Traccar](https://github.com/traccar/traccar) (`traccar/traccar`) con personalización de branding para RudaTrak.

## Stack técnico

| Componente | Tecnología |
|-----------|-----------|
| Lenguaje | Java 21 |
| Build | Gradle (`build.gradle`) |
| Servidor HTTP | Jetty 12 |
| REST API | Jersey 4 (JAX-RS) |
| Inyección | Guice 7 + HK2 bridge |
| DB | H2 (embebida), MySQL, MariaDB, PostgreSQL, SQL Server |
| Migraciones | Liquibase 5 (`schema/changelog-*.xml`) |
| Mensajería | Netty 4 (`io.netty:*`) |
| WebSocket | Jetty EE10 WebSocket |
| Tests | JUnit 5 + Mockito |
| Geocoding | `spatial4j`, `jts-core`, Google Open Location Code |

## Estructura de paquetes

```
src/main/java/org/traccar/
├── *.java                  # Core: Main, ServerManager, TrackerServer, PipelineBuilder
├── api/
│   ├── resource/           # Endpoints REST (SessionResource, DeviceResource, PositionResource, etc.)
│   ├── security/           # Autenticación y autorización
│   └── signature/          # Firma de requests
├── broadcast/              # Broadcast de posiciones a múltiples destinos
├── command/                # Comandos enviados a dispositivos GPS
├── config/                 # Configuración del servidor (keys, defaults)
├── database/               # DAOs y managers (CommandsManager, NotificationManager, etc.)
├── forward/                # Forwarding de posiciones a otros servidores
├── geocoder/               # Geocoding inverso (nominatim, google, etc.)
├── geofence/               # Geocercas (círculo, polígono)
├── geolocation/            # Geolocalización por torres celulares y WiFi
├── handler/                # Pipeline de procesamiento de datos
│   ├── events/             # Detección de eventos (alarmas, velocidad, etc.)
│   └── network/            # Handlers de red (celular, WiFi)
├── helper/                 # Utilidades
├── mail/                   # Envío de emails
├── mapmatcher/             # Map matching (snap a carreteras)
├── media/                  # Archivos multimedia de dispositivos
├── model/                  # Entidades: Device, Position, Event, Geofence, User, etc.
├── notification/           # Sistema de notificaciones
├── notificators/           # Canales: email, SMS, Firebase, Telegram, Webhook
├── protocol/               # ~675 decodificadores de protocolos GPS (200+ protocolos)
├── reports/                # Generación de reportes (Jxls + Velocity templates)
├── schedule/               # Tareas programadas (limpieza, reportes automáticos)
├── session/                # Sesiones de conexión de dispositivos
│   ├── cache/              # Cache de atributos y estado por dispositivo
│   └── state/              # Máquina de estados de conexión
├── sms/                    # Envío de SMS vía módem o proveedor HTTP
├── speedlimit/             # Límites de velocidad por coordenadas
├── storage/                # Capa de almacenamiento (SQL queries)
│   └── query/              # Builder de queries tipado
└── web/                    # Filtros HTTP, MCP, anulación de branding
    ├── OverrideTextFilter.java   # Reemplaza ${title}, ${description}, ${colorPrimary}
    └── OverrideFileFilter.java   # Reemplaza archivos (logos, íconos)
```

## Build

```bash
./gradlew build        # Compilar + tests
./gradlew jar          # Solo el JAR → target/tracker-server.jar
./gradlew test         # Tests con JUnit 5
./gradlew checkstyle   # Checkstyle (main sources)
```

- **JDK:** 21 (source + target)
- **JAR final:** `target/tracker-server.jar` con `Main-Class: org.traccar.Main`
- **Dependencias:** copiadas a `target/lib/` vía task `copyDependencies`

## Startup

`Main.java` → `MainModule.java` (Guice) → `ServerManager.java` → inicia:
1. **Jetty** en el puerto configurado (default 8082)
2. **TrackerServer** — listeners Netty para protocolos GPS (puerto 5000+)
3. **BroadcastService** — difusión de posiciones
4. **ScheduleManager** — tareas programadas

## API REST

Recursos en `api/resource/` (24 endpoints). Mapeados por Jersey. Ejemplos clave:

| Endpoint | Recurso |
|----------|---------|
| `/api/server` | `ServerResource` — configuración pública del servidor |
| `/api/session` | `SessionResource` — login, sesión, registro, recuperación |
| `/api/devices` | `DeviceResource` — CRUD de dispositivos |
| `/api/positions` | `PositionResource` — posiciones históricas y en tiempo real |
| `/api/reports/*` | `ReportResource` — reportes combinados, ruta, paradas, viajes |
| `/api/socket` | WebSocket — posiciones en tiempo real, eventos, logs |

**Autenticación:** Basic Auth + tokens de sesión. `SessionResource` maneja login, logout, registro, reset de password.

## Pipeline de procesamiento

Cuando un dispositivo GPS envía datos, pasa por esta cadena (orden configurable):

```
ProtocolDecoder → FilterHandler → DatabaseHandler → GeocoderHandler
→ GeofenceHandler → MotionHandler → DistanceHandler
→ ComputedAttributesHandler → Event handlers → PositionForwardingHandler
→ PostProcessHandler → BroadcastService (WebSocket)
```

Cada handler puede enriquecer la posición, generar eventos o bloquearla.

## Base de datos

- **Desarrollo/producción:** H2 (archivo `database.mv.db` en `data/`)
- **Migraciones:** Liquibase con changelogs en `schema/` (un archivo por versión)
- **`changelog-master.xml`** incluye todos los changelogs en orden

## Protocolos GPS

El directorio `protocol/` contiene ~675 archivos Java que implementan **más de 200 protocolos GPS** (Teltonika, Concox, Meitrack, Sinocast, etc.). Cada protocolo extiende:

- `BaseProtocol.java` — registro del protocolo
- `BaseProtocolDecoder.java` — decodificación de tramas binarias/texto
- `BaseProtocolEncoder.java` — envío de comandos al dispositivo
- `BaseFrameDecoder.java` — delimitación de tramas en el stream

## Personalización de Traccar-web (branding)

`OverrideTextFilter.java` intercepta respuestas HTTP a archivos `.html`, `.js`, `.css` y `.webmanifest` y reemplaza placeholders:

| Placeholder | Default | Origen |
|-------------|---------|--------|
| `${title}` | `RudaTrak` | `server.getString("title")` |
| `${description}` | `RudaTrak GPS Tracking` | `server.getString("description")` |
| `${colorPrimary}` | `#1976d2` | `server.getString("colorPrimary")` |

**Attributes configurables desde UI** (Settings → Server) que pisan los defaults.

`OverrideFileFilter.java` permite reemplazar archivos completos (logos, íconos) con versiones personalizadas subidas desde la UI.

## Docker

La imagen oficial es `traccar/traccar:latest`. Las personalizaciones (branding, frontend) se aplican en el Dockerfile multi-stage del frontend que:
1. Compila `OverrideTextFilter.java` con JDK 21
2. Parchea `tracker-server.jar` con la clase compilada
3. Copia el JAR parcheado sobre la imagen base

No se usa un Dockerfile propio en `backend/docker/` — esos son los Dockerfiles oficiales de Traccar para referencia.
