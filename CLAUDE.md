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

### Protocolo Rinho (`RinhoProtocol*`)

Implementación propia para el tracker GPS Rinho IoT (`EG915U + LC86G`). Usa UDP con mensajes de texto.

**Archivos:**
| Archivo | Líneas | Descripción |
|---------|--------|-------------|
| `RinhoProtocol.java` | 48 | Registro Netty, comandos soportados |
| `RinhoProtocolDecoder.java` | ~2900 | Decodificador principal (32 tipos de reporte) |
| `RinhoProtocolEncoder.java` | 73 | Comandos al dispositivo (QGP, SXP00, QVR) |
| `RinhoProtocolDecoderTest.java` | 614 | 20 tests JUnit 5 |

**Formato de mensajes:** `>TYPE...;#NNNN;ID=XXXX;*CC<` con checksum XOR.

**Tipos de reporte:** RCQ (principal), RER (CAN bus), RCR (compacto), RGP, RCW, RCY, RAD, RAE, RIO, REQ (OBD-II), RVR, RSN, RIMEI, RTAG, RCXHWI, RTX, RIB, RSC, RLC, RHT y 10 variantes CQ con sufijos (RCP, RCT, RCU, RCV, RBQ, RBR, RBV, RHQ, RHR, RHV).

**Sistema de alarmas — `decodeAlarm(int eventCode)`:**
Mapea 44 códigos de evento Rinho a constantes `Position.ALARM_*` de Traccar. 13 códigos informativos usan `ALARM_GENERAL` para ser visibles en la UI sin disparar alertas. 25 códigos RES. devuelven `null`.

**`getEventDescription(int eventCode)`** devuelve la descripción en español de `listado.txt` y la almacena en el atributo `eventDescription`.

**`getEventType(int eventCode)`** enruta cada código al tipo nativo Traccar (`Event.TYPE_*`):
- `0x35, 0x37` → `geofenceEnter`
- `0x36` → `geofenceExit`
- `0x40, 0x46` → `deviceFuelDrop`
- `0x74, 0x77` → `deviceOverspeed`
- `0x24, 0x41, 0x42, 0x43, 0x44` → `maintenance`
- resto → `alarm`

**`getEventCategory(int eventCode)`** clasifica eventos informativos como `"aviso"` (puertas cerradas, reconexiones, pérdidas de conexión) para que la UI muestre "Aviso" en vez de "Alarma".

Patrón en los 5 puntos donde se setea alarma + descripción + categoría + tipo:
```java
String alarm = decodeAlarm(eventCode);
if (alarm != null) {
    position.addAlarm(alarm);
}
String desc = getEventDescription(eventCode);
if (desc != null) {
    position.set("eventDescription", desc);
}
position.set("eventCategory", getEventCategory(eventCode));
position.set("eventType", getEventType(eventCode));
```

**ACK:** El decoder envía `>ACK;#NNNN;ID=XXXX;*CC<` por cada mensaje recibido con `msgNum < 0x8000`.

**Importante — sincronización con docker:** Los archivos modificados del backend deben copiarse al overlay Docker en `traccar-web/docker/` para que el build los incluya en el JAR parcheado:
```bash
# Desde traccar/
cp src/main/java/org/traccar/protocol/RinhoProtocolDecoder.java \
   traccar-web/docker/org/traccar/protocol/RinhoProtocolDecoder.java
cp src/main/java/org/traccar/handler/events/AlarmEventHandler.java \
   traccar-web/docker/org/traccar/handler/events/AlarmEventHandler.java
cp src/main/java/org/traccar/notification/NotificationFormatter.java \
   traccar-web/docker/org/traccar/notification/NotificationFormatter.java
```

**Documentación completa:** [`traccar-web/docs/GPS_RINHO/protocolo-rinho.md`](traccar-web/docs/GPS_RINHO/protocolo-rinho.md)

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

La imagen oficial es `traccar/traccar:latest`. Las personalizaciones (branding, frontend, protocolo Rinho, eventDescription, notificaciones) se aplican en el Dockerfile multi-stage del frontend que:
1. Compila `OverrideTextFilter.java`, `RinhoProtocol*.java`, `AlarmEventHandler.java`, y `NotificationFormatter.java` con JDK 21
2. Parchea `tracker-server.jar` con las clases compiladas y templates Velocity personalizados
3. Copia el JAR parcheado sobre la imagen base

No se usa un Dockerfile propio en `backend/docker/` — esos son los Dockerfiles oficiales de Traccar para referencia.

### Sync de archivos parcheados

Los siguientes archivos existen tanto en `src/main/java/` (fuente canónico) como en `traccar-web/docker/` (copia para build Docker). Deben mantenerse sincronizados:

| Archivo | Propósito |
|---------|-----------|
| `RinhoProtocolDecoder.java` | Decodificador del protocolo Rinho (44 códigos, `getEventType()`, `getEventCategory()`, `getEventDescription()`) |
| `AlarmEventHandler.java` | Propaga `eventDescription`, `eventCategory` y `eventType` de posición a evento; dedup por `KEY_EVENT` |
| `NotificationFormatter.java` | Fallback `Maintenance` para eventos Rinho sin `maintenanceId`; digest en español natural (`contains()` + `SimpleDateFormat`) |

**Si se modifica el fuente canónico y no se sincroniza la copia en `docker/`, el deploy usará la versión antigua.**
