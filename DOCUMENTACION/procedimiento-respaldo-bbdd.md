# Procedimiento de Respaldo de Base de Datos - SGL

Version 1.0.0 - Mayo 2026

## Que se respalda

La base de datos PostgreSQL `sgl_db`, que contiene toda la informacion operativa del sistema:

| Tabla | Descripcion |
|-------|-------------|
| `services` | Catalogo de servicios legales y precios |
| `service_price_history` | Historial de cambios de precio |
| `appointments` | Agendamientos de clientes |
| `admin_users` | Usuarios administradores |
| `email_retry_queue` | Cola de reintentos de email |
| `notification_log` | Auditoria de notificaciones |
| `reminder_log` | Log de recordatorios enviados |

El respaldo es un volcado SQL completo en texto plano (`pg_dump --format=plain`) que incluye definicion de tablas, datos, indices y secuencias. No incluye permisos de roles ni propietarios, lo que facilita la restauracion en cualquier entorno.

## Como se ejecuta el respaldo

El respaldo lo ejecuta el script `backup.sh` desde el contenedor `sgl_backup`. El flujo es:

```
02:00 America/Santiago
    crond
        backup.sh
            pg_dump -> /backups/backup_YYYY-MM-DD_HH-MM.sql
            find /backups -mtime +7 -delete  (limpia archivos de mas de 7 dias)
```

Comando interno:

```bash
PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump \
  -h postgres \
  -U "${POSTGRES_USER}" \
  -d "${POSTGRES_DB}" \
  --no-password \
  --format=plain \
  --no-owner \
  --no-privileges \
  > /backups/backup_YYYY-MM-DD_HH-MM.sql
```

Para ejecutar manualmente sin esperar al cron:

```bash
docker exec sgl_backup sh -c "sh /backup.sh >> /var/log/backup.log 2>&1"
```

Ver el log de ejecucion:

```bash
docker exec sgl_backup cat /var/log/backup.log
```

Salida esperada:
```
[2026-05-18 19:30:01] Iniciando respaldo -> /backups/backup_2026-05-18_19-30.sql
[2026-05-18 19:30:03] Respaldo completado: /backups/backup_2026-05-18_19-30.sql (1.2M)
[2026-05-18 19:30:03] Limpieza: no hay archivos que superen 7 dias.
```

## Programacion del cron

El servicio `sgl_backup` ejecuta el siguiente crontab al iniciar:

```
0 2 * * * sh /backup.sh >> /var/log/backup.log 2>&1
```

El contenedor tiene configurada la variable `TZ: America/Santiago`, por lo que 02:00 corresponde a la hora local de Chile.

Para verificar la configuracion activa dentro del contenedor:

```bash
docker exec sgl_backup crontab -l
```

Para confirmar que el contenedor esta activo:

```bash
docker compose ps
```

## Almacenamiento

Los archivos se guardan en `/backups/` dentro del contenedor, que esta montado como volumen bind en `./backups/` del host. Los archivos persisten aunque el contenedor se recree o elimine.

Nomenclatura de archivos:
```
backup_YYYY-MM-DD_HH-MM.sql

Ejemplo: backup_2026-05-18_02-00.sql
```

Politica de retencion: los archivos con mas de 7 dias se eliminan automaticamente. Siempre hay 7 archivos disponibles.

Tamaño estimado por respaldo: menos de 10 MB en condiciones normales.

Los archivos `.sql` estan excluidos del repositorio git via `.gitignore`.

Verificar archivos generados:

```bash
ls -lh ./backups/
```

## Recuperacion del respaldo

Este procedimiento sobreescribe todos los datos actuales. Ejecutar solo cuando sea necesario.

**Paso 1 - Identificar el respaldo**

```bash
ls -lh ./backups/
```

**Paso 2 - Registrar estado actual (referencia)**

```bash
docker exec sgl_postgres psql -U sgl_user -d sgl_db \
  -c "SELECT 'appointments' AS tabla, COUNT(*) FROM appointments
      UNION ALL SELECT 'services', COUNT(*) FROM services;"
```

**Paso 3 - Detener el backend**

```bash
docker compose stop backend
```

**Paso 4 - Limpiar la base de datos**

```bash
docker exec -it sgl_postgres psql \
  -U ${POSTGRES_USER:-sgl_user} \
  -d postgres \
  -c "DROP DATABASE IF EXISTS sgl_db;" \
  -c "CREATE DATABASE sgl_db;"
```

**Paso 5 - Restaurar**

```bash
docker exec -i sgl_postgres psql \
  -U ${POSTGRES_USER:-sgl_user} \
  -d ${POSTGRES_DB:-sgl_db} \
  < ./backups/backup_2026-05-18_02-00.sql
```

Reemplazar el nombre del archivo por el respaldo que se desea restaurar.

**Paso 6 - Verificar tablas**

```bash
docker exec sgl_postgres psql \
  -U ${POSTGRES_USER:-sgl_user} \
  -d ${POSTGRES_DB:-sgl_db} \
  -c "\dt"
```

**Paso 7 - Verificar conteo de registros**

```bash
docker exec sgl_postgres psql \
  -U ${POSTGRES_USER:-sgl_user} \
  -d ${POSTGRES_DB:-sgl_db} \
  -c "SELECT 'appointments' AS tabla, COUNT(*) FROM appointments
      UNION ALL SELECT 'services', COUNT(*) FROM services;"
```

Comparar con el resultado del Paso 2 para confirmar que los datos coinciden.

**Paso 8 - Reiniciar el backend**

```bash
docker compose start backend
```

**Paso 9 - Verificacion final**

Acceder al panel admin en http://localhost:4321/admin/login y confirmar que los KPIs y el listado de agendamientos cargan correctamente.

## Resumen de parametros

| Parametro | Valor |
|-----------|-------|
| Herramienta | pg_dump (PostgreSQL 15) |
| Tipo | Volcado logico SQL completo |
| Frecuencia | Diaria automatica |
| Hora | 02:00 America/Santiago |
| Retencion | 7 dias |
| Ubicacion | ./backups/ en el host |
| Formato | .sql texto plano |
| Tamaño estimado | menos de 10 MB por respaldo |
| Tiempo de restauracion | menos de 5 minutos |
