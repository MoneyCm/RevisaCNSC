# Fuentes CNSC

## DIAN 2676: identidad y plazo de ingreso — 2026-09-20
- robots.txt HTTP 200: micrositios y /node permitidos; no se descargaron adjuntos.
- https://www.cnsc.gov.co/convocatorias/dian-2676?field_tipo_de_contenido_convocat_target_id=64 — HTTP 200. main h1 identifica DIAN 2676; main cita expresamente Proceso de Selección DIAN 2676 de 2025. El año del proceso no equivale al año de inscripción.
- https://www.cnsc.gov.co/node/59797 — HTTP 200. Aviso publicado el 6 febrero 2026 16:18 amplía ingreso hasta 23:59 del 7 febrero 2026. Esta ventana es pasada a fecha de comprobación; no se interpreta como proceso finalizado ni como ausencia de futuras convocatorias.
- Extracción Android: validar main h1 contra nombre oficial y buscar nombre exacto + de + año; ambigüedad no produce año. Consulta bajo demanda al abrir detalle, caché local 24 horas. Estabilidad media.
- Fixture sanitizada android/app/src/test/resources/identity/dian-2676.html: conserva solo encabezado y extracto oficial con año, sin navegación ni datos personales. La búsqueda web fue pista; las afirmaciones anteriores se verificaron con HTTP directo.

Verificación: 19 septiembre 2026 (America/Bogota). Se consultó HTTP público sin autenticación. El buscador devolvió resultados pero falló al abrir algunas páginas; curl directo obtuvo 200 para listado, Territorial 12 y robots.txt. No se interpreta un fallo del buscador como caída CNSC.

| Fuente / URL | Información y extracción | Frecuencia propuesta | Verificación / estabilidad |
|---|---|---|---|
| https://www.cnsc.gov.co/convocatorias/en-desarrollo | Catálogo HTML Drupal; `main .views-field-name a`; eliminar query de categoría de la identidad | 30 minutos | HTTP 200, HTML inspeccionado; fixture real. Estabilidad media |
| https://www.cnsc.gov.co/convocatorias/territorial-12 | h1.page-title, taxonomy-term-1546, publicaciones con .views-field-created y .views-field-body; adjuntos | 30 minutos | HTTP 200, HTML inspeccionado; no inferir etapa actual de avisos históricos |
| https://www.cnsc.gov.co/convocatorias/proximas-convocatorias | Próximos; resultados indexados contienen referencias antiguas de 2020 | 60 minutos | HTTP 200; h1 y seis campos views observados; vigencia y parser NOT VERIFIED |
| https://www.cnsc.gov.co/convocatorias/en-uso-de-listas | Listas de elegibles | 60 minutos | Enlace observado en navegación real; extracción NOT VERIFIED |
| https://www.cnsc.gov.co/convocatorias/historicas | Finalizados | 24 horas | Enlace observado; extracción NOT VERIFIED |
| https://www.cnsc.gov.co/avisos-informativos | Avisos con convocatoria asociada y paginación | 30 minutos | HTTP 200; h1 CNSC Avisos informativos, 8 campos views y siguiente página; parser NOT VERIFIED |
| https://www.cnsc.gov.co/node/65132 | Aviso de adquisición de derechos e inscripciones Territorial 12 | Bajo demanda | Contenido indexado oficial consultado; no asumir vigencia actual |
| https://www.cnsc.gov.co/node/68210 | Aplazamiento de derechos e inscripciones CAR | Bajo demanda | Índice consultado; demuestra necesidad de manejar rectificaciones |
| https://www.cnsc.gov.co/mapa-del-sitio | Descubrimiento de Noticias y Calendario | Semanal | Índice consultado; URL exacta/estructura de calendario pendiente |
| https://simo.cnsc.gov.co/ | Enlace al trámite oficial | Sin crawling | No login, cookies personales ni CAPTCHA |

## Restricciones y riesgos

robots.txt obtenido por HTTP 200: prohíbe `/sites/default/files/*`, además de administración, login y búsqueda. El monitor debe respetarlo: metadatos/enlaces de adjuntos sí; descarga automática de PDF bajo esa ruta no. No habilitar PDF hasta resolver acceso permitido. Los términos de reutilización completos siguen NOT VERIFIED.

Categorías de micrositio observadas: Avisos, Acuerdos, Anexos, OPEC, documentos y acciones judiciales. Query `field_tipo_de_contenido_convocat_target_id` filtra contenido; no forma parte de identidad del concurso. No confundir una tutela que menciona inscripción con apertura. No extrapolar fecha de publicación al año del evento. No usar subdominio wip como fallback automático.

La primera integración sólo descubre catálogo real. Noticias, calendario y extracción de fechas se incorporarán con fixtures y verificaciones independientes. Conservar UNKNOWN para etapa que el catálogo no demuestra.

## Verificación HTTP complementaria

- https://www.cnsc.gov.co/cnsc-al-dia — HTTP 200, h1 Noticias, campos Drupal views y paginación. Frecuencia propuesta 30 min; estabilidad media; parser pendiente.
- https://www.cnsc.gov.co/calendario — HTTP 200, h1 Eventos, sin campos `main .views-field`. Requiere inspección del mecanismo de calendario antes de implementar. No asumir que una respuesta vacía sea ausencia de eventos.
- https://www.cnsc.gov.co/avisos-informativos — HTTP 200, paginado. Asociar por convocatoria explícita y capturar publicación/cuerpo separados.
- https://www.cnsc.gov.co/convocatorias/proximas-convocatorias — HTTP 200, seis campos views, sin siguiente página detectada. No convertir referencias antiguas en novedades actuales.
- Términos de uso: enlace PDF observado en footer dentro de `/sites/default/files/2021-07/`; no se descargó automáticamente por la restricción de robots. Revisión completa NOT VERIFIED.

Estos accesos usaron TLS verificado, robots.txt y pausa mínima de dos segundos. HTTP 200 valida accesibilidad, no la corrección de un parser todavía inexistente.


## Cadena TLS observada el 20 septiembre 2026
OpenSSL hacia www.cnsc.gov.co:443 con SNI mostró solo certificado final (*.cnsc.gov.co), emisor GeoTrust TLS RSA CA G1. Android rechazaba la cadena incompleta. Se obtuvo el intermedio desde https://cacerts.digicert.com/GeoTrustTLSRSACAG1.crt con HTTPS validado. Tras completar la cadena y conservar las raíces del sistema, el teléfono obtuvo HTTP 200. Ver DEC-010 para huella y mantenimiento.
