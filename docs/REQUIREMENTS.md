# PROYECTO: MÉRITO RADAR

Quiero que construyas una aplicación Android completa, funcional, mantenible y preparada para uso real denominada provisionalmente:

**MÉRITO RADAR**

Su finalidad es vigilar los procesos de selección de la Comisión Nacional del Servicio Civil de Colombia —CNSC— para que el usuario pueda conocer en qué estado se encuentra cada concurso y, sobre todo, NO PIERDA fechas importantes de recaudo, pago de derechos de participación o inscripción.

No quiero solamente un prototipo visual.

No quiero solamente una maqueta.

No quiero pantallas bonitas conectadas a datos ficticios.

Quiero un sistema funcional de extremo a extremo:

CNSC → monitor → detección de cambios → base de datos → clasificación del evento → notificación push → teléfono Android → detalle del concurso → fuente oficial.

Debes trabajar como:

* arquitecto de software;
* desarrollador Android senior;
* desarrollador backend senior;
* especialista en extracción y normalización de información web;
* diseñador UI/UX;
* especialista en notificaciones Android;
* QA;
* DevOps.

Toma decisiones técnicas razonables sin detener el desarrollo innecesariamente para preguntarme cosas que puedas resolver técnicamente.

Si encuentras una alternativa mejor que la propuesta en este documento, puedes usarla, pero:

1. debe mejorar objetivamente confiabilidad, mantenibilidad o simplicidad;
2. debes documentar la decisión;
3. no debes eliminar ningún requisito funcional importante;
4. debes registrarla en `docs/DECISIONS.md`.

---

# 1. OBJETIVO DEL PRODUCTO

Quiero una aplicación Android que permita vigilar de forma permanente los procesos de selección de la CNSC.

Debe poder responder rápidamente preguntas como:

* ¿Qué concursos de la CNSC existen actualmente?
* ¿Cuáles están próximos?
* ¿Cuáles están en desarrollo?
* ¿Cuáles ya cerraron inscripciones?
* ¿En qué etapa está cada proceso?
* ¿Cuándo empieza el recaudo?
* ¿Cuándo empiezan las inscripciones?
* ¿Cuándo terminan?
* ¿Publicaron una nueva etapa?
* ¿Publicaron citación?
* ¿Publicaron pruebas?
* ¿Publicaron resultados?
* ¿Publicaron lista de elegibles?
* ¿Suspendieron el proceso?
* ¿Lo reanudaron?
* ¿Cambió alguna fecha?
* ¿Apareció un concurso completamente nuevo?

La característica MÁS IMPORTANTE del sistema es:

**AVISARME OPORTUNAMENTE CUANDO APAREZCA UN NUEVO CONCURSO O CUANDO SE PUBLIQUE EL INICIO DEL RECAUDO/PAGO DE DERECHOS O EL PERÍODO DE INSCRIPCIÓN.**

---

# 2. PRINCIPIO DE FUNCIONAMIENTO

NO diseñes la aplicación suponiendo que Android puede mantener indefinidamente un proceso consultando la CNSC cada pocos minutos.

La arquitectura principal debe ser cliente-servidor.

Un backend debe realizar la vigilancia.

La aplicación Android debe recibir notificaciones mediante Firebase Cloud Messaging.

Android puede usar WorkManager como respaldo o sincronización complementaria, pero NO debe depender de un servicio artificial permanentemente activo.

Arquitectura conceptual:

```text
Fuentes públicas CNSC
        ↓
Recolectores
        ↓
Normalización
        ↓
Comparador / Change Detector
        ↓
Base de datos
        ↓
Motor de eventos
        ↓
Clasificador de prioridad
        ↓
Firebase Cloud Messaging
        ↓
Aplicación Android
        ↓
Detalle + fuente oficial
```

---

# 3. FUENTES DE INFORMACIÓN

Antes de comenzar la implementación definitiva debes investigar la estructura ACTUAL de las fuentes públicas de la CNSC.

Prioriza siempre fuentes oficiales.

La fuente primaria debe ser el dominio oficial de la CNSC.

Debes investigar, como mínimo:

* procesos de selección próximos;
* procesos en desarrollo;
* páginas individuales de procesos;
* noticias CNSC;
* avisos importantes;
* calendario;
* documentos asociados;
* acuerdos;
* anexos;
* páginas de cada convocatoria;
* publicaciones relacionadas con recaudo;
* publicaciones relacionadas con derechos de participación;
* publicaciones relacionadas con inscripciones;
* publicaciones de citaciones;
* resultados;
* listas de elegibles;
* suspensiones;
* reanudaciones.

Investiga si existen otras fuentes públicas oficiales útiles.

SIMO puede utilizarse como enlace cuando corresponda.

NO almacenes:

* usuario SIMO;
* contraseña SIMO;
* cookies privadas;
* credenciales personales.

NO automatices autenticación personal del usuario en SIMO.

NO evadas CAPTCHA.

NO implementes técnicas para burlar mecanismos anti-bot.

Respeta límites razonables de consultas.

Implementa rate limiting.

Implementa cache.

Implementa backoff.

Evita generar carga innecesaria sobre los servidores oficiales.

---

# 4. NO BUSCAR SOLAMENTE LA PALABRA “PIN”

No dependas exclusivamente del término PIN.

La CNSC puede utilizar expresiones como:

* recaudo;
* derechos de participación;
* pago de derechos;
* pago;
* adquisición de derechos;
* inscripción;
* inscripciones;
* periodo de inscripción;
* modalidad abierto;
* modalidad ascenso;
* proceso de selección;
* convocatoria;
* venta de derechos;
* inicio de recaudo;
* cierre de recaudo.

Construye un mecanismo normalizado para identificar estos conceptos.

Ejemplo:

```text
EVENT_PAYMENT_OPEN
EVENT_PAYMENT_CLOSE
EVENT_REGISTRATION_OPEN
EVENT_REGISTRATION_CLOSE
```

independientemente de la redacción exacta encontrada.

---

# 5. TECNOLOGÍAS ANDROID

La aplicación Android debe desarrollarse preferiblemente con:

* Kotlin;
* Jetpack Compose;
* Material 3;
* MVVM;
* Repository Pattern;
* Clean Architecture pragmática;
* Coroutines;
* Kotlin Flow;
* Retrofit;
* OkHttp;
* Room;
* DataStore;
* Navigation Compose;
* Firebase Cloud Messaging;
* WorkManager;
* Hilt para inyección de dependencias si resulta apropiado.

Evita complejidad innecesaria.

La arquitectura debe permitir crecer sin convertir el proyecto en un monolito inmanejable.

---

# 6. BACKEND

Utiliza preferiblemente:

* Python;
* FastAPI;
* PostgreSQL;
* SQLAlchemy;
* Alembic;
* Pydantic;
* httpx;
* BeautifulSoup/lxml cuando sean adecuados;
* scheduler confiable;
* Firebase Admin SDK;
* Docker;
* Docker Compose;
* pytest.

Puedes usar herramientas adicionales cuando aporten una ventaja clara.

No utilices un navegador automatizado pesado si la información puede extraerse mediante HTTP normal.

Playwright solamente debe utilizarse para fuentes que realmente requieran renderizado JavaScript.

---

# 7. ESTRUCTURA DEL REPOSITORIO

Organiza aproximadamente así:

```text
merito-radar/
│
├── AGENTS.md
├── README.md
├── .gitignore
├── .env.example
├── docker-compose.yml
│
├── .agents/
│   └── skills/
│       ├── cnsc-source-research/
│       │   └── SKILL.md
│       │
│       ├── cnsc-monitor/
│       │   └── SKILL.md
│       │
│       ├── cnsc-change-detection/
│       │   └── SKILL.md
│       │
│       ├── android-design/
│       │   └── SKILL.md
│       │
│       ├── notifications/
│       │   └── SKILL.md
│       │
│       └── app-qa/
│           └── SKILL.md
│
├── android/
│
├── backend/
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── SOURCES.md
│   ├── DATA_MODEL.md
│   ├── DECISIONS.md
│   ├── NOTIFICATIONS.md
│   ├── SECURITY.md
│   └── STATUS.md
│
└── tests/
```

Puedes modificar esta estructura si existe una organización técnicamente superior.

---

# 8. AGENTS.MD

Crea un archivo `AGENTS.md` en la raíz.

Debe contener las reglas permanentes de desarrollo.

Incluye como mínimo:

## Reglas de confiabilidad

* Nunca inventar datos CNSC.
* Nunca inventar fechas.
* Nunca inventar estados.
* Nunca presentar una inferencia como información oficial.
* Conservar siempre trazabilidad hacia la fuente.
* Si un dato es ambiguo, marcarlo como `UNCONFIRMED`.
* No enviar alertas críticas basadas exclusivamente en inferencias débiles.

## Reglas de desarrollo

* Leer `docs/STATUS.md` antes de empezar.
* Actualizar `docs/STATUS.md` después de cada bloque importante.
* Ejecutar pruebas pertinentes después de cada cambio.
* No declarar una función como DONE si no fue probada.
* No reemplazar funcionalidad pendiente por mocks fuera de pruebas.
* Evitar TODO silenciosos.
* Documentar deuda técnica.
* Mantener cambios pequeños y verificables.

## Reglas de seguridad

* No almacenar secretos en Git.
* No almacenar credenciales personales.
* No registrar tokens FCM completos en logs públicos.
* Sanitizar entradas.
* Validar URLs.
* Utilizar variables de entorno.

---

# 9. SKILLS

Crea las siguientes skills reales del repositorio.

Cada skill debe incluir:

```text
---
name: nombre
description: descripción precisa de cuándo utilizar esta skill
---
```

y después instrucciones específicas.

## SKILL 1 — cnsc-source-research

Debe activarse cuando:

* aparezca una nueva fuente CNSC;
* cambie una URL;
* falle un scraper;
* sea necesario determinar dónde publica CNSC cierta información.

Debe enseñar a Codex a:

* investigar la fuente;
* identificar campos;
* registrar URL;
* identificar estructura HTML;
* identificar frecuencia de actualización;
* identificar estabilidad;
* documentarla en `docs/SOURCES.md`.

---

## SKILL 2 — cnsc-monitor

Debe activarse al:

* crear;
* modificar;
* depurar;

los recolectores CNSC.

Debe incluir reglas para:

* rate limiting;
* timeout;
* retry;
* exponential backoff;
* user-agent identificable;
* parseo defensivo;
* normalización;
* logging;
* detección de páginas caídas;
* almacenamiento de snapshots;
* fixture de prueba.

---

## SKILL 3 — cnsc-change-detection

Debe encargarse de determinar si una modificación constituye una novedad real.

Debe contemplar:

* contenido nuevo;
* proceso nuevo;
* etapa nueva;
* cambio de fecha;
* cambio de estado;
* nueva publicación;
* cambios cosméticos;
* modificaciones de navegación;
* HTML irrelevante;
* duplicados.

Nunca generar una alerta simplemente porque cambió el hash bruto de toda la página.

Primero normalizar el contenido relevante.

---

## SKILL 4 — android-design

Debe gobernar cualquier modificación UI.

Principios:

* minimalista;
* elegante;
* moderno;
* legible;
* rápido;
* accesible;
* consistente;
* poco ruido visual.

No convertir la aplicación en un dashboard empresarial saturado.

Antes de añadir un elemento preguntar internamente:

“¿Ayuda al usuario a saber qué concurso requiere atención?”

Si no, probablemente sobra.

---

## SKILL 5 — notifications

Debe encargarse de:

* canales Android;
* FCM;
* deep links;
* deduplicación;
* prioridades;
* preferencias;
* permisos;
* navegación desde notificación.

Debe impedir notificaciones duplicadas.

---

## SKILL 6 — app-qa

Debe activarse antes de considerar terminada una funcionalidad.

Debe obligar a:

* ejecutar tests;
* validar errores;
* validar estados vacíos;
* validar offline;
* validar reinicio;
* validar rotación cuando corresponda;
* validar modo oscuro;
* validar notificaciones;
* validar deep links.

---

# 10. MODELO DE DATOS

Diseña cuidadosamente las entidades.

Como mínimo contempla:

## Process

```text
id
external_id
slug
name
short_name
description
status
current_stage
modality
official_url
simo_url
created_at
updated_at
first_detected_at
last_checked_at
```

## Organization

```text
id
name
process_id
```

Un proceso puede tener múltiples entidades.

## ProcessStage

```text
id
process_id
stage_type
name
status
start_date
end_date
official_source_id
confidence
created_at
updated_at
```

## Event

```text
id
process_id
event_type
title
description
priority
event_date
detected_at
source_id
fingerprint
notification_sent
```

## Source

```text
id
url
source_type
title
official
last_checked_at
last_success_at
http_status
content_hash
```

## Snapshot

```text
id
source_id
captured_at
normalized_content
content_hash
```

No necesitas conservar indefinidamente HTML gigante si existe una estrategia mejor.

## Change

```text
id
source_id
process_id
change_type
old_value
new_value
detected_at
event_id
```

## Device

```text
id
installation_id
fcm_token
platform
app_version
last_seen
```

## Subscription

```text
id
device_id
process_id
critical_enabled
important_enabled
info_enabled
enabled
```

Agrega las tablas que sean necesarias.

---

# 11. ESTADOS DE PROCESO

Crea un modelo normalizado.

Ejemplo:

```text
UPCOMING
PAYMENT
REGISTRATION
REQUIREMENTS_VERIFICATION
EXAMS
RESULTS
ELIGIBLE_LIST
SUSPENDED
FINISHED
UNKNOWN
```

No fuerces un proceso a pasar por una etapa que no corresponda.

---

# 12. TIPOS DE EVENTO

Como mínimo:

```text
PROCESS_DISCOVERED
PROCESS_UPDATED

PAYMENT_ANNOUNCED
PAYMENT_OPEN
PAYMENT_CLOSE
PAYMENT_DATE_CHANGED

REGISTRATION_ANNOUNCED
REGISTRATION_OPEN
REGISTRATION_CLOSE
REGISTRATION_DATE_CHANGED

REQUIREMENTS_PUBLISHED

EXAM_ANNOUNCED
EXAM_CITATION
EXAM_DATE_CHANGED

RESULT_PUBLISHED

CLAIMS_OPEN
CLAIMS_CLOSE

ELIGIBLE_LIST_PUBLISHED

PROCESS_SUSPENDED
PROCESS_RESUMED

DOCUMENT_PUBLISHED
NEWS_PUBLISHED

OTHER_IMPORTANT_CHANGE
```

---

# 13. PRIORIDAD DE EVENTOS

Clasifica los eventos.

## CRITICAL

* concurso nuevo;
* anuncio de recaudo;
* inicio de recaudo;
* cierre próximo de recaudo;
* cambio de fecha de recaudo;
* anuncio de inscripciones;
* apertura de inscripción;
* cierre próximo de inscripción;
* cambio de fecha de inscripción;
* suspensión;
* reanudación.

## IMPORTANT

* citación;
* prueba;
* cambio de prueba;
* resultados;
* reclamaciones;
* lista de elegibles.

## INFO

* nuevo documento;
* noticia;
* cambio menor.

Esta clasificación debe ser configurable.

---

# 14. DEDUPLICACIÓN

Es crítico.

Un mismo evento puede aparecer:

* en una noticia;
* en la página de la convocatoria;
* en un documento;
* en un calendario.

NO quiero tres notificaciones.

Construye fingerprints deterministas.

El fingerprint podría considerar:

```text
process_id
event_type
normalized_start_date
normalized_end_date
```

No dependas únicamente de URL.

Si el mismo evento aparece en distintas fuentes oficiales:

* consolídalo;
* registra múltiples evidencias;
* envía una sola alerta.

---

# 15. CONFIANZA

Cada dato extraído debe poder tener:

```text
CONFIRMED
PROBABLE
UNCONFIRMED
```

Las fechas tomadas directamente de una publicación oficial pueden marcarse CONFIRMED.

Una inferencia basada exclusivamente en texto ambiguo no debe generar una alerta crítica automáticamente.

---

# 16. TRAZABILIDAD

Para cada evento conservar:

* fuente;
* URL;
* fecha de detección;
* fecha de publicación si existe;
* fragmento relevante normalizado;
* campos anteriores;
* campos nuevos.

En Android debe existir:

**Ver fuente oficial**

---

# 17. PANTALLA PRINCIPAL

Quiero una pantalla extremadamente limpia.

Debe entenderse en menos de cinco segundos.

Encabezado:

**Mérito Radar**

Debajo:

estado general.

Ejemplo:

```text
Todo bajo control

12 procesos vigilados
0 alertas urgentes
Última revisión: 10:42 p. m.
```

Si existe una alerta:

```text
ATENCIÓN

Territorial 2027

La CNSC anunció el inicio del recaudo.

Comienza:
4 noviembre

Ver detalle
```

Después:

## Mis concursos

Tarjetas simples.

Ejemplo:

```text
DIAN
Pruebas escritas
Sin fechas pendientes
```

```text
Procuraduría
Inscripciones cerradas
En seguimiento
```

```text
Territorial
Recaudo
Comienza en 3 días
```

---

# 18. NAVEGACIÓN

Bottom Navigation:

```text
Inicio
Concursos
Alertas
Ajustes
```

No agregar más opciones principales salvo necesidad clara.

---

# 19. PANTALLA CONCURSOS

Debe tener pestañas o filtros sencillos:

```text
Todos
Siguiendo
Próximos
En desarrollo
Finalizados
```

Agregar búsqueda.

Permitir buscar por:

* nombre;
* entidad;
* palabra clave.

---

# 20. DETALLE DEL CONCURSO

Debe mostrar arriba:

```text
Nombre
Estado
Etapa actual
Próxima fecha importante
```

Después una línea temporal.

Ejemplo:

```text
Convocatoria
      ✓

Recaudo
      ● ACTUAL
      4 nov - 18 nov

Inscripción
      ○

VRM
      ○

Pruebas
      ○

Resultados
      ○

Lista de elegibles
      ○
```

Mostrar también:

* descripción;
* entidades;
* modalidad;
* eventos recientes;
* documentos;
* fuentes.

Botones:

```text
Ver fuente oficial
Abrir en SIMO
```

cuando corresponda.

---

# 21. FAVORITOS / SEGUIMIENTO

Cada concurso debe poder marcarse:

```text
☆ Seguir
```

o

```text
★ Siguiendo
```

Permitir:

* seguir todos;
* dejar de seguir;
* silenciar;
* configurar alertas.

---

# 22. PERSONALIZACIÓN DE ALERTAS

Por proceso:

```text
Alertas críticas       ON
Actualizaciones        ON
Noticias menores       OFF
```

También permitir:

```text
Solo alertas críticas
```

Configuración global + override individual.

---

# 23. FUNCIÓN “¿CÓMO VAN LOS CONCURSOS?”

Quiero una función visible llamada:

**¿Cómo van los concursos?**

Al tocarla:

1. solicitar actualización al backend;
2. obtener estado;
3. mostrar resumen.

Ejemplo:

```text
Actualizado hace unos segundos

23 procesos revisados
2 con novedades
21 sin cambios

Fuentes CNSC:
7 disponibles
0 con error
```

Después mostrar novedades.

Ejemplo:

```text
NUEVO
Territorial 2027
Publicada fecha de recaudo

ACTUALIZADO
DIAN
Nueva citación disponible
```

---

# 24. CENTRO DE ALERTAS

Pantalla:

**Alertas**

Filtros:

```text
Todas
Urgentes
Importantes
Informativas
```

Cada alerta:

```text
URGENTE

Territorial 2027
Inicio de recaudo publicado

Detectado:
19 sep 2026 · 7:42 p. m.

Ver detalle
```

---

# 25. NOTIFICACIONES PUSH

Utilizar Firebase Cloud Messaging.

Configurar correctamente permisos de notificación según versión Android.

Crear canales:

## Alertas urgentes

Importancia alta.

Para:

* recaudo;
* inscripción;
* suspensión;
* cambio crítico.

## Actualizaciones importantes

Para:

* citaciones;
* pruebas;
* resultados;
* reclamaciones;
* listas.

## Información general

Noticias menores.

---

# 26. EJEMPLOS DE NOTIFICACIONES

## Concurso nuevo

```text
🆕 Nuevo proceso CNSC

Territorial 2027

Mérito Radar detectó un nuevo proceso de selección publicado por la CNSC.

Toca para revisarlo.
```

## Recaudo

```text
🚨 RECAUDO PUBLICADO

Territorial 2027

La CNSC publicó fechas para derechos de participación.

Inicio:
4 noviembre

Fin:
18 noviembre

Toca para ver los detalles.
```

## Inscripciones

```text
🚨 INSCRIPCIONES ABIERTAS

Territorial 2027

El periodo de inscripción ya comenzó.

Cierre:
18 noviembre

Toca para revisar el proceso.
```

## Citación

```text
📅 Nueva citación

DIAN 2676

Se publicó información relacionada con la citación a pruebas.

Toca para revisar la fuente oficial.
```

---

# 27. DEEP LINKS

Al tocar una notificación:

NO abrir solamente la pantalla Inicio.

Debe abrir exactamente:

```text
/process/{processId}
```

y resaltar el evento relacionado cuando sea posible.

---

# 28. ICONO

Diseña un icono original.

Concepto:

* radar;
* objetivo;
* pequeño indicador/check/estrella;
* minimalista;
* moderno;
* reconocible en tamaño pequeño.

NO utilizar:

* logo de CNSC;
* escudo de Colombia;
* símbolos que hagan parecer la aplicación oficial;
* diseño copiado.

Genera adaptive icon Android.

Incluye foreground y background adecuados.

---

# 29. IDENTIDAD VISUAL

La aplicación debe tener identidad propia.

Nombre:

**Mérito Radar**

Subconcepto:

**No pierdas tu próxima oportunidad.**

No es necesario mostrar ese texto permanentemente.

Diseño:

* Material 3;
* moderno;
* sobrio;
* minimalista;
* agradable;
* profesional;
* no gubernamental;
* no infantil.

Debe soportar:

* modo claro;
* modo oscuro;
* Dynamic Color solamente si no destruye identidad visual.

---

# 30. ACCESIBILIDAD

Considera:

* contraste;
* tamaño mínimo táctil;
* TalkBack;
* contentDescription;
* escalado de fuente;
* estados no diferenciados únicamente por color.

---

# 31. ESTADOS DE CARGA

Cada pantalla debe contemplar:

```text
Loading
Success
Empty
Offline
Error
```

No mostrar pantallas en blanco.

---

# 32. FUNCIONAMIENTO SIN INTERNET

La aplicación debe conservar mediante Room los datos recientes.

Si el teléfono está offline:

```text
Sin conexión

Mostrando información guardada.

Última actualización:
18 sep · 8:42 p. m.
```

---

# 33. BACKEND — MONITOR

Implementa un scheduler.

Frecuencia inicial razonable:

aproximadamente cada 30-60 minutos.

No necesito actualizaciones cada minuto.

La frecuencia debe ser configurable mediante variable de entorno.

Ejemplo:

```text
MONITOR_INTERVAL_MINUTES=30
```

---

# 34. MONITOREO ADAPTATIVO

Opcionalmente implementa después una estrategia:

si existe una etapa crítica próxima:

* aumentar temporalmente frecuencia.

Ejemplo:

recaudo previsto mañana:

```text
monitor cada 15 minutos
```

Si no hay eventos próximos:

```text
monitor cada 60 minutos
```

Nunca generes tráfico excesivo.

---

# 35. HEALTH CHECK

El backend debe ofrecer:

```text
/health
```

Resultado aproximado:

```json
{
  "status": "ok",
  "database": "ok",
  "scheduler": "ok",
  "firebase": "ok",
  "last_monitor_run": "...",
  "sources_ok": 7,
  "sources_failed": 0
}
```

---

# 36. LATIDO DEL MONITOR EN ANDROID

En Ajustes:

```text
Sistema de vigilancia

● Operativo

Última revisión:
10:03 p. m.

Fuentes:
7/7 disponibles
```

Si falla:

```text
⚠ Problemas de vigilancia

2 fuentes no pudieron consultarse.
```

---

# 37. DIFERENCIAR PUBLICACIÓN Y DETECCIÓN

Mostrar cuando exista información:

```text
Publicado por CNSC:
19 sep 2026 · 4:32 p. m.

Detectado por Mérito Radar:
19 sep 2026 · 4:47 p. m.
```

Es importante conocer cuánto tardó el sistema en detectar la novedad.

---

# 38. SCRAPERS RESISTENTES

No construyas parsers extremadamente dependientes de:

```css
div:nth-child(7)
```

Prioriza:

* semántica;
* texto;
* headings;
* atributos estables;
* URLs;
* estructura tolerante.

Si cambia la web, el sistema debe fallar de forma visible y registrable, no silenciosamente.

---

# 39. DETECCIÓN DE CAMBIO SEMÁNTICO

No compares solamente HTML bruto.

Pipeline:

```text
HTTP
↓
parse
↓
eliminar navegación/ruido
↓
extraer contenido relevante
↓
normalizar espacios
↓
normalizar fechas
↓
normalizar enlaces
↓
estructura canónica
↓
hash
↓
comparación
```

---

# 40. EXTRACCIÓN DE FECHAS

Construye parser de fechas en español.

Debe reconocer ejemplos como:

```text
4 de noviembre de 2026
04/11/2026
04-11-2026
del 4 al 18 de noviembre
entre el 4 y el 18 de noviembre de 2026
a partir del 4 de noviembre
```

No asumir año si existe ambigüedad importante.

---

# 41. OBSERVABILIDAD

Implementa logging estructurado.

Registrar:

```text
source_check_started
source_check_success
source_check_failed
process_discovered
event_detected
event_deduplicated
notification_sent
notification_failed
parser_warning
```

No registrar secretos.

---

# 42. PANEL ADMINISTRATIVO

No es prioridad para MVP.

Primero implementa herramientas básicas vía backend/API.

Posteriormente, si aporta valor, puede existir panel web interno muy sencillo para:

* ver fuentes;
* procesos;
* errores;
* eventos;
* estado del monitor.

NO retrases la app Android principal por crear un dashboard administrativo sofisticado.

---

# 43. API

Diseña endpoints aproximadamente:

```text
GET /health

GET /processes
GET /processes/{id}
GET /processes/{id}/events

GET /alerts

POST /devices/register
POST /devices/token

GET /subscriptions
POST /subscriptions
PATCH /subscriptions/{id}

POST /sync/request
GET /sync/status
```

Utiliza versionado:

```text
/api/v1/
```

---

# 44. SEGURIDAD API

No dejar endpoints administrativos críticos abiertos.

Implementa protección razonable.

Para dispositivos puedes generar installation IDs anónimos.

No necesito cuenta de usuario en el MVP salvo que sea realmente necesaria.

Diseña el sistema para poder añadir cuentas posteriormente.

---

# 45. PRIVACIDAD

Recopilar la mínima información posible.

Preferiblemente:

* installation ID;
* token FCM;
* preferencias;
* concursos seguidos.

No recopilar:

* nombre;
* documento;
* contraseña SIMO;
* hoja de vida;
* información sensible.

---

# 46. MIGRACIONES

Todas las modificaciones PostgreSQL mediante Alembic.

No modificar esquemas manualmente sin migración.

---

# 47. DOCKER

El backend debe poder iniciarse mediante:

```bash
docker compose up
```

Servicios mínimos:

```text
api
postgres
```

Añade servicios adicionales solo si son necesarios.

---

# 48. VARIABLES DE ENTORNO

Crear `.env.example`.

Ejemplo:

```text
DATABASE_URL=
FIREBASE_PROJECT_ID=
FIREBASE_CREDENTIALS=
MONITOR_INTERVAL_MINUTES=30
LOG_LEVEL=INFO
```

Nunca subir secretos reales.

---

# 49. TESTS OBLIGATORIOS

Crea fixtures representativas de fuentes CNSC.

Prueba como mínimo los siguientes escenarios.

## Test 1

Aparece concurso nuevo.

Resultado:

```text
PROCESS_DISCOVERED
CRITICAL
```

una sola alerta.

## Test 2

Aparece fecha de recaudo.

Resultado:

```text
PAYMENT_ANNOUNCED
CRITICAL
```

## Test 3

Cambia fecha de recaudo.

Resultado:

```text
PAYMENT_DATE_CHANGED
CRITICAL
```

## Test 4

Comienza periodo de inscripción.

Resultado:

```text
REGISTRATION_OPEN
CRITICAL
```

## Test 5

Proceso suspendido.

Resultado:

```text
PROCESS_SUSPENDED
CRITICAL
```

## Test 6

Proceso reanudado.

Resultado:

```text
PROCESS_RESUMED
CRITICAL
```

## Test 7

Nueva citación.

Resultado:

```text
EXAM_CITATION
IMPORTANT
```

## Test 8

Aparece una noticia irrelevante.

Resultado:

NO alerta crítica.

## Test 9

Cambian menú, footer o CSS.

Resultado:

NO evento.

## Test 10

Mismo evento encontrado en dos páginas.

Resultado:

UNA sola alerta.

## Test 11

Se ejecuta monitor dos veces sin cambios.

Resultado:

segunda ejecución:

0 eventos nuevos.

## Test 12

La CNSC devuelve error HTTP.

Resultado:

* registrar fallo;
* conservar información previa;
* NO borrar datos;
* NO generar eventos falsos.

## Test 13

Página cambia inesperadamente de estructura.

Resultado:

* parser warning;
* preservar último dato conocido;
* marcar fuente degradada;
* NO asumir que desapareció el concurso.

---

# 50. TESTS ANDROID

Prueba:

* navegación;
* favoritos;
* filtros;
* detalle;
* caché;
* offline;
* DataStore;
* recepción FCM;
* deep link;
* preferencias;
* estado vacío;
* modo oscuro;
* fuentes grandes.

---

# 51. PRUEBA END-TO-END

Debe existir una forma de simular:

```text
fixture CNSC
↓
monitor
↓
evento nuevo
↓
base de datos
↓
notificación
↓
payload FCM
↓
Android
↓
detalle
```

Si Firebase real no puede utilizarse en CI, mockear solamente la capa externa de entrega FCM durante tests.

La lógica previa debe probarse realmente.

---

# 52. CI

Configura GitHub Actions o equivalente.

Al menos:

Backend:

```text
lint
tests
```

Android:

```text
lint
unit tests
assembleDebug
```

No es necesario configurar publicación automática a Play Store inicialmente.

---

# 53. DOCUMENTACIÓN

Mantén:

## docs/SOURCES.md

Para cada fuente:

```text
Nombre
URL
Tipo
Qué información aporta
Método de extracción
Frecuencia
Confiabilidad
Última verificación
Notas
```

## docs/ARCHITECTURE.md

Arquitectura completa.

## docs/DATA_MODEL.md

Modelo de datos.

## docs/DECISIONS.md

Decisiones arquitectónicas.

Formato sugerido:

```text
DEC-001
Fecha
Problema
Opciones
Decisión
Motivo
Consecuencias
```

## docs/STATUS.md

Siempre:

```text
# DONE

# DOING

# NEXT

# BLOCKERS
```

Mantenerlo actualizado.

---

# 54. README

Debe permitir que otro desarrollador pueda levantar el proyecto.

Incluir:

* propósito;
* arquitectura;
* requisitos;
* configuración;
* Firebase;
* PostgreSQL;
* Docker;
* Android;
* tests;
* variables de entorno;
* cómo ejecutar monitor;
* cómo generar APK;
* troubleshooting.

---

# 55. CRITERIOS PARA CONSIDERAR ALGO “TERMINADO”

Nunca escribas DONE simplemente porque escribiste el código.

Una funcionalidad está terminada solamente si:

1. está implementada;
2. compila;
3. tiene pruebas pertinentes;
4. las pruebas pasan;
5. se ejecutó cuando sea razonable;
6. la documentación está actualizada.

---

# 56. NO HACER

No quiero:

* datos ficticios persistentes en producción;
* botones sin implementar;
* TODO escondidos;
* pantallas desconectadas;
* alertas duplicadas;
* scraping agresivo;
* secretos en Git;
* credenciales SIMO;
* WebView como aplicación entera;
* aplicación que sea simplemente la web CNSC envuelta;
* servicio Android permanente innecesario;
* UI saturada;
* colores excesivos;
* iconografía inconsistente;
* animaciones molestas;
* información inferida presentada como oficial.

---

# 57. MVP REAL

Antes de implementar todas las características, consigue un vertical slice funcional.

El PRIMER gran objetivo debe ser:

```text
1. consultar una fuente CNSC real
2. descubrir al menos un proceso real
3. normalizarlo
4. guardarlo en PostgreSQL
5. exponerlo por FastAPI
6. mostrarlo en Android
7. generar un evento de prueba
8. enviar notificación
9. tocar notificación
10. abrir detalle
11. abrir fuente oficial
```

Hasta que este flujo funcione, no gastes gran cantidad de tiempo creando pantallas secundarias.

---

# 58. FASES

Trabaja aproximadamente así:

## FASE 0 — Investigación

* inspeccionar fuentes;
* crear SOURCES.md;
* verificar términos;
* comprobar estructuras;
* identificar riesgos.

## FASE 1 — Arquitectura

* AGENTS.md;
* skills;
* ARCHITECTURE.md;
* DATA_MODEL.md;
* DECISIONS.md;
* estructura de repositorio.

## FASE 2 — Backend mínimo

* PostgreSQL;
* modelos;
* FastAPI;
* primera fuente;
* primer parser.

## FASE 3 — Detección

* snapshot;
* normalización;
* diff;
* events;
* deduplicación.

## FASE 4 — Android mínimo

* Compose;
* lista;
* detalle;
* favoritos;
* API;
* Room.

## FASE 5 — FCM

* registro;
* canales;
* permisos;
* push;
* deep links.

## FASE 6 — Ampliación de fuentes

* noticias;
* procesos;
* calendario;
* documentos.

## FASE 7 — Personalización

* subscriptions;
* preferencias;
* filtros;
* alertas.

## FASE 8 — UX

* diseño definitivo;
* animaciones;
* modo oscuro;
* accesibilidad;
* icono.

## FASE 9 — Hardening

* tests;
* parser failures;
* observabilidad;
* retries;
* seguridad.

## FASE 10 — Entrega

* APK;
* documentación;
* Docker;
* CI;
* checklist.

---

# 59. DESPLIEGUE

El proyecto debe quedar preparado para desplegar el backend permanentemente.

No acoples el código a un proveedor específico.

Debe poder ejecutarse en:

* VPS;
* servicio de contenedores;
* Railway/Render/Fly.io o equivalente;
* infraestructura propia.

Si seleccionas un proveedor durante el desarrollo, documenta:

* motivo;
* costo aproximado;
* limitaciones;
* cómo migrarlo.

---

# 60. CONSUMO Y EFICIENCIA

El teléfono no debe gastar batería innecesariamente.

No hacer polling constante desde Android.

Usar:

```text
FCM para novedades
+
Room para cache
+
WorkManager ocasional
```

La mayor parte del monitoreo ocurre en backend.

---

# 61. RESILIENCIA

Si el backend permanece caído unas horas:

al volver:

* recuperar monitoreo;
* comparar estado previo;
* detectar cambios;
* evitar duplicados.

Si una fuente CNSC desaparece temporalmente:

NO asumir automáticamente que terminó el proceso.

---

# 62. FECHAS

Internamente manejar fechas con tipos reales.

No almacenar fechas importantes únicamente como texto.

Conservar:

```text
raw_text
parsed_date
timezone
confidence
```

cuando corresponda.

Mostrar en formato español colombiano.

---

# 63. ZONA HORARIA

Utilizar para eventos CNSC:

```text
America/Bogota
```

salvo evidencia explícita de otra zona.

Backend internamente puede usar UTC y convertir adecuadamente.

---

# 64. HISTORIAL

Cada concurso debe mostrar:

**Actividad reciente**

Ejemplo:

```text
19 sep
Nueva fecha de recaudo

14 sep
Documento publicado

3 sep
Proceso creado
```

---

# 65. CALENDARIO

Prepara el modelo para poder incorporar posteriormente una vista calendario.

No es requisito imprescindible del primer MVP.

No retrases el desarrollo principal por esto.

---

# 66. COMPARTIR

Posteriormente podría agregarse:

```text
Compartir concurso
```

que genere texto + enlace.

No es prioridad.

---

# 67. BÚSQUEDA

Búsqueda instantánea por:

```text
DIAN
Procuraduría
Alcaldía
Territorial
Nación
docente
```

Si un proceso tiene múltiples entidades, deben poder encontrarse por cualquiera de ellas.

---

# 68. ORDENAMIENTO

Por defecto priorizar:

1. alertas críticas;
2. fechas próximas;
3. procesos seguidos;
4. procesos recientes.

No mostrar una lista alfabética gigante como única opción.

---

# 69. INDICADORES VISUALES

Usar con moderación:

```text
URGENTE
NUEVO
ACTUALIZADO
PRÓXIMO
```

No llenar la pantalla de badges.

---

# 70. “TODO BAJO CONTROL”

Cuando no existe novedad:

```text
✓ Todo bajo control

No hay cambios importantes en tus concursos.
```

Debe transmitir confianza sin ocultar cuándo fue la última revisión.

---

# 71. FUENTE OFICIAL

En detalle:

```text
Fuente

Comisión Nacional del Servicio Civil
cnsc.gov.co

Ver publicación
```

Nunca presentar Mérito Radar como fuente oficial.

---

# 72. DESCARGO DE IDENTIDAD

En Ajustes / Acerca de:

```text
Mérito Radar es una aplicación independiente de seguimiento de información pública.

No está afiliada ni representa a la Comisión Nacional del Servicio Civil.

Para actuaciones oficiales, consulte siempre la CNSC y SIMO.
```

---

# 73. APK

Al final necesito poder generar:

```text
app-debug.apk
```

para instalarlo manualmente.

Documentar también:

```text
release signing
```

pero no colocar claves privadas en el repositorio.

---

# 74. PAQUETE ANDROID

Usar un applicationId provisional profesional.

Ejemplo:

```text
co.meritoradar.app
```

Si existe conflicto, selecciona alternativa y documenta.

---

# 75. VERSIONAMIENTO

Utilizar SemVer cuando corresponda.

Inicialmente:

```text
0.1.0
```

hasta que el producto sea estable.

---

# 76. EXPERIENCIA INICIAL

Primera apertura:

Pantalla sencilla:

```text
Mérito Radar

No pierdas una inscripción importante.

Vigilamos las novedades públicas de los procesos CNSC y te avisamos cuando aparece algo importante.

[Comenzar]
```

Segunda pantalla:

```text
¿Qué quieres vigilar?

○ Todos los concursos
○ Elegir concursos
```

Tercera:

solicitud de notificaciones explicada claramente.

No crear onboarding de 8 pantallas.

---

# 77. PRIMERA SINCRONIZACIÓN

Mostrar progreso:

```text
Buscando concursos...

Encontramos 24 procesos.
```

Los números deben ser reales.

---

# 78. CONFIGURACIÓN

Ajustes:

```text
Notificaciones

Alertas críticas
Actualizaciones importantes
Información general

Seguimiento

Seguir nuevos procesos automáticamente

Apariencia

Sistema
Claro
Oscuro

Sistema de vigilancia

Estado del monitor
Última revisión

Acerca de

Versión
Fuentes de información
Privacidad
```

---

# 79. NUEVOS CONCURSOS

Debe existir configuración:

```text
Avisarme de cualquier nuevo proceso CNSC
```

por defecto:

ON.

Esto es fundamental para no perder convocatorias futuras.

---

# 80. NUEVOS PROCESOS SIN FECHA

Si aparece un proceso pero todavía no tiene inscripciones:

avisar:

```text
🆕 Nuevo proceso CNSC

Aún no se han publicado fechas de inscripción.

Lo seguiremos vigilando.
```

Si el usuario lo desea.

---

# 81. ALERTAS PREVIAS A FECHAS

Una vez se conoce una fecha crítica, además del aviso inicial, preparar recordatorios configurables.

Ejemplo:

```text
7 días antes
3 días antes
1 día antes
día de inicio
1 día antes del cierre
```

Para MVP utilizar defaults razonables:

* día de publicación;
* día de inicio;
* 24 horas antes del cierre.

Evitar exceso de notificaciones.

---

# 82. CAMBIO DE FECHA

Este caso es especialmente importante.

Ejemplo:

```text
⚠ CAMBIÓ LA FECHA

Territorial 2027

La fecha de cierre de inscripción cambió.

Antes:
18 noviembre

Ahora:
22 noviembre

Ver fuente oficial
```

Guardar siempre valor anterior y nuevo.

---

# 83. CANCELACIÓN O SUSPENSIÓN

Ejemplo:

```text
⚠ PROCESO SUSPENDIDO

Proceso X

La CNSC publicó una suspensión.

Toca para consultar la información oficial.
```

Nunca asumir el motivo si no está explícito.

---

# 84. DETECCIÓN DE DOCUMENTOS

Si aparece PDF nuevo:

* guardar metadatos;
* URL;
* título;
* fecha si está disponible;
* relacionarlo con proceso.

No descargar indefinidamente archivos grandes si no es necesario.

Si el título revela información crítica, puede entrar al pipeline de eventos.

---

# 85. PDF

Si para detectar información importante es necesario analizar PDFs oficiales:

implementa una capa separada.

No mezcles lógica PDF con parser HTML.

Extrae texto y conserva referencia de origen.

Si el PDF es escaneado y requiere OCR, marcar menor confianza salvo verificación suficiente.

---

# 86. MÉTRICAS DEL MONITOR

Registrar:

```text
checks_total
checks_failed
events_detected
events_deduplicated
notifications_sent
notifications_failed
parser_errors
average_detection_latency
```

No necesitas implementar una plataforma compleja de métricas para el MVP, pero prepara la estructura.

---

# 87. FALLBACKS

Si una fuente principal falla y existe otra fuente oficial válida:

consultarla.

Nunca sustituir CNSC por blogs, redes sociales no oficiales o terceros como fuente primaria de una alerta crítica.

Fuentes externas pueden servir para descubrir que algo podría haber ocurrido, pero antes de alertar críticamente se debe buscar confirmación oficial.

---

# 88. FECHA DE PUBLICACIÓN VS FECHA DEL EVENTO

Distinguir:

```text
published_at
event_start_at
event_end_at
detected_at
```

No mezclarlas.

---

# 89. MONITOREO INCREMENTAL

Evitar reprocesar todo innecesariamente.

Usar:

* ETag si existe;
* Last-Modified si existe;
* hash;
* caché;
* timestamp.

Pero nunca depender exclusivamente de ellos si la fuente no los maneja correctamente.

---

# 90. PRUEBA MANUAL

Crear un checklist en:

```text
docs/MANUAL_TESTING.md
```

que permita probar la aplicación físicamente.

Ejemplo:

```text
[ ] instalar APK
[ ] abrir app
[ ] recibir permisos
[ ] cargar procesos
[ ] seguir proceso
[ ] cerrar app
[ ] bloquear teléfono
[ ] enviar FCM
[ ] recibir notificación
[ ] tocar alerta
[ ] abrir detalle correcto
[ ] abrir fuente
```

---

# 91. CALIDAD DE CÓDIGO

Evita archivos gigantes.

Separar responsabilidades.

Nombres claros.

Documentar solo cuando aporte valor.

No escribir comentarios que simplemente repitan el código.

---

# 92. NO SOBREARQUITECTAR

Quiero calidad, pero no burocracia técnica.

No crees:

* 30 microservicios;
* Kafka;
* Kubernetes;
* CQRS;
* event sourcing;

a menos que exista una razón extraordinariamente fuerte.

Para esta aplicación:

FastAPI + PostgreSQL + scheduler + FCM es suficiente inicialmente.

---

# 93. MANEJO DE CAMBIOS CNSC

Asume que la web puede cambiar.

Por eso:

* fixtures;
* tests;
* parsers separados por fuente;
* health status por fuente;
* warnings;
* fallback.

---

# 94. CAPACIDAD FUTURA

La arquitectura debe permitir posteriormente seguir también:

* Procuraduría;
* DIAN;
* Rama Judicial;
* entidades con sistemas de mérito propios;

pero NO implementes estas fuentes todavía.

El MVP es CNSC.

No contamines la lógica usando constantes exclusivamente CNSC donde pueda existir una abstracción simple de fuente.

---

# 95. NOMBRE

Utiliza provisionalmente:

**Mérito Radar**

No detener desarrollo por decisiones de branding.

---

# 96. DEFINICIÓN DE ÉXITO

Consideraré exitoso el primer producto cuando pueda hacer esto:

1. instalar APK en mi teléfono Android;
2. abrir Mérito Radar;
3. ver concursos CNSC reales;
4. seleccionar cuáles seguir;
5. cerrar la app;
6. seguir recibiendo alertas;
7. recibir aviso si aparece un nuevo proceso;
8. recibir aviso cuando se anuncie recaudo;
9. recibir aviso cuando se abran inscripciones;
10. recibir aviso si cambia una fecha;
11. tocar la alerta;
12. abrir directamente el concurso;
13. revisar la fuente CNSC;
14. pulsar SIMO cuando corresponda;
15. comprobar cuándo se revisó por última vez el sistema.

---

# 97. PRIORIDAD ABSOLUTA

Si debes escoger entre:

una animación bonita

o

detectar correctamente una apertura de inscripción,

elige siempre la detección.

Si debes escoger entre:

una pantalla adicional

o

eliminar alertas duplicadas,

elimina duplicados.

Si debes escoger entre:

una arquitectura sofisticada

o

un sistema confiable y mantenible,

elige confiabilidad.

---

# 98. PROCEDIMIENTO DE TRABAJO PARA CODEX

A partir de este momento trabaja así.

Antes de cualquier implementación significativa:

1. lee `AGENTS.md`;
2. lee `docs/STATUS.md`;
3. identifica la skill aplicable;
4. inspecciona el código existente;
5. no dupliques soluciones ya implementadas.

Después de implementar:

1. ejecuta las pruebas pertinentes;
2. corrige fallos;
3. actualiza documentación;
4. actualiza STATUS;
5. informa exactamente qué quedó probado.

---

# 99. PRIMERA TAREA QUE DEBES HACER AHORA

Empieza inmediatamente.

NO empieces creando veinte pantallas.

Haz esto en orden:

### PASO 1

Investiga las fuentes públicas actuales de CNSC necesarias para:

* descubrir procesos;
* conocer estado;
* obtener recaudo;
* obtener inscripción;
* detectar noticias;
* detectar cambios.

### PASO 2

Crea:

```text
docs/SOURCES.md
```

con los hallazgos.

### PASO 3

Crea:

```text
AGENTS.md
```

### PASO 4

Crea todas las skills:

```text
.agents/skills/cnsc-source-research/SKILL.md
.agents/skills/cnsc-monitor/SKILL.md
.agents/skills/cnsc-change-detection/SKILL.md
.agents/skills/android-design/SKILL.md
.agents/skills/notifications/SKILL.md
.agents/skills/app-qa/SKILL.md
```

### PASO 5

Crea:

```text
docs/ARCHITECTURE.md
docs/DATA_MODEL.md
docs/DECISIONS.md
docs/STATUS.md
```

### PASO 6

Crea estructura del backend.

### PASO 7

Implementa una fuente CNSC real.

### PASO 8

Haz que al menos un proceso CNSC real entre:

```text
fuente
→ parser
→ normalización
→ PostgreSQL
→ API
```

### PASO 9

Escribe las pruebas.

### PASO 10

Solo entonces comienza el cliente Android.

---

# 100. REGLA FINAL

No me entregues una aplicación que “parezca terminada”.

Entrégame una aplicación que realmente funcione.

Cuando una parte no pueda comprobarse todavía, indícalo expresamente como:

```text
NOT VERIFIED
```

No inventes éxito.

No marques:

```text
DONE
```

si no fue probado.

No ocultes errores.

Si una decisión es provisional, documenta que es provisional.

Mantén permanentemente actualizados:

```text
docs/STATUS.md
docs/DECISIONS.md
```

y continúa trabajando progresivamente hasta conseguir el flujo completo.

## COMIENZA AHORA

Empieza por investigar las fuentes oficiales actuales de la CNSC y crear la infraestructura de instrucciones del repositorio (`AGENTS.md`, skills y documentación).

Después construye el vertical slice funcional.

No te limites a explicarme lo que harías.

**Hazlo en el repositorio.**
