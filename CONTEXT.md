CONTEXTO DEL PROYECTO: BOTICAANITA — CHATBOT SOFIA Y RESTRICCIONES DEL CARRITO
1. Descripción general del proyecto

Estoy desarrollando una tienda virtual para una botica llamada BoticaAnita, ubicada en Lima, Perú.

El proyecto utiliza:

Backend: Java con Spring Boot.
Frontend: Angular.
Base de datos: gestionada mediante JPA/Hibernate y repositorios de Spring Data.
Autenticación: Spring Security y JWT.
Inteligencia artificial: Gemini API de Google.
Búsqueda e indexación de productos: Algolia.
Carrito de compras: cada usuario tiene su propio carrito, con detalles que representan los productos y sus cantidades.

El chatbot se llama SofIA, la asistente virtual de BoticaAnita. Su función es atender consultas de los clientes, buscar productos en la base de datos, informar sobre precios y ofertas, orientar de manera informativa sobre temas de salud y facilitar las compras.

2. Objetivo principal de esta revisión

Se me indicó que debía implementar la siguiente mejora:

Limitar el uso del carrito de compras desde el chatbot y verificar el stock antes de agregar productos.

Actualmente, existe el riesgo de que un cliente solicite una cantidad excesiva de un producto y el chatbot la agregue al carrito sin restricciones suficientes.

Las reglas que quiero implementar son:

El máximo permitido es de 10 unidades por producto en el carrito.
Antes de agregar un producto, se debe comprobar que exista y consultar su stock actual.
La cantidad solicitada debe ser un número entero positivo.
No se debe agregar una cantidad superior al stock disponible.
El límite de 10 unidades debe considerar la cantidad que el usuario ya tiene en el carrito para ese producto.
Si el cliente solicita más unidades de las permitidas, el sistema debe informarle la cantidad máxima que puede agregar y preguntarle si desea continuar con esa cantidad.
Si no hay stock suficiente, no se debe agregar el producto ni confirmar una operación que no se haya completado.
La validación debe realizarse en el backend Java, no únicamente mediante instrucciones para Gemini.
Las operaciones realizadas desde Angular, el chatbot o cualquier otro cliente deben respetar las mismas restricciones.
Se debe mantener la lógica existente del carrito, la autenticación, los precios, las ofertas y las relaciones entre entidades, evitando cambios innecesarios.
Ejemplos del comportamiento esperado

Caso A: stock insuficiente

El cliente solicita 7 unidades.
El stock disponible es de 3 unidades.
SofIA debe explicar que no hay suficientes existencias y preguntar si desea agregar las 3 unidades disponibles.
No debe agregar automáticamente las 3 unidades sin consentimiento del cliente.

Caso B: límite máximo

El cliente solicita 15 unidades.
El stock disponible es de 30 unidades.
El máximo permitido sigue siendo 10 unidades.
SofIA debe informar del límite y solicitar confirmación para agregar 10 unidades.

Caso C: producto ya presente en el carrito

El cliente tiene 6 unidades de un producto en el carrito.
Solicita agregar otras 5 unidades.
El total sería 11, por lo que se debe rechazar esa operación.
Si hay stock suficiente, puede ofrecer agregar hasta 4 unidades adicionales, previa confirmación del cliente.

Caso D: stock insuficiente para completar el carrito

El cliente tiene 2 unidades en el carrito.
Solicita agregar otras 5 unidades.
Solo quedan 3 unidades en inventario.
Debe comprobarse el stock real y la cantidad acumulada. El sistema no debe permitir superar el stock ni el límite de 10 unidades.

Caso E: cantidad inválida

El cliente o frontend envía una cantidad igual a 0, negativa o inválida.
El backend debe rechazar la operación.
3. Código revisado hasta ahora
A. ChatbotController.java

Paquete:

com.example.demo.controllers

Ruta base:

/api/chatbot

Endpoint:

POST /api/chatbot/consultar

Responsabilidades actuales:

Recibe los mensajes del chatbot.
Se conecta a Gemini mediante RestTemplate.
Utiliza la propiedad gemini.api.key.
Tiene configurados un modelo principal y uno de respaldo.
Si el modelo principal responde con HTTP 429 o 503, intenta utilizar el modelo de respaldo.
Construye el historial de conversación para Gemini.
Declara las funciones buscarProductos, agregarAlCarrito y redirigir.
Ejecuta buscarProductos en el backend mediante ProductoService.buscarPorNombre().
Devuelve las llamadas a agregarAlCarrito y redirigir al frontend para que este las ejecute.
Construye la instrucción del sistema que define la identidad y el comportamiento de SofIA.

La función buscarProductos devuelve información de los productos, incluyendo:

ID del producto.
Nombre.
Precio regular.
Precio de oferta.
Indicador de oferta.
Precio con descuento.
Porcentaje de descuento.
Stock.
Descripción.

Se añadió a las instrucciones del sistema una sección llamada «RESTRICCIONES DE CANTIDAD Y VALIDACIÓN DE STOCK», que establece que SofIA debe consultar el stock, respetar el máximo de 10 unidades, solicitar confirmación cuando corresponda y no afirmar que se completó una operación sin que el sistema la haya realizado.

Problema detectado: estas instrucciones solo orientan al modelo. No garantizan por sí mismas que el frontend o el backend respeten las restricciones.

Además, el controlador actual no ejecuta directamente la función agregarAlCarrito: devuelve la llamada al frontend. Por ello, es indispensable revisar el código Angular que procesa esa llamada.

El código original completo de ChatbotController.java fue compartido en la conversación anterior. Si se necesita modificarlo, debo proporcionar primero una propuesta que conserve las funciones actuales y explique los cambios concretos.

B. CarritoController.java

Paquete:

com.example.demo.controllers

Ruta base:

/api/carrito

Servicios inyectados:

CarritoService
AuthService

Endpoints actuales:

GET /api/carrito: obtiene o crea el carrito del usuario autenticado.
POST /api/carrito/agregar: agrega un producto mediante los parámetros idProducto y cantidad.
POST /api/carrito/disminuir: disminuye en una unidad la cantidad de un producto.
DELETE /api/carrito/eliminar: elimina completamente un producto del carrito.
DELETE /api/carrito/vaciar: vacía el carrito.

El usuario se obtiene a partir de Principal, utilizando su email y AuthService.

Problema detectado: el endpoint /agregar no valida directamente el máximo de 10 unidades, la cantidad acumulada en el carrito ni el stock. Delega la operación en CarritoService.

También se debe revisar cómo se devuelven los errores de validación al frontend. Conviene que el cliente reciba respuestas claras cuando la cantidad o el stock no permitan la operación.

C. CarritoService.java

Paquete:

com.example.demo.services

Repositorios inyectados:

CarritoRepository
DetalleCarritoRepository
UserRepository
ProductoRepository

Métodos existentes:

obtenerOCrearCarrito(Integer idUsuario)
agregarProductoAlCarrito(Integer idUsuario, Integer idProducto, Integer cantidad)
disminuirProductoDelCarrito(Integer idUsuario, Integer idProducto)
eliminarProductoDelCarrito(Integer idUsuario, Integer idProducto)
vaciarCarrito(Integer idUsuario)

El método agregarProductoAlCarrito() actualmente:

Obtiene o crea el carrito.
Busca el producto en la base de datos.
Busca si ese producto ya está en el carrito.
Si existe, suma la cantidad solicitada a la cantidad existente.
Si no existe, crea un nuevo detalle.
Guarda el detalle y el carrito.

Problemas detectados:

No valida que la cantidad sea un entero positivo.
No limita a 10 unidades la cantidad acumulada por producto.
No comprueba que el stock sea suficiente.
No evita que la suma de las unidades existentes y las nuevas supere el stock disponible.
No define una respuesta de negocio clara para los intentos inválidos.

Este servicio es el lugar principal donde debe aplicarse la validación del carrito, porque centraliza las operaciones y evita depender de las instrucciones de Gemini o del comportamiento del frontend.

La validación también debe considerar que el stock puede cambiar entre la consulta y el intento de agregar el producto. Si es necesario garantizar consistencia ante solicitudes concurrentes, se debe analizar una estrategia transaccional y de bloqueo adecuada para la base de datos.

D. ProductoService.java

Paquete:

com.example.demo.services

Repositorio inyectado:

ProductoRepository

También utiliza AlgoliaService para indexar los productos.

Métodos existentes:

listarTodos()
buscarPorNombre(String nombre)
filtrarPorCategoria(Integer idCategoria)
guardar(Producto producto)
eliminar(Integer id)
indexarTodosEnAlgolia()

El método buscarPorNombre() utiliza:

productoRepository.findByNombreContainingIgnoreCase(nombre)

El servicio también sincroniza productos con Algolia al guardarlos, eliminarlos y arrancar la aplicación.

La lógica de stock y carrito no debe alterar innecesariamente esta integración.

E. Producto.java

Paquete:

com.example.demo.models

Es una entidad JPA asociada a la tabla productos.

Atributos relevantes:

idProducto: identificador del producto.
nombre: nombre del producto.
descripcion: descripción.
precioVenta: precio regular de tipo BigDecimal.
precioOferta: precio de oferta.
enOferta: indicador de oferta.
stock: unidades disponibles en inventario, de tipo Integer.
precioConDescuento: calculado mediante @Formula.
descuentoPorcentaje: calculado mediante @Formula.
fechaCaducidad: fecha de caducidad.

Se utiliza Lombok con @Data y JPA/Hibernate.

Conclusión: la entidad ya dispone del atributo stock; no es necesario crear otro atributo para almacenar las existencias.

Debe comprobarse que las operaciones del carrito utilicen el stock real de la base de datos y no dependan exclusivamente de información antigua del historial de conversación.

4. Reglas de implementación recomendadas

Antes de modificar el código, revisar las entidades, repositorios y frontend para conocer las relaciones y los contratos reales de la aplicación.

La lógica de agregar al carrito debe validar, como mínimo:

Que idUsuario, idProducto y cantidad sean válidos.
Que el usuario exista y esté autenticado según la seguridad actual.
Que el producto exista.
Que la cantidad solicitada sea positiva.
Que el producto tenga stock suficiente para la cantidad total que se pretende mantener en el carrito, de acuerdo con la regla de negocio.
Que la cantidad total del producto en el carrito no supere 10 unidades.
Que la operación no deje el carrito en un estado inconsistente.

La fórmula conceptual para la cantidad adicional permitida es:

cantidadMaximaAdicional = min(10 - cantidadActualEnCarrito, stockDisponible)

Si el resultado es cero o negativo, no se deben agregar más unidades.

No se debe implementar esta fórmula sin verificar primero cómo se manejan las reservas de stock, el inventario y las compras en el resto de la aplicación. En particular, debe aclararse si el stock se descuenta al agregar al carrito o solo cuando se confirma la compra.

La cantidad máxima debe aplicarse a cada producto individualmente, no necesariamente a la suma de unidades de todos los productos del carrito.

5. Clases que todavía necesito revisar

Para comprobar la concordancia de todo el sistema, debo proporcionar las siguientes clases y archivos:

DetalleCarrito.java
Para verificar cómo se almacena la cantidad y cómo se relaciona el detalle con el carrito y el producto.
Carrito.java
Para revisar las relaciones JPA, la colección de detalles y las reglas de persistencia.
ProductoRepository.java
Para comprobar cómo se consultan los productos y si se necesita una consulta específica para obtener el stock actual.
DetalleCarritoRepository.java
Para revisar cómo se consultan y guardan los detalles del carrito.
CarritoRepository.java
Para comprobar cómo se obtiene el carrito del usuario y si las consultas permiten cargar correctamente sus detalles.
El código Angular que procesa las llamadas a funciones de Gemini.
Especialmente la parte que identifica agregarAlCarrito, obtiene idProducto y cantidad, y realiza la petición HTTP al backend.
El servicio Angular que consume /api/carrito/agregar, si está separado del componente del chatbot.
SecurityConfig.java.
Para comprobar la autenticación y autorización de los endpoints.
El código de la entidad o del servicio de compras/pedidos que descuenta el stock al confirmar una compra, si existe.
Esto es necesario para entender si las existencias se reservan, se descuentan al comprar o se gestionan de otra manera.

No es necesario reescribir todas las clases desde cero. Se debe conservar lo que ya funciona y proponer cambios puntuales.

6. Instrucciones para el siguiente asistente

Actúa como un desarrollador senior especializado en Java, Spring Boot, Spring Security, JPA/Hibernate, Angular y APIs de modelos de lenguaje.

Debes:

Analizar el código real que se comparta antes de recomendar modificaciones.
Mantener coherencia entre controladores, servicios, entidades, repositorios y frontend.
No asumir nombres de métodos, propiedades, relaciones o rutas que no hayan sido confirmados.
Implementar el límite de 10 unidades por producto en el backend, considerando la cantidad acumulada en el carrito.
Validar el stock antes de agregar productos.
No permitir cantidades negativas, cero o inválidas.
Diseñar una respuesta de error comprensible para el frontend.
Hacer que SofIA informe al cliente cuando la cantidad solicitada supere el máximo o las existencias disponibles.
Solicitar confirmación antes de agregar una cantidad alternativa cuando el cliente haya pedido más de lo permitido.
No permitir que Gemini confirme operaciones que el backend no haya completado.
No confiar en que las instrucciones del sistema de Gemini son una medida de seguridad.
Evitar cambios innecesarios en la integración con Algolia, la autenticación JWT, los precios, las ofertas y el resto del carrito.
Analizar los casos de concurrencia y el stock al finalizar una compra, sin implementar reservas de inventario que no formen parte de la arquitectura existente.
Explicar exactamente qué archivos hay que modificar, qué código hay que añadir o sustituir y por qué.
Proporcionar código completo y coherente cuando sea necesario, indicando el nombre del archivo y su ubicación.
Si falta información para tomar una decisión importante, pedir únicamente las clases o fragmentos necesarios.
7. Estado actual y siguiente paso

Hasta el momento se han compartido y revisado ChatbotController.java, CarritoController.java, CarritoService.java, ProductoService.java y Producto.java.

Ya se han identificado las principales carencias de validación, pero todavía no se ha completado la implementación de la restricción.

El siguiente paso es revisar primero DetalleCarrito.java, Carrito.java y ProductoRepository.java. Después se debe analizar el código Angular que ejecuta la función agregarAlCarrito y, con toda esa información, definir e implementar la solución completa.

No dar por hecho que la restricción está implementada hasta que se haya comprobado el código Java, la comunicación con Angular y las respuestas reales del backend.

El objetivo final es que la regla de máximo 10 unidades y la validación de stock se cumplan siempre, independientemente de que el cliente agregue el producto mediante el chatbot o directamente desde la tienda virtual.

---

## 8. Progreso de implementación (sesión actual)

### 8.1 Análisis previo: el prompt NO es una garantía
Se revisó el código real completo (backend + frontend) y se concluyó:
- Las reglas 19–25 añadidas al system prompt de SofIA en `ChatbotController` son una mejora de UX válida, pero **no garantizan nada**: Gemini es no determinista, puede no llamar a `buscarProductos`, usar un id viejo del historial o inventar cantidades; y el modelo de respaldo (`flash-lite`) es peor siguiendo instrucciones.
- SofIA **no tiene acceso al carrito del usuario**, por lo que el "Caso C" (6 en carrito + 5 pedidas) es imposible de resolver con prompt. Solo el backend puede resolverlo.
- La validación real **debe** vivir en `CarritoService` (que centraliza todas las operaciones del carrito y se usa tanto desde el chatbot como desde la tienda).

### 8.2 Hallazgo importante sobre el stock (dos fuentes de verdad)
`PagoService.descontarStockLotesFefo()` descuenta inventario **al momento de pagar**, con lógica FEFO:
- Si el producto **tiene lotes** (`InventarioLote`): descuenta de `cantidadActual` de los lotes y **NO actualiza `producto.stock`**.
- Si **no tiene lotes**: descuenta de `producto.stock`.
- **El carrito NO reserva inventario.** Por tanto, validar al agregar es "best effort"; la validación definitiva sigue siendo la del pago (que ya lanza excepción si no alcanza).
- ⚠️ Pendiente de decisión de negocio: `producto.stock` y la suma de lotes pueden estar desincronizados. Definir cuál es la fuente autoritativa antes de refinar más.

### 8.3 HECHO: `CarritoService.agregarProductoAlCarrito()` (validación implementada)
Se implementó la validación de negocio en el método, respetando los Casos A–E:
1. Cantidad debe ser entero positivo (`null` o `<= 0` → rechaza).
2. Límite duro de **10 unidades por producto** (se evalúa antes que stock, para priorizar el mensaje correcto).
3. Debe existir **stock suficiente** para la cantidad solicitada.
4. Si el producto **ya está en el carrito**, valida el **acumulado** (`cantidadActual + cantidad`) contra el tope de 10 y contra el stock.
5. Los mensajes de error informan el **máximo adicional** permitido (`min(10 - cantidadActual, stock - cantidadActual)`), para que el frontend pueda ofrecer "¿deseas agregar N?".
6. `stock` se protege contra `null` (evita NPE → 500). Se corrigió un null-check de `cantidad` que estaba al revés (`cantidad.equals(null)` no detecta `null`).

> Nota: estas validaciones lanzan `RuntimeException`. **Aún se comportan como HTTP 500.** Falta el paso 8.4 para que el frontend reciba un 400 comprensible.

### 8.3.b HECHO (Opción A): contrato de error 400 estructurado + confirmación en el chat
Se implementó la **"Opción A"**: el backend rechaza con un 400 llevando el `maxAdicional`, y el frontend lo usa para ofrecer la confirmación.

**Backend (nuevo paquete `com.example.demo.exceptions`):**
- `CarritoException.java`: excepción de negocio que, además del mensaje, transporta `maxAdicional` (unidades que sí se pueden agregar; 0 = ninguna).
- `CarritoExceptionHandler.java`: `@RestControllerAdvice` que convierte `CarritoException` en **HTTP 400** con cuerpo `{ "error": "...", "maxAdicional": N }`.
- `CarritoService`: refactorizado para calcular `maxAdicional = max(0, min(10 - cantidadActual, stock - cantidadActual))` y lanzar `CarritoException` con ese valor en todos los rechazos. Se unificaron las reglas de límite y stock en un solo cálculo (más claro y sin duplicar lógica).

**Frontend Angular:**
- `cart.service.ts`: `addWithQty()` ahora **devuelve un `Observable`** (antes era `void` con `alert()`). Ya no maneja el error internamente; el chatbot decide cómo mostrarlo. Se importaron `Observable`, `throwError` (rxjs) y `tap` (rxjs/operators).
- `chatbot.ts`:
  - Nueva interfaz `ChatMessage` con campo opcional `confirmacion?: { idProducto, cantidad }`.
  - `ejecutarAgregarAlCarrito()`: suscribe y reacciona al resultado real. Ya **no confirma "¡Listo!" a ciegas**. Si el backend devuelve `maxAdicional > 0`, muestra el mensaje + botones "Sí, agregar N" / "No, gracias".
  - `confirmarCantidad()` / `cancelarConfirmacion()`: manejan la respuesta del usuario.
- `chatbot.html`: burbuja con botones de confirmación renderizados solo cuando `msg.confirmacion` existe.
- `chatbot.css`: estilos `.chatbot-confirm-actions` / `.chatbot-confirm-btn` (usa la variable `--primary-orange` del proyecto).

**Prompt (`ChatbotController`):**
- Reglas 20 y 23 corregidas: ya no dicen "10 por operación" sino **10 unidades acumuladas por producto** en el carrito.
- Nueva regla 26: indica a SofIA que el sistema valida y rechaza, y que no debe insistir en cantidades rechazadas.

**Contrato del 400 (para referencia):**
```json
{ "error": "No se pueden agregar 15 unidades porque el límite es de 10 unidades por producto. Puedes agregar 4 unidad(es) más como máximo.", "maxAdicional": 4 }
```
- `maxAdicional > 0` → el chat ofrece el botón "Sí, agregar N".
- `maxAdicional == 0` → solo se informa el motivo (no hay nada que ofrecer).

**Compilación verificada:** backend (`./mvnw compile`) y frontend (`tsc --noEmit`) sin errores.

### 8.4 PENDIENTE / Notas abiertas
- La tienda normal (`producto-detalle` → `cartService.add()`) sigue usando el `alert()` de `manejarErrorCarrito`. Con el nuevo 400, se le podría mostrar un mensaje más específico en lugar del alert genérico (mejora opcional, no crítica).
- Decisión de negocio pendiente (ver 8.2): definir la fuente autoritativa de stock cuando hay lotes (`producto.stock` vs suma de `InventarioLote.cantidadActual`), ya que el carrito valida contra `producto.stock` pero el pago FEFO descuenta de los lotes.
- Concurrencia: dos peticiones simultáneas podrían superar el límite/stock (no hay bloqueo pesimista). Riesgo bajo; evaluar si se necesita `@Lock(PESSIMISTIC_WRITE)` en el producto.