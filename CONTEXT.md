# Contexto del Proyecto: Botica Anita

## Descripción General
Botica Anita es un sistema de e-commerce para una farmacia (botica) con backend Spring Boot y frontend Angular. Incluye gestión de productos, pedidos, carrito de compras, pagos (Stripe, Plin, Yape, SIP), seguimiento de pedidos con mapa, dashboard corporativo para administradores, dashboard para asistentes de tienda (POS + confirmación de entregas), y más.

---

## Stack Tecnológico

### Backend
- **Framework:** Spring Boot 4.0.6 (Java 21)
- **Puerto:** 8080
- **Base de datos:** PostgreSQL (Supabase en `aws-0-us-east-2.pooler.supabase.com:5432`)
- **ORM:** Hibernate/JPA
- **Seguridad:** JWT (Spring Security)
- **Mapas/Geocodificación:** Nominatim (OpenStreetMap, gratuito)
- **Pagos:** Stripe, Plin, Yape, SIP (Ágora)
- **Email:** JavaMail (SMTP Gmail)
- **Almacenamiento de archivos:** Supabase Storage
- **Búsqueda:** Algolia

### Frontend
- **Framework:** Angular 20 (standalone components)
- **Puerto:** 4200
- **Mapas:** MapLibre GL JS (tiles de Esri World Street Map, sin token)
- **Geocodificación:** Geoapify Autocomplete
- **Estilos:** Bootstrap 5 + CSS personalizado
- **UI:** Bootstrap, CSS custom
- **Gráficos:** SVG manual (dashboard)

### Comandos
- Iniciar ambos: `iniciar.bat` (raíz del proyecto)
- Apagar: `apagar.bat`
- Backend solo: `cd backend && ./mvnw.cmd spring-boot:run`
- Frontend solo: `cd frontend && npm start`

---

## Estructura del Proyecto

```
proyecto_botica_anita-main/
├── backend/                          # Spring Boot
│   ├── src/main/java/com/example/demo/
│   │   ├── controllers/
│   │   │   ├── AdminController.java  # Dashboard admin (ventas, productos, usuarios, cupones)
│   │   │   ├── AsistenteController.java  # Rol asistente (POS, pedidos, historial)
│   │   │   ├── AuthController.java   # Login/registro
│   │   │   ├── CarritoController.java
│   │   │   ├── PagoController.java    # Stripe, Plin, Yape, SIP
│   │   │   ├── PedidoController.java  # Pedidos del cliente + actualizar ubicación
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
│   │   │   ├── PedidoTrackingService.java  # Avance de estado por tiempo + geocodificación
│   │   │   ├── PedidoEstadoScheduler.java # Avance automático cada 30s (sin auto-completar)
│   │   │   ├── GeocodificacionService.java # Nominatim con rate limit
│   │   │   ├── ProductoService.java
│   │   │   ├── AuthService.java
│   │   │   ├── EmailService.java     # Emails HTML (confirmación, en camino, entregado, boleta presencial)
│   │   │   ├── AlgoliaService.java
│   │   │   ├── SupabaseStorageService.java
│   │   │   └── VentaPresencial (en AsistenteController)
│   │   ├── config/
│   │   │   ├── DataSeeder.java       # Datos iniciales (admin, vendedor, asistente)
│   │   │   ├── ConstraintFixConfig.java  # Altera constraint metodo_pago
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
│   │   │   │   └── asistente/       # Dashboard asistente (POS + pedidos + historial)
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
│   │   │   ├── algolia.service.ts    # Búsqueda Algolia
│   │   │   └── toast.service.ts      # Notificaciones
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

## Variables de Entorno (`application.properties`)

El backend usa `application.properties` con claves reales. **NO subir a GitHub** (push protection bloquea).

Claves usadas:
- `spring.datasource.password` (Supabase)
- `jwt.secret`
- `spring.mail.password`
- `stripe.api.key`
- `gemini.api.key`
- `supabase.key`
- `plin.numero`, `plin.titular`
- `sip.numero`, `sip.titular`

Hay un archivo `.env.example` en backend con las variables necesarias.

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
- **Métodos de pago:** Tarjeta (Stripe), Plin, Yape, SIP (Ágora)
- **Validación de cobertura:** 2 km de radio desde la tienda, bloqueo por distrito
- **Recetas médicas:** Flujo oculto (`mostrarRecetas: false`)
- **Autocompletado de direcciones:** Geoapify (detecta distrito automáticamente)
- **Marcador arrastrable** en seguimiento para ajustar ubicación exacta

### 4. Seguimiento de Pedidos
- Estados: PAGADO → EN_CAMINO → COMPLETADO (delivery, confirmado manualmente por asistente) o PAGADO → LISTO_RECOJO → COMPLETADO (recojo, confirmado manualmente)
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

### 6. Dashboard Asistente (Rol: asistente)
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

---

## Coordenadas de la Tienda (Botica Anita)
- **Dirección:** Av. Los Jazmines 408, Independencia, Lima
- **Pin verificado:** `-12.001353207277829, -77.04735384172724`
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
2. `LISTO_RECOJO` (2 min) → listo para recoger
3. `COMPLETADO` (manual, por asistente) → cerrado

### Estados especiales
- `EN_ESPERA` / `ESPERANDO`: espera de receta médica
- `PENDIENTE_VERIFICACION`: verificando pago Plin
- `CANCELADO` / `RECHAZADO`: pedido cancelado o rechazado

---

## Mapas y Geocodificación

### MapLibre GL JS
- **Tiles:** Esri World Street Map (`https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}`)
- **Worker:** servido localmente (`/maplibre-gl-worker.mjs` en public/)
- **Componentes:**
  - `tracking-mapa.component.ts` (seguimiento con MapLibre, solo en Mis Pedidos)
  - Sin mapa en página de pago

### Geoapify
- **API Key:** `5877badf92e14ccb85d2a16ede4aa1dc` (local, no subida)
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

---

## Variables de Entorno

### Frontend (`environment.ts` dev, `environment.prod.ts` prod)
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  mostrarRecetas: false,
  geoapifyApiKey: '...',        // local, no subida
  stripePublishableKey: '...',  // local, no subida (legacy)
};
```

### Backend (`application.properties` + `.env`)
- `STRIPE_API_KEY` (secret, .env)
- `GEMINI_API_KEY` (secret, .env)
- `MAIL_PASSWORD` (SMTP, .env)
- `SUPABASE_URL`, `SUPABASE_KEY` (con defaults)
- `PLIN_NUMERO`, `SIP_NUMERO` (con defaults)

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

## Git y Ramas
- **Rama principal:** `main`
- **Rama de desarrollo:** `lima`
- **Repo:** `https://github.com/Uriel-20-07/proyecto_botica_anita.git`
- **⚠️ NO subir `application.properties` ni `environment.ts` con keys reales**

---

## Notas Importantes

1. **Fechas en UTC:** Todo el sistema usa UTC para fechas. El backend guarda `LocalDateTime.now(ZoneOffset.UTC)` y el frontend interpreta las fechas como UTC.
2. **Mapas:** Se migró de Leaflet → Mapbox GL JS → MapLibre GL JS. Tiles de Esri (sin token). Worker servido localmente.
3. **Cobertura de delivery:** Radio de 2 km desde la tienda (Av. Los Jazmines 408, Independencia). Solo distrito Independencia.
4. **Envío urgente:** S/ 5.00 (delivery), S/ 0.00 (recojo en tienda).
5. **Recojo en tienda:** Solo en "Bodega Independencia" (Av. Los Jazmines 408).
6. **Estados de pedido:** Avance automático por tiempo (scheduler cada 30s) hasta EN_CAMINO/LISTO_RECOJO. COMPLETADO solo manual (asistente).
7. **Paginación:** 20 items por página en listas del dashboard.
8. **Recetas médicas:** Flujo oculto (`mostrarRecetas: false` en environment).
9. **Botica Anita:** Tienda única en Av. Los Jazmines 408, Independencia, Lima. Coords: `-12.001353207277829, -77.04735384172724`.
10. **Emails:** Todos en formato HTML con diseño profesional (gradiente, tablas, footer). SMTP configurado con Gmail.
11. **Asistente:** Rol `asistente` con dashboard POS + pedidos + historial. Login: `asistente@correo_corp.com` / `asistente123`.
12. **Marcador arrastrable:** En seguimiento, el usuario puede ajustar la ubicación exacta. Se persiste en el backend via PUT `/api/pedidos/{id}/ubicacion`.

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
git push origin HEAD:main            # Push a main (solo cuando esté verificado)
```

### ⚠️ Seguridad
- **NO subir `application.properties`** (contiene claves API reales)
- **NO subir `environment.ts` / `environment.prod.ts` con keys reales**
- Usar variables de entorno: `${STRIPE_API_KEY:}`, `${GEMINI_API_KEY:}`, etc.
- GitHub push protection bloquea commits con claves reales

---

## Archivos Clave para Entender el Proyecto

### Backend
- `AdminController.java` - Dashboard admin (ventas, productos, usuarios, cupones, reportes)
- `AsistenteController.java` - POS, pedidos, historial (rol asistente)
- `AuthController.java` - Login/registro (usuarios + corporativo)
- `PagoController.java` - Pagos (Stripe, Plin, Yape, SIP)
- `PedidoController.java` - Historial y seguimiento de pedidos + actualizar ubicación
- `PedidoTrackingController.java` - Tracking en tiempo real
- `PedidoTrackingService.java` - Avance de estado por tiempo + geocodificación + email "en camino"
- `PedidoEstadoScheduler.java` - Avance automático cada 30s (sin auto-completar)
- `GeocodificacionService.java` - Nominatim con rate limit
- `PagoService.java` - Lógica de pagos, validación de cobertura 2km
- `EmailService.java` - Emails HTML (confirmación, en camino, entregado, boleta presencial)
- `DataSeeder.java` - Datos iniciales (admin, vendedor, asistente)
- `ConstraintFixConfig.java` - Altera constraint metodo_pago para incluir EFECTIVO

### Frontend
- `mapa-ruta.service.ts` - MapLibre + Geoapify
- `tracking-mapa.component.ts` - Mapa de seguimiento (solo en Mis Pedidos)
- `pago.ts` - Checkout y pago (sin mapa)
- `seguimiento-pedido.ts` - Seguimiento del pedido
- `admin.component.ts` - Dashboard corporativo
- `asistente.component.ts` - Dashboard asistente (POS + pedidos + historial)
- `fecha-util.ts` - Fechas UTC
- `tracking.service.ts` - API tracking
- `admin.service.ts` - API admin
- `asistente.service.ts` - API asistente
- `pedido.service.ts` - API pedidos
- `algolia.service.ts` - Búsqueda Algolia
- `toast.service.ts` - Notificaciones

---

*Última actualización: 12 de noviembre de 2026*
*Contexto generado para facilitar la comprensión del proyecto por otra IA.*