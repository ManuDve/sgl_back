# Arquitectura del Sistema - SGL

## Diagrama general

```
Cliente (Browser)
    |
    |-- Frontend Astro (puerto 4321)
    |       |
    |       +-- Backend Spring Boot (puerto 8080)
    |               |
    |               +-- PostgreSQL 15 (puerto 5432)
    |               |
    |               +-- Mailtrap (API externa, emails)
    |               |
    |               +-- Transbank (API externa, pagos)
    |
    +-- sgl_backup (cron diario 02:00 CLT)
```

## Componentes

| Componente | Tecnologia | Puerto |
|------------|-----------|--------|
| Frontend | Astro 6 con React 19 | 4321 |
| Backend | Spring Boot 3.4.5 con Java 21 | 8080 |
| Base de datos | PostgreSQL 15 | 5432 |
| Respaldo | Contenedor postgres:15-alpine con cron | - |

## Estructura del backend

```
cl.sgl
├── config/         Seguridad, CORS, filtros, seeders
├── controller/     Endpoints REST
├── dto/            Objetos de transferencia de datos
├── entity/         Entidades JPA
├── exception/      Manejo global de errores
├── repository/     Acceso a base de datos (Spring Data JPA)
└── service/        Logica de negocio
```

### Controladores principales

| Controlador | Ruta base | Acceso |
|-------------|-----------|--------|
| `PublicAppointmentController` | `/api/appointments` | Publico |
| `PublicServiceController` | `/api/services` | Publico |
| `AppointmentController` | `/api/admin/appointments` | JWT |
| `LegalServiceController` | `/api/admin/services` | JWT |
| `NotificationLogController` | `/api/admin/notifications` | JWT |
| `AuthController` | `/api/auth` | Publico |
| `WebpayController` | `/api/webpay` | Publico |

### Seguridad

- Autenticacion con JWT (expiracion 8 horas)
- CORS restringido a un origen via variable de entorno `ALLOWED_ORIGIN`
- CSRF deshabilitado por ser una API stateless
- Rate limiting de 20 solicitudes por minuto por IP en endpoints publicos (Bucket4j)
- Sanitizacion de inputs con OWASP HTML Sanitizer

### Base de datos

Migraciones con Flyway. La base de datos se crea automaticamente al iniciar la aplicacion si no existe.

| Migracion | Tabla | Descripcion |
|-----------|-------|-------------|
| V001 | `services` | Servicios legales |
| V002 | `appointments` | Agendamientos |
| V003 | `service_price_history` | Historial de precios |
| V004 | - | Campo descripcion en appointments |
| V005 | `reminder_log` | Log de recordatorios enviados |
| V006 | `email_retry_queue` | Cola de reintentos de email |
| V007 | `notification_log` | Auditoria de notificaciones |

## Levantamiento local

### Modo desarrollo

**1. Base de datos**

```bash
docker compose up -d postgres
```

**2. Backend** (IntelliJ, Run > Edit Configurations > Environment Variables)

```
SPRING_DATASOURCE_USERNAME=sgl_user
SPRING_DATASOURCE_PASSWORD=sgl_pass
JWT_SECRET=dev-local-secret-change-me-32bytes
SPRING_PROFILES_ACTIVE=dev
MAILTRAP_API_TOKEN=<token>
```

**3. Frontend**

```bash
cd ../sgl_front
npm install
npm run dev
```

Con `SPRING_PROFILES_ACTIVE=dev`, el sistema carga datos de prueba al iniciar (4 servicios y 10 agendamientos de muestra).

## Variables de entorno

| Variable | Descripcion | Requerida |
|----------|-------------|-----------|
| `SPRING_DATASOURCE_USERNAME` | Usuario de PostgreSQL | Si |
| `SPRING_DATASOURCE_PASSWORD` | Contrasena de PostgreSQL | Si |
| `SPRING_DATASOURCE_URL` | URL JDBC | No (default localhost) |
| `JWT_SECRET` | Clave de firma JWT (minimo 32 caracteres) | Si |
| `JWT_EXPIRATION_HOURS` | Duracion de sesion admin en horas | No (default 8) |
| `MAILTRAP_API_TOKEN` | Token de Mailtrap | No (emails no se envian) |
| `ALLOWED_ORIGIN` | Origen permitido por CORS | No (default localhost:4321) |
| `SPRING_PROFILES_ACTIVE` | Perfil activo | No (default prod) |
| `TRANSBANK_COMMERCE_CODE` | Codigo de comercio Transbank | Si (pagos) |
| `TRANSBANK_API_KEY` | Clave Transbank | Si (pagos) |
| `TRANSBANK_ENV` | Entorno Transbank | No (default integration) |

## Puertos y URLs de referencia

| Servicio | URL local |
|----------|-----------|
| Frontend | http://localhost:4321 |
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health check | http://localhost:8080/api/health |
| Panel admin | http://localhost:4321/admin/login |

## Credenciales por defecto (desarrollo)

- Admin: `admin@sgl.cl`
- Contrasena: `admin123`

Estas credenciales se crean automaticamente al iniciar la aplicacion en cualquier perfil excepto `test`. Deben cambiarse antes de cualquier despliegue a produccion.
