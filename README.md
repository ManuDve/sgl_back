# SGL Backend

API REST de SGL Plataforma de Reserva y Coordinación Jurídica. Gestiona agendamientos de consultas juriíicas, notificaciones por email, autenticación de administradores y pagos con Transbank Webpay Plus.

## Descripción del proyecto

SGL es un sistema para un estudio jurídico. Esta API expone dos grupos de endpoints: uno público para que los clientes puedan agendar consultas sin crear una cuenta, y uno protegido con JWT para que los administradores gestionen agendamientos, confirmen pagos y administren los servicios del estudio.

El sistema incluye un módulo de notificaciones con reintentos automáticos, gestión para reagendar y cancelar consultas, un registro de auditoría de emails enviados e integración con Transbank para pagos en línea.

## Tecnologías

- Java 21
- Spring Boot 3.4.5
- PostgreSQL 15
- Flyway para migraciones de base de datos
- Spring Security con autenticación JWT
- Mailtrap SDK para emails transaccionales
- Transbank SDK para pagos con Webpay Plus
- JUnit 5 y Mockito para pruebas unitarias
- JaCoCo para reporte de cobertura de código
- Bucket4j para rate limiting en endpoints públicos
- OWASP HTML Sanitizer para prevención de XSS
- Maven como herramienta de build
- Docker y Docker Compose para la base de datos local

## Estructura del equipo

| Nombre | Rol |
|--------|-----|
| Manuel Alfaro | Desarrollador |

## Tablero Kanban

https://gestor-legal.atlassian.net/jira/software/projects/DEV/boards/1

## Confluence

https://gestor-legal.atlassian.net/wiki/spaces/~7120201ea05b60bb4f43c98ac238acf9179f38/pages/edit-v2/17268737

La documentacion tecnica adicional esta disponible en la carpeta `DOCUMENTACION/`.

## Requisitos

- Java 21
- Maven 3.8 o superior
- Docker y Docker Compose

## Configuracion local

**1. Base de datos**

```bash
docker compose up -d postgres
```

**2. Variables de entorno**

Configurar en IntelliJ bajo Run > Edit Configurations > Environment Variables:

```
SPRING_DATASOURCE_USERNAME=sgl_user
SPRING_DATASOURCE_PASSWORD=sgl_pass
JWT_SECRET=dev-local-secret-change-me-32bytes
SPRING_PROFILES_ACTIVE=dev
MAILTRAP_API_TOKEN=<tu_token>
```

**3. Ejecutar la aplicación**

```bash
./mvnw spring-boot:run
```

- API disponible en: http://localhost:8080
- Health check: http://localhost:8080/api/health
- Swagger UI: http://localhost:8080/swagger-ui.html

Con el perfil `dev` activo, el sistema carga datos de prueba automáticamente al iniciar.

## Pruebas

```bash
./mvnw test
```

Reporte de cobertura con JaCoCo (minimo 60% de líneas):

```bash
./mvnw test jacoco:report
open target/site/jacoco/index.html
```
