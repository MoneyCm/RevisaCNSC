# Seguridad y privacidad

No se recopilan cuentas, credenciales ni cookies SIMO. Android guarda catálogo y favoritos localmente. El backend inicial expone sólo lectura con paginación limitada; no permite URLs de ingesta suministradas por cliente. Monitor CLI consulta host HTTPS permitido, rechaza redirects, valida TLS y respeta robots; respuesta máxima 2 MB. Datos fuente no se ejecutan como HTML en Android.

Producción pendiente: TLS reverse proxy, limitación por IP en borde, backups y restauración, métricas, retención de snapshots, revisión de dependencias. Base privada; secretos vía entorno. Instancia local de verificación usa trust sólo en loopback 55432, excluida de Git y no apta para despliegue.

Antes de FCM: credencial anónima secreta por instalación (no confiar sólo en UUID), almacenamiento protegido del token, borrado de dispositivo, preferencias autorizadas y rate limit del registro. No registrar tokens completos.
