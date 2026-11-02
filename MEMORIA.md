# MEMORIA — Proyecto Botica Anita

> Archivo de trabajo temporal. El usuario lo eliminará antes de subir cambios. No tocar `.gitignore` ni nada relacionado.

## Objetivo actual

Implementar la **confirmación de correo en el registro de nuevas cuentas**.
Alcance estricto: **solo registro**. No se tocó el login ni el flujo de recuperación de contraseña.

## Flujo implementado (3 pasos, dentro del modal)

1. **Paso 1 — Credenciales**
   - Email, contraseña y confirmación.
   - Al pulsar "Continuar", el frontend llama a `POST /api/auth/solicitar-codigo` con `reenviar = false`.
   - El backend valida que el correo esté disponible y **asegura que exista un código vigente**:
     - Si no hay código (o expiró), genera uno de **6 dígitos**, lo guarda hasheado y lo envía por correo.
     - Si ya hay un código vigente, **lo reutiliza y no envía otro correo**.
   - El modal pasa al paso 2 con "Enviamos un código..." o "Ya te enviamos un código...", según corresponda.

2. **Paso 2 — Datos personales**
   - Nombre, apellido, DNI/CE, teléfono y aceptación de términos.
   - Al pulsar "Continuar", solo se valida el formulario en local y se pasa al paso 3. **No** se llama al backend.

3. **Paso 3 — Código de verificación**
   - Campo de código de 6 dígitos + botón "Reenviar código".
   - Al pulsar "Crear cuenta", `POST /api/auth/registro` recibe todos los datos + `codigo`.
   - El backend valida el código y **crea la cuenta ya verificada** (`emailVerificado = true`), elimina el código usado y envía el cupón de bienvenida.
   - El modal vuelve al login con "Correo confirmado. Ya puedes iniciar sesión."

La cuenta **no existe** hasta que el código es correcto, así que no quedan cuentas basura ni se puede "reservar" el correo de otra persona.

## Stack

- **Frontend:** Angular 20 (standalone components, `@if`, Reactive Forms). `frontend/`.
- **Backend:** Spring Boot 4.0.6, Java 21, PostgreSQL (Supabase), Spring Mail. `backend/`.
- API base en desarrollo: `http://localhost:8080` (`frontend/src/environments/environment.ts`).
- `spring.jpa.hibernate.ddl-auto=update`: la tabla nueva se crea sola.

## Backend — implementado

### Archivos nuevos

| Archivo | Rol |
|---|---|
| `backend/src/main/java/com/example/demo/models/EmailVerificationCode.java` | Entidad del código temporal (tabla `codigos_verificacion_email`) |
| `backend/src/main/java/com/example/demo/repositories/EmailVerificationCodeRepository.java` | Repositorio `findByEmail` |

### Archivos modificados

| Archivo | Cambio |
|---|---|
| `controllers/AuthController.java` | Nuevo `POST /api/auth/solicitar-codigo` (acepta `reenviar`); `/registro` ahora recibe `codigo` |
| `services/AuthService.java` | `enviarCodigoVerificacion(email, reenviar)`, `validarCodigoVerificacion()` y `registrar()` actualizado |
| `services/EmailService.java` | Nuevo `sendVerificationCodeEmail()` (devuelve `boolean`) |

### Detalles del código

- Formato: **6 dígitos numéricos** (`100000`–`999999`), generados con `SecureRandom`.
- Almacenamiento: **hash BCrypt**, nunca en texto plano.
- Tabla `codigos_verificacion_email`: `correo` (único), `codigo_hash`, `fecha_creacion`, `fecha_expiracion`, `intentos`.
- Expiración: **15 minutos** (`AuthService.CODIGO_EXPIRACION_MINUTOS`).
- Intentos máximos: **5** (`CODIGO_MAX_INTENTOS`). Al agotarlos se borra el código y hay que reenviar.
- Cooldown entre envíos: **60 segundos** por correo (`CODIGO_REENVIO_COOLDOWN_SEGUNDOS`).
- Reutilización: si ya existe un código vigente y `reenviar = false`, **no se envía correo** (`enviado = false`). Solo el botón "Reenviar código" manda `reenviar = true`.
- Si el envío SMTP falla, se borra el código para permitir reintentar de inmediato.
- `registrar()` valida el código **antes** de crear la cuenta. La cuenta nace con `emailVerificado = true`.
- El login **no se modificó**: no se agregó bloqueo por email no verificado. Las cuentas nuevas ya nacen verificadas; las antiguas siguen funcionando como antes.
- El endpoint `/api/auth/verificar-email` se mantiene, aunque el modal ya no lo usa.
- Si falla la creación/envío del cupón de bienvenida, la cuenta igual queda creada (se registra el error en consola).

### Endpoints

- `POST /api/auth/solicitar-codigo`
  - Body: `{ "email": "usuario@email.com", "reenviar": "false" }` (`reenviar` es opcional)
  - 200: `{ "mensaje": "Código enviado al correo" | "Ya existe un código vigente para este correo", "enviado": true|false, "expiracionMinutos": 15 }`
  - 400: `{ "error": "Este correo ya está registrado" | "Espera N segundos..." | "No se pudo enviar..." }`

- `POST /api/auth/registro`
  - Body: `{ email, password, nombre, apellido, telefono, tipoDocumento, numeroDocumento, aceptaTerminos, codigo }`
  - 201: `{ mensaje, usuario: { id, email, nombre, apellido } }`
  - 400: `{ "error": "Código incorrecto. Te quedan N intentos" | "El código expiró..." | ... }`

## Frontend — implementado

### Archivos modificados

| Archivo | Cambio |
|---|---|
| `frontend/src/app/services/auth.service.ts` | Nuevo `solicitarCodigo(email, reenviar)`; `registro()` recibe `codigo` |
| `frontend/src/app/components/auth-modal/auth-modal.component.ts` | Formulario `verificacionForm`, `continuarDatos()`, `confirmarRegistro()`, `volverADatos()`, cooldown, `reenviarCodigo()`, `soloNumerosCodigo()` y persistencia en `sessionStorage` |
| `frontend/src/app/components/auth-modal/auth-modal.component.html` | Stepper de 3 pasos, paso 3 con campo de código y botón reenviar |
| `frontend/src/app/components/auth-modal/auth-modal.component.css` | `.code-input`, `.mini-link:disabled` |

### Notas

- El stepper ahora tiene **3 pasos**: credenciales → datos → código.
- El código se envía en el paso 1, así que llega mientras el usuario completa sus datos.
- `verificarEmail()` quedó sin uso en el modal, pero se conserva en el servicio.
- El cooldown visual del botón "Reenviar código" es de 60 s; el backend también lo valida.
- Si el usuario vuelve al paso 1 y cambia el correo, al continuar se envía un código nuevo (el anterior queda huérfano hasta expirar; es inofensivo).
- Si vuelve al paso 1 con el **mismo** correo, el backend reutiliza el código vigente y **no manda otro correo**.
- **Persistencia del registro (`sessionStorage`, clave `registroPendiente`):**
  - Se guarda email, contraseña, confirmación y datos personales, más el paso actual y un timestamp.
  - Al recargar la página, el modal se reabre solo en el paso donde se quedó el usuario, con "Recuperamos tu registro. Continúa donde lo dejaste."
  - Se borra al cerrar el modal, al cambiar a otra pestaña del modal, al completar el registro o al vencer (**30 min**).
  - Se ignora si el usuario ya tiene sesión iniciada.
  - **Ojo:** la contraseña se guarda en `sessionStorage` (vive solo en esa pestaña y se borra al cerrarla). Si se prefiere no guardarla, habría que volver al paso 1 al recargar.
- El cooldown del botón "Reenviar código" se restaura con el tiempo restante si el código se envió hace menos de 60 s.
- **No se pudo compilar el frontend en este entorno porque no hay Node/npm instalado.** El backend sí compila (`./mvnw -q -DskipTests compile` sin errores).

## Cómo probar

1. Levantar backend (`./mvnw spring-boot:run`) y frontend (`npm start` o `ng serve`).
2. Abrir el modal → pestaña **Registrar**.
3. **Paso 1:** usar un correo no registrado + contraseña (mín. 6) + confirmación → **Continuar**.
   - Debe llegar un correo de BoticaAnita con un código de 6 dígitos.
   - El modal debe pasar al paso 2 mostrando "Enviamos un código...".
4. **Paso 2:** completar nombre, apellido, DNI, teléfono y aceptar términos → **Continuar**.
5. **Paso 3:** ingresar el código de 6 dígitos → **Crear cuenta**.
   - Debe volver al login con "Correo confirmado. Ya puedes iniciar sesión."
   - Debe llegar el correo del cupón de bienvenida.
6. Verificar en BD que el usuario se creó con `email_verificado = true` y que la fila del código desapareció.

### Casos a probar

- **Recarga en el paso 2 o 3:** recargar la página (F5) a mitad del registro → el modal debe reabrirse en el mismo paso, con los datos cargados y el mensaje "Recuperamos tu registro...". **No** debe llegar un correo nuevo.
- **Volver al paso 1 con el mismo correo:** pulsar "Continuar" → debe avanzar sin enviar otro correo ("Ya te enviamos un código...").
- **Reenviar código:** el botón respeta el cooldown de 60 s; si se fuerza, el backend responde "Espera N segundos" y el contador se ajusta.
- **Cerrar el modal a mitad del registro:** al reabrir, debe empezar de cero (el borrador se descarta).
- **Código incorrecto** → mensaje con intentos restantes (máx. 5).
- **Código expirado** (15 min) → "El código expiró. Solicita uno nuevo."
- **Correo ya registrado** en el paso 1 → "Este correo ya está registrado".
- **Cambiar el correo** en el paso 1 → se envía un código nuevo.
- **Documento repetido** → "El documento ya está registrado".

## Decisiones tomadas

- Solo se modificó el flujo de **registro**; login y recuperación quedan intactos.
- Confirmación por **código de 6 dígitos** dentro del modal, sin ruta nueva.
- **3 pasos**: credenciales → datos personales → código. El código se asegura en el paso 1 y se valida en el paso 3.
- La cuenta se crea **solo después** de confirmar el código.
- El paso 1 **reutiliza** el código vigente en vez de reenviar: evita correos duplicados al recargar o volver atrás.
- El progreso del registro se guarda en `sessionStorage` para sobrevivir a una recarga.
- Se reutilizó `EmailService` y el `BCryptPasswordEncoder` existentes.
- `MEMORIA.md` vive en la raíz y es temporal (no se sube al repo).

## Posibles mejoras (no implementadas)

- Rate limit por IP además del cooldown por correo.
- Job de limpieza de códigos expirados (la tabla guarda una fila por correo).
- Bloquear login de cuentas no verificadas (no se tocó login por pedido explícito).
- Enviar el código en un correo HTML con mejor diseño.
- Reutilizar `/verificar-email` o eliminarlo si se confirma que nadie más lo usa.

## Bitácora

- **2026-11-01:** Revisado el frontend del registro (`auth-modal` + `auth.service` + rutas). Creado este archivo.
- **2026-11-01:** Definido el flujo de 3 pasos y luego la propuesta de 2 pasos (código en el paso 1).
- **2026-11-01:** Revisado el backend: `AuthController`, `AuthService`, `EmailService`, `User`, `PasswordResetToken`, `SecurityConfig`, `pom.xml`, `application.properties`.
- **2026-11-01:** Implementado backend + frontend del flujo de 2 pasos. Backend compila. Frontend pendiente de build/prueba por falta de Node/npm en el entorno.
- **2026-11-01:** El usuario probó el flujo de 2 pasos y funcionó. Pidió separarlo en 3 pasos por UX. Implementado en frontend (backend sin cambios). Pendiente nueva prueba.
- **2026-11-01:** Ajuste UX: si el paso 1 se repite dentro del cooldown de 60 s, el frontend deja avanzar usando el código ya enviado.
- **2026-11-01:** El usuario preguntó qué pasa al recargar a mitad del registro. Detectado que se reenviaba un correo y se invalidaba el código anterior.
- **2026-11-01:** Implementado: backend reutiliza el código vigente (`reenviar = false`) y frontend persiste el progreso en `sessionStorage` (30 min). Pendiente de prueba del usuario.
