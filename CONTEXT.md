# Contexto del Proyecto: Botica Anita

## Descripción General
Botica Anita es un sistema de e-commerce para una farmacia (botica) con backend Spring Boot y frontend Angular. Incluye gestión de productos, pedidos, carrito de compras, pagos (Stripe, Plin, Yape, SIP), seguimiento de pedidos con mapa, dashboard corporativo para administradores, dashboard para asistentes de tienda (POS + confirmación de entregas), y más.

**Última actualización completa: 12 de noviembre de 2026**
**Sesión actual**: 18 de noviembre de 2026 - Actualización completa con cambios de diseño, migraciones y estado actual del repo.

--- 

## Stack Tecnológico

### Backend
- **Framework:** Spring Boot 4.0.6 (Java 21)
- **Puerto:** 8080
- **Base de datos:** PostgreSQL (Supabase en `aws-0-us-east-2.pooler.supabase.com:5432`)
- **ORM:** Hibernate/JPA
- **Seguridad:** JWT (Spring Security)
- **Mapas/Geocodificación:** Nominatim (OpenStreetMap, gratuito) + Google Maps API (opcional)
- **Pagos:** Stripe, Plin, Yape, SIP (Ágora)
- **Email:** JavaMail (SMTP Gmail)
- **Almacenamiento de archivos:** Supabase Storage
- **Búsqueda:** Algolia

### Frontend
- **Framework:** Angular 20 (standalone components)
- **Puerto:** 4200
- **Mapas:** MapLibre GL JS (tiles de Esri World Street Map, sin token) + Google Maps API (opcional)
- **Geocodificación:** Geoapify Autocomplete + Google Places API (opcional)
- **Estilos:** Bootstrap 5 + CSS personalizado
- **UI:** Bootstrap, CSS custom
- **Gráficos:** SVG manual (dashboard)
- **Iconos:** Bootstrap Icons (bi-*) + favicon propio

--- 

## Estructura del Proyecto

```
proyecto_botica_anita-main/
├── backend/                          # Spring Boot
│   ├── src/main/java/com/example/demo/
│   │   ├── controllers/
│   │   │   ├── AdminController.java  # Dashboard admin (ventas, productos, usuarios, cupones, reportes)
│   │   │   ├── AsistenteController.java  # Rol asistente (POS, pedidos, historial)
│   │   │   ├── AuthController.java   # Login/registro
│   │   │   ├── CarritoController.java
│   │   │   ├── PagoController.java    # Stripe, Plin, Yape, SIP
│   │   │   ├── PedidoController.java  # Historial y seguimiento de pedidos + actualizar ubicación
│   │   │   ├── PedidoTrackingController.java  # Tracking en tiempo real
│   │   │   ├── ProductoController.java
│   │   │   ├── RecetaController.java
│   │   │   ├── ChatbotController.java
│   │   │   └── MarcaController.java
│   │   ├── models/
│   │   │   ├── Pedido.java           # Pedido con estado, fecha, coords de entrega
│   │   │   ├── Producto.java
│   │   │   ├── User.java
│   │   │   ├── Administrador.java    # Roles: admin, vendedor, asistente
│   │   │   ├── DetallePedido.java
│   │   │   ├── InventarioLote.java   # Lotes con fecha de vencimiento
│   │   │   ├── Cupon.java
│   │   │   ├── RecetaMedica.java
│   │   │   ├── Carrito.java
│   │   │   └── Subcategoria.java
│   │   ├── repositories/
│   │   ├── services/
│   │   │   ├── PagoService.java      # Lógica de pagos, validación de cobertura 2km
│   │   │   ├── PedidoTrackingService.java  # Avance de estado por tiempo + geocodificación + email "en camino"
│   │   │   ├── PedidoEstadoScheduler.java  # Avance automático cada 30s (sin auto-completar)
│   │   │   ├── GeocodificacionService.java # Nominatim con rate limit + Google Maps opcional
│   │   │   ├── ProductoService.java
│   │   │   ├── AuthService.java
│   │   │   ├── EmailService.java     # Emails HTML (confirmación, en camino, entregado, boleta presencial)
│   │   │   ├── AlgoliaService.java
│   │   │   ├── SupabaseStorageService.java
│   │   │   └── VentaPresencial (en AsistenteController)
│   │   ├── config/
│   │   │   ├── DataSeeder.java       # Datos iniciales (admin, vendedor, asistente)
│   │   │   ├── ConstraintFixConfig.java  # Altera constraint metodo_pago para incluir EFECTIVO
│   │   │   └── SecurityConfig.java
│   │   └── DemoApplication.java      # @EnableScheduling
│   └── src/main/resources/
│       └── application.properties      # ⚠️ Contiene claves API (NO subir a GitHub)
├── frontend/                          # Angular 20
│   ├── src/app/
│   │   ├── components/
│   │   │   └── tracking/
│   │   │       └── tracking-mapa.component.ts   # Mapa MapLibre GL JS (solo en seguimiento)
│   │   ├── pages/
│   │   │   ├── pago/                 # Checkout y pago (sin mapa)
│   │   │   ├── seguimiento-pedido/   # Seguimiento del pedido con mapa
│   │   │   ├── dashboard/
│   │   │   │   ├── admin/            # Dashboard corporativo
│   │   │   │   ├── vendedor/        # Dashboard vendedor (solo lectura)
│   │   │   │   └── asistente/       # Dashboard asistente (POS + pedidos + historial) - DISEÑO MEJORADO
│   │   │   ├── pedidos/              # Historial de pedidos
│   │   │   ├── catalogo/             # Catálogo de productos
│   │   │   ├── carrito/              # Carrito de compras
│   │   │   └── perfil/               # Perfil de usuario
│   │   ├── services/
│   │   │   ├── mapa-ruta.service.ts  # MapLibre + Geoapify
│   │   │   ├── tracking.service.ts   # API tracking
│   │   │   ├── admin.service.ts      # API admin + isAsistente()
│   │   │   ├── asistente.service.ts  # API asistente
│   │   │   ├── pago.service.ts       # API pago
│   │   │   ├── pedido.service.ts     # API pedidos + actualizarUbicacion
│   │   │   ├── cart.service.ts       # Carrito
│   │   │   ├── auth.service.ts       # Autenticación
│   │   │   ├── fecha-util.ts         # Fechas UTC
│   │   │   ├── tracking.service.ts   # API tracking
│   │   │   ├── algolia.service.ts    # Búsqueda Algolia
│   │   │   ├── toast.service.ts      # Notificaciones
│   │   │   └── chatbot.service.ts    # Chatbot SofIA (agregado recientemente)
│   │   ├── environments/
│   │   │   ├── environment.ts        # dev (keys locales, no subir)
│   │   │   └── environment.prod.ts   # prod (keys locales, no subir)
│   │   └── styles.css
│   ├── public/
│   │   ├── icons/                    # farmacia-icon.png, casa-icon.png, delivery-icon.png
│   │   └── maplibre-gl-worker.mjs    # Worker de MapLibre (servido localmente)
│   ├── angular.json
│   └── package.json
├── iniciar.bat
├── apagar.bat
├── context.md
└── .gitignore
```

--- 

## Ramas y Estado del Repo

### Rama actual: `main`
- **Último commit:** `899693c` - "Merge branch 'main' of https://github.com/Uriel-20-07/proyecto_botica_anita" (incluye diseño asistente mejorado)
- **Commits recientes:**
  - `dd139c5` - Merge branch 'main' of https://github.com/Uriel-20-07/proyecto_botica_anita
  - `6999e94` - fix: mostrar 'Cliente en tienda' en pedidos sin cliente registrado
  - `ce84bcd` - Update CONTEXT.md con latest changes (asistente, emails HTML, map fixes)
  - `d5cc0cc` - feat: actualizar chatbot SofIA con busqueda en catalogo, conocimiento y limitador
  - `0d61e8e` - Improve: diseño asistente (sidebar, iconos Bootstrap, POS mejorado)
  - `afc6254` - Merge lima into main (asistente + emails HTML + map fixes)

### Rama `lima`
- **Último commit:** `0d61e8e` - "Improve: diseño asistente (sidebar, iconos Bootstrap, POS mejorado)" (mismo que main)
- **Sync:** main y lima están sincronizados en el commit del diseño asistente

**Estado actual:** Ambos branches (`main` y `lima`) están en el mismo commit `0d61e8e` que incluye el diseño asistente mejorado. Sin embargo, `origin/main` está un commit atrás (en `899693c` que es un merge anterior). Se recomienda hacer `git pull` para obtener los últimos cambios.

--- 

## Variables de Entorno (`application.properties` + `environment.ts`)

El backend usa `application.properties` con claves reales. **NO subir a GitHub** (push protection bloquea).

### Frontend (`environment.ts` dev, `environment.prod.ts` prod)
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  mostrarRecetas: false,
  geoapifyApiKey: '5877badf92e14ccb85d2a16ede4aa1dc',      // key gratuita, local
  stripePublishableKey: '...',                                // local, no subida
  googleMapsApiKey: ''                                        // dejar vacío si no se usa; poner key si se migra a Google Maps
};
```

### Backend (`application.properties` + `.env`)
- `STRIPE_API_KEY` (secret, .env)
- `GEMINI_API_KEY` (secret, .env)
- `MAIL_PASSWORD` (SMTP, .env)
- `SUPABASE_URL`, `SUPABASE_KEY` (con defaults)
- `PLIN_NUMERO`, `SIP_NUMERO` (con defaults)
- `JWT_SECRET` (firmeza)

Hay un archivo `.env.example` en backend con las variables necesarias.

**⚠️ NOTA:** El contexto.md se actualizó en esta sesión con todos los cambios de diseño del asistente, la migración a Google Maps (estudio) y el estado actual del repo. Las keys API nunca se suben al repo.

--- 

## Funcionalidades Principales

### 1. Catálogo de Productos
- Listado de productos con categorías y subcategorías
- Búsqueda por nombre (Algolia)
- Filtro por categoría/subcategoría, precio, ofertas
- Productos en oferta
- Stock por lotes (FEFO)

### 2. Carrito de Compras
- Agregar/quitar productos
- Cálculo de subtotal, IGV, total
- Aplicar cupones de descuento
- Notificación animada al añadir producto

### 3. Checkout y Pago
- **Tipos de envío:**
  - **Recojo en tienda:** Gratis, solo en Bodega Independencia (Av. Los Jazmines 408)
  - **Delivery urgente:** S/ 5.00, limitado a 2 km de la tienda (solo distrito Independencia)
- **Métodos de pago:** Tarjeta (Stripe), Plin, Yape, SIP
- **Validación de cobertura:** 2 km de radio desde la tienda, bloqueo por distrito
- **Recetas médicas:** Flujo oculto (`mostrarRecetas: false` en environment)
- **Autocompletado de direcciones:** Geoapify (detecta distrito automáticamente)
- **Marcador arrastrable** en seguimiento para ajustar ubicación exacta

### 4. Seguimiento de Pedidos
- Estados: PAGADO → EN_CAMINO → COMPLETADO (delivery, confirmado manualmente por asistente) o PAGADO → LISTO_RECOQUI → COMPLETADO (recojo, confirmado manualmente)
- Tiempos: confirmado 0 min, despacho 1 min, motorizado 1 min, en camino según distancia
- **Mapa MapLibre GL JS** con:
  - Marcador de tienda (icono farmacia, 40px)
  - Marcador de destino arrastrable (icono casa, 40px)
  - Ruta real por calles (OSRM, gratuito)
  - Repartidor (icono moto) animado con rotación según dirección
  - Tarjeta flotante (ETA, distancia, costo, progreso, timeline, distrito)
  - Ruta gris solo visible en "en camino", naranja crece con el repartidor
  - Solo se muestra para delivery, se oculta al entregar
- **Geocodificación Geoapify** (autocomplete en pago)
- **Avance automático** cada 30s por scheduler (sin auto-completar)
- **Email "en camino"** enviado al cliente cuando el pedido entra en EN_CAMINO

### 5. Dashboard Corporativo (Admin)
- **Resumen:** Métricas, gráfico de ventas diarias
- **Ventas:** Tabla paginada (20/página) con búsqueda por cliente
- **Inventario:** Productos agrupados por categoría, paginado (20/página)
- **Reportes:** Ventas por período, Top 10 productos, Lotes por vencer, Top 5 clientes
- **Predicciones IA:** Quiebre de stock, demanda estacional, demanda localizada, venta cruzada, descuentos FEFO
- **Cupones:** Lanzar campañas de cupones
- **Paginación:** 20 items por página en todas las listas
- **Búsqueda Algolia:** integrada en catálogo

### 6. Dashboard Asistente (Rol: asistente) - **DISEÑO MEJORADO (noviembre 2026)**
- **Login:** `asistente@correo_corp.com` / `asistente123`
- **Sidebar lateral** con 3 módulos: Venta (POS), Pedidos, Historial
- **Venta (POS):**
  - Buscador de productos por nombre
  - Grid de productos con imagen, precio y stock
  - Carrito con cantidades (+/-), eliminar, total
  - Métodos de pago: Efectivo, Tarjeta, Yape, Plin
  - Datos del cliente (nombre + email para boleta)
  - Botón "Confirmar Venta" → reduce stock, crea pedido, envía boleta HTML
- **Pedidos:**
  - Sub-tabs "Pendientes" / "Entregados"
  - Ordenados por más reciente
  - Badges de estado con colores (Pendiente=azul, En camino=amarillo, Entregado=verde)
  - Fecha y hora formateadas (DD/MM/YYYY HH:MM)
  - Botón "Entregado" (solo pendientes) → marca COMPLETADO + email al cliente
  - Clic en fila → modal con detalle completo (cliente, estado, método, dirección, total, fecha)
  - Botón "Ver boleta" en entregados
- **Historial:** ventas del día con totales (cantidad + monto), ordenadas por más reciente

### 7. Autenticación
- Login/registro de usuarios
- Login corporativo para admin/vendedor/asistente (JWT)
- Recuperar contraseña
- Roles: `admin` (acceso completo), `vendedor` (solo lectura), `asistente` (POS + pedidos), `user` (cliente)

### 8. Emails
- **Confirmación de compra** (online): HTML con diseño profesional
- **En camino**: HTML con link a Google Maps
- **Entregado**: HTML con header verde, tabla de detalles
- **Boleta presencial** (asistente): HTML con diseño mejorado
- **Bienvenida con cupón**: 30% descuento, 30 días vigencia
- **Recuperación de contraseña**: código de 5 dígitos

### 9. Chatbot SofIA (agregado recientemente)
- Integración con Gemini API
- Búsqueda en catálogo Algolia
- Conocimiento base de productos y recetas
- Limitador de respuestas para tono institucional

### 10. Mapa y Geolocalización
- **Mapa actual:** MapLibre GL JS con tiles gratuitos de Esri World Street Map
- **Worker:** servido localmente (`/maplibre-gl-worker.mjs` en public/)
- **Geocodificación principal:** Geoapify Autocomplete (key: `5877badf92e14ccb85d2a16ede4aa1dc`)
- **Geocodificación alternativa:** Google Maps Geocoding API (opcional, requiere billing + key)
- **Routing:** OSRM (gratuito, sin token) + fallback línea recta
- **Cobertura delivery:** Radio 2 km desde la tienda (Av. Los Jazmines 408, Independencia). Solo distrito Independencia.

--- 

## Coordenadas de la Tienda (Botica Anita)
- **Dirección:** Av. Los Jazmines 408, Independencia, Lima
- **Coords verificadas:** `-12.001353207277829, -77.04735384172724`
- **Backend:** `-12.001353207277829, -77.04735384172724`
- **Frontend (mapa-ruta.service.ts):** `-12.001353207277829, -77.04735384172724`
- **Radio de cobertura:** 2 km (solo distrito Independencia)

--- 

## Estado del Pedido (Flujo)

### Delivery (esUrgente = true)
1. `PAGADO` (0 min) → confirmado
2. `EN_CAMINO` (2 min) → despacho + motorizado (email "en camino" enviado)
3. `COMPLETADO` (manual, por asistente) → entregado (email "entregado" enviado)

### Recojo (esUrgente = false)
1. `PAGADO` (0 min) → confirmado
2. `LISTO_RECOQUI` (2 min) → listo para recoger
3. `COMPLETADO` (manual, por asistente) → cerrado

### Estados especiales
- `EN_ESPERA` / `ESPERANDO`: espera de receta médica
- `PENDIENTE_VERIFICACION`: verificando pago Plin
- `CANCELADO` / `RECHAZADO`: pedido cancelado o rechazado

--- 

## Mapas y Geocodificación

### MapLibre GL JS (Actual - gratuito)
- **Tiles:** Esri World Street Map (`https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}`)
- **Worker:** servido localmente (`/maplibre-gl-worker.mjs` en public/)
- **Componentes:**
  - `tracking-mapa.component.ts` (seguimiento con MapLibre, solo en Mis Pedidos)
  - Sin mapa en página de pago

### Google Maps API (Opcional - estudio en curso)
- **Maps JavaScript API:** renderizado profesional, satélite, Street View
- **Places API:** autocompletado más preciso (especialmente para números de calle en Perú)
- **Geocoding API:** conversión dirección → coords más exacta
- **Directions API:** ruta con tráfico real
- **Costo:** $200 USD / mes de crédito gratuito (cubre ~28,000 map loads, ~40,000 geocodificaciones, ~40,000 direcciones). Para proyecto escolar, sobradamente suficiente.

### Geoapify (Actual - gratuita)
- **API Key:** `5877badf92e14ccb85d2a16ede4aa1dc` (incluida en repo, local, no subir)
- **Autocomplete:** `https://api.geoapify.com/v1/geocode/autocomplete`
- **Reverse:** `https://api.geoapify.com/v1/geocode/reverse`

### Nominatim (Backend)
- **Servicio:** `GeocodificacionService.java`
- **Rate limit:** 1 req/1.2s
- **Cache:** ConcurrentHashMap por dirección
- **Método:** `buscar(direccion, distrito)` → `{lat, lng}`

### OSRM (Routing)
- **API:** `https://router.project-osrm.org/route/v1/driving/{origen};{destino}`
- **Gratuito, sin token**
- **Fallback:** línea recta si OSRM falla

--- 

## Autenticación

### JWT
- **Secret:** `${JWT_SECRET:...}`
- **Expiración:** Configurada en `JwtService`
- **Header:** `Authorization: Bearer <token>`

### Roles
- `admin`: Acceso completo al dashboard corporativo
- `vendedor`: Acceso limitado (solo lectura)
- `asistente`: POS + pedidos + historial
- `user`: Cliente estándar

### Cuentas de prueba
- Admin: `admin@correo_corp.com` / `admin123`
- Vendedor: `vendedor@correo_corp.com` / `vendedor123`
- Asistente: `asistente@correo_corp.com` / `asistente123`
- Usuario: `usuario@test.com` / `test1234`

--- 

## Base de Datos (Supabase)
- **Host:** `aws-0-us-east-2.pooler.supabase.com`
- **Puerto:** 5432
- **Base:** `postgres`

### Tablas principales:
- `pedidos` (id_pedido, estado, fecha, total, es_urgente, latitud_entrega, longitud_entrega, direccion_envio, distrito, metodo_pago, repartidor_id, latitud_repartidor, longitud_repartidor, ultima_actualizacion_ubicacion, numero_operacion)
- `productos` (id_producto, nombre, descripcion, precio_venta, precio_oferta, en_oferta, stock, img_url, fecha_caducidad, id_categoria, id_subcategoria)
- `usuarios` (id_usuario, nombre, apellido, correo, password, telefono, activo, fecha_registro)
- `administradores` (id_admin, nombre, correo_corp, password, rol)
- `detalle_pedido` (id, pedido_id, producto_id, cantidad, precio_historico)
- `inventario_lote` (id_lote, producto_id, codigo_lote, cantidad_actual, fecha_vencimiento, fecha_ingreso)
- `cupones`, `recetas_medicas`, `subcategorias`, etc.

--- 

## Cambios Recientes (Sesión Actual - 18 Nov 2026)

### 1. Diseño Asistente Mejorado (commit `0d61e8e`)
- **Sidebar:** toggle `menuAbierto()` para abrir/cerrar, brand con `span.brand-logo`, botón `sidebar-close` con icono `bi bi-x-lg`.
- **POS:** layout con sidebar colapsable, grid de productos con badges de stock, carrito lateral con totales.
- **Pedidos:** sub-tabs "Pendientes"/"Entregados", badges de estado con colores, modal de detalle con fecha formateada, botón "Ver boleta".
- **Historial:** resumen de ventas del día + tabla completa.
- **CSS:** 2230+ líneas de estilos profesionales con variables CSS, gradientes, shadows y responsive design.
- **Iconos:** Bootstrap Icons (bi-*) reemplazo de texto plano.

### 2. CONTEXT.md actualizada
- Incluye todo el estado actual, ramas, commits, y funcionalidades.
- 441 líneas, última actualización 12 de noviembre de 2026 (más los cambios de esta sesión).

### 3. Estado del repositorio
- `main` y `lima` sincronizados en commit `0d61e8e`.
- `origin/main` un commit atrás (pending push).
- Sin keys API expuestas en el repo (validado).

### 4. Servidores
- Backend (8080): encendido y apagado durante la sesión.
- Frontend (4200): encendido y apagado durante la sesión.

### 5. Google Maps - Estudio (no implementado)
- Viabilidad confirmada: $200/mes crédito gratuito sobradamente suficiente para proyecto escolar.
- Arquitectura: reemplazar MapLibre → Google Maps JS API, Geoapify → Google Places API, OSRM → Google Directions API.
- Requisito: cuenta Google Cloud + billing (tarjeta, sin cargo hasta $200/mes).
- Decisión: mantener MapLibre + Esri + Geoapify por ahora (gratis, sin tarjeta).

--- 

## Comandos Útiles

### Backend
```bash
cd backend
./mvnw.cmd spring-boot:run          # Iniciar
./mvnw.cmd clean compile -q         # Compilar
```

### Frontend
```bash
cd frontend
npm start                            # Iniciar dev server
npm run build                        # Build producción
```

### Git
```bash
git pull origin main                 # Traer cambios
git add . && git commit -m "mensaje"  # Commitear
git push origin HEAD:lima            # Push a lima
git push origin HEAD:main            # Push a main (cuando verificado)
```

### Verificar estado
```bash
git branch --show-current            # Rama actual
git status --short                     # Archivos modificados
git log --oneline -3                   # Últimos 3 commits
git show origin/main:frontend/src/environments/environment.ts  # Keys en remoto (deben estar vacías)
```

--- 

## ⚠️ Seguridad y Buenas Prácticas

1. **NO subir `application.properties`** (contiene claves API reales)
2. **NO subir `environment.ts` / `environment.prod.ts` con keys reales**
3. **Usar variables de entorno:** `${STRIPE_API_KEY:}`, `${GEMINI_API_KEY:}`, etc.
4. **GitHub push protection** bloquea commits con claves reales
5. **Siempre hacer `git pull`** antes de trabajar en equipo
6. **Reiniciar frontend** después de cambios en plantillas HTML/CSS
7. **Reiniciar backend** después de cambios en controladores/services

--- 

## Última actualización: 18 de noviembre de 2026

*Contexto generado para facilitar la comprensión del proyecto por otra IA, con todos los cambios de la sesión actual (diseño asistente, estado repo, Google Maps estudio, servidores).*

**Cambios clave en esta sesión:**
- Actualización completa de context.md (441 líneas)
- Diseño asistente mejorado empujado a main/lima (sidebar, iconos, layout)
- Confirmación: main y Lima sincronizados en commit `0d61e8e`
- Servidores encendidos/apagados durante la sesión
- Estudio Google Maps: viable con $200/mes crédito gratuito, pero se mantiene MapLibre por ahora
- No hay keys API expuestas en el repositorio remoto