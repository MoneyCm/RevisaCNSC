---
name: cnsc-monitor
description: Crear, modificar o depurar recolectores de fuentes oficiales CNSC.
---

Leer docs/SOURCES.md. Mantener timeout, máximo de respuesta, user-agent identificable, intervalo mínimo entre solicitudes, cache condicional y backoff exponencial acotado. Respetar robots y validar host HTTPS antes de cada redirección. Ante error conservar datos, registrar source_check_failed sin cuerpos ni secretos y actualizar salud. Extraer sólo contenido relevante, normalizar y guardar snapshots por cambios; no guardar HTML indefinidamente. Parser vacío o estructura inesperada es fallo visible. Añadir fixture y prueba de regresión.
