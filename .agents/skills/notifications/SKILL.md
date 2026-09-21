---
name: notifications
description: Implementar FCM, canales, preferencias, permisos, entrega o deep links.
---

Separar evento de entrega por dispositivo mediante outbox durable. Confirmar evidencia antes de crítica. Respetar configuración global y override; nuevo proceso por defecto habilitado. Crear canales urgente/importante/general. Manejar POST_NOTIFICATIONS, rotación token, token inválido, retry y deduplicación persistente Android por event_id. Payload abre /process/{id} con evento correcto. No afirmar exactly-once de FCM. No registrar tokens. Probar lógica real; mock sólo adaptador externo. Verificación física con pantalla bloqueada antes de DONE.
