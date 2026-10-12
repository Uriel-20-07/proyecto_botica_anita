package com.example.demo.controllers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.example.demo.models.Producto;
import com.example.demo.services.ProductoService;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    @Autowired
    private ProductoService productoService;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    // Modelo principal (mejor calidad, cuota gratuita muy baja: ~20
    // solicitudes/día).
    @Value("${gemini.model.primary:gemini-3.8-flash}")
    private String geminiModelPrimary;

    // Modelo de respaldo: se usa automáticamente si el principal responde 429
    // (cuota) o 503
    // (saturado). Flash-Lite tiene cuota gratuita mucho más generosa (~500
    // solicitudes/día).
    @Value("${gemini.model.fallback:gemini-3.5-flash-lite}")
    private String geminiModelFallback;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Llama a Gemini con el modelo principal; si responde 429 (cuota agotada) o 503
     * (modelo saturado), reintenta automáticamente una vez con el modelo de
     * respaldo.
     */
    private ResponseEntity<Map> callGemini(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        String primaryUrl = "https://generativelanguage.googleapis.com/v1beta/models/"
                + geminiModelPrimary + ":generateContent";
        try {
            return restTemplate.postForEntity(primaryUrl, entity, Map.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            int status = e.getStatusCode().value();
            boolean cuotaOSaturado = status == 429 || status == 503;
            if (cuotaOSaturado && geminiModelFallback != null && !geminiModelFallback.isBlank()
                    && !geminiModelFallback.equals(geminiModelPrimary)) {
                String fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/"
                        + geminiModelFallback + ":generateContent";
                return restTemplate.postForEntity(fallbackUrl, entity, Map.class);
            }
            throw e;
        }
    }

    public static class ChatMessage {
        public String role; // "user", "model" or "function"
        public String content;
        public Map<String, Object> functionCall; // For client-side function calls
    }

    public static class ChatRequest {
        public List<ChatMessage> messages;
    }

    @PostMapping("/consultar")
    public ResponseEntity<?> consultar(@RequestBody ChatRequest request) {
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || geminiApiKey.contains("YOUR_GEMINI_API_KEY")) {
            ChatMessage errorResponse = new ChatMessage();
            errorResponse.role = "model";
            errorResponse.content = "¡Hola! Soy SofIA, la asistente virtual de BoticaAnita. Para poder ayudarte de forma inteligente, necesitas configurar una clave de API válida para Gemini (`gemini.api.key`) en el archivo `application.properties` del backend. ¡Es muy sencillo y gratuito!";
            return ResponseEntity.ok(errorResponse);
        }

        try {
            // Construir la petición para Gemini
            List<Map<String, Object>> contents = new ArrayList<>();
            for (ChatMessage msg : request.messages) {
                Map<String, Object> contentMap = new HashMap<>();
                contentMap.put("role", msg.role);

                List<Map<String, Object>> parts = new ArrayList<>();
                Map<String, Object> part = new HashMap<>();
                if (msg.functionCall != null) {
                    part.put("functionCall", msg.functionCall);
                } else {
                    part.put("text", msg.content);
                }
                parts.add(part);
                contentMap.put("parts", parts);
                contents.add(contentMap);
            }

            // Realizar bucle de llamada para resolver llamadas a funciones internas
            // (buscarProductos)
            int maxIterations = 5;
            while (maxIterations-- > 0) {
                Map<String, Object> geminiPayload = buildGeminiPayload(contents);
                ResponseEntity<Map> response = callGemini(geminiPayload);
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Map<String, Object> body = response.getBody();

                    // Extraer candidatos
                    List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("candidates");
                    if (candidates != null && !candidates.isEmpty()) {
                        Map<String, Object> firstCandidate = candidates.get(0);
                        Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
                        if (content != null) {
                            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                            if (parts != null && !parts.isEmpty()) {
                                Map<String, Object> firstPart = parts.get(0);

                                // Verificar si es una llamada a función
                                if (firstPart.containsKey("functionCall")) {
                                    Map<String, Object> functionCall = (Map<String, Object>) firstPart
                                            .get("functionCall");
                                    String functionName = (String) functionCall.get("name");
                                    Map<String, Object> args = (Map<String, Object>) functionCall.get("args");

                                    // Si es buscarProductos, la resolvemos aquí mismo en el backend
                                    if ("buscarProductos".equals(functionName)) {
                                        String query = args != null && args.containsKey("query")
                                                ? (String) args.get("query")
                                                : "";
                                        List<Producto> productos = productoService.buscarPorNombre(query);

                                        // Agregar la llamada a la función al historial.
                                        // IMPORTANTE (Gemini 3.x): se reutiliza "firstPart" tal cual lo devolvió
                                        // Gemini, porque además de "functionCall" puede traer "thoughtSignature",
                                        // y hay que reenviarlo exactamente igual o la siguiente llamada falla
                                        // con 400 "missing thought_signature".
                                        Map<String, Object> modelCallMap = new HashMap<>();
                                        modelCallMap.put("role", "model");
                                        List<Map<String, Object>> modelParts = new ArrayList<>();
                                        modelParts.add(firstPart);
                                        modelCallMap.put("parts", modelParts);
                                        contents.add(modelCallMap);

                                        // Agregar la respuesta de la función al historial.
                                        // Gemini 3.x ya NO acepta role "function": exige role "user",
                                        // y hace "strict response matching" del id de la llamada.
                                        Map<String, Object> functionRespMap = new HashMap<>();
                                        functionRespMap.put("role", "user");
                                        List<Map<String, Object>> funcParts = new ArrayList<>();
                                        Map<String, Object> funcPart = new HashMap<>();

                                        Map<String, Object> functionResponse = new HashMap<>();
                                        functionResponse.put("name", "buscarProductos");
                                        if (functionCall.get("id") != null) {
                                            functionResponse.put("id", functionCall.get("id"));
                                        }

                                        Map<String, Object> responseContent = new HashMap<>();
                                        List<Map<String, Object>> simplifiedProductos = new ArrayList<>();
                                        for (Producto p : productos) {
                                            Map<String, Object> simplified = new HashMap<>();
                                            simplified.put("id", p.getIdProducto());
                                            simplified.put("nombre", p.getNombre());
                                            simplified.put("precioVenta", p.getPrecioVenta());
                                            simplified.put("precioOferta", p.getPrecioOferta());
                                            simplified.put("enOferta", p.getEnOferta());
                                            simplified.put("precioConDescuento", p.getPrecioConDescuento());
                                            simplified.put("descuentoPorcentaje", p.getDescuentoPorcentaje());

                                            boolean enOferta = p.getEnOferta() != null && p.getEnOferta();
                                            simplified.put("precio",
                                                    enOferta && p.getPrecioOferta() != null ? p.getPrecioOferta()
                                                            : p.getPrecioVenta());

                                            simplified.put("stock", p.getStock());
                                            simplified.put("descripcion", p.getDescripcion());
                                            simplifiedProductos.add(simplified);
                                        }
                                        responseContent.put("productos", simplifiedProductos);

                                        functionResponse.put("response", responseContent);
                                        funcPart.put("functionResponse", functionResponse);
                                        funcParts.add(funcPart);
                                        functionRespMap.put("parts", funcParts);
                                        contents.add(functionRespMap);

                                        // Repetir el bucle para que Gemini responda en base a los productos encontrados
                                        continue;
                                    } else {
                                        // Es una función del cliente (agregarAlCarrito o redirigir).
                                        // La devolvemos directamente al frontend para que la ejecute.
                                        ChatMessage clientResponse = new ChatMessage();
                                        clientResponse.role = "model";
                                        clientResponse.content = "Procesando acción...";
                                        clientResponse.functionCall = functionCall;
                                        return ResponseEntity.ok(clientResponse);
                                    }
                                } else if (firstPart.containsKey("text")) {
                                    // Es una respuesta de texto normal
                                    ChatMessage textResponse = new ChatMessage();
                                    textResponse.role = "model";
                                    textResponse.content = (String) firstPart.get("text");
                                    return ResponseEntity.ok(textResponse);
                                }
                            }
                        }
                    }
                }
                break;
            }

            ChatMessage defaultFail = new ChatMessage();
            defaultFail.role = "model";
            defaultFail.content = "Lo siento, tuve un problema procesando tu solicitud con Gemini. Por favor intenta de nuevo.";
            return ResponseEntity.ok(defaultFail);

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            e.printStackTrace();
            ChatMessage err = new ChatMessage();
            err.role = "model";
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                err.content = "No se pudo autenticar con Gemini (error " + status
                        + "). Verifica en Google AI Studio que la clave en `gemini.api.key` sea de tipo Auth (empieza con 'AQ.'), esté activa, y que la 'Generative Language API' esté habilitada en el proyecto.";
            } else if (status == 429) {
                err.content = "Se alcanzó el límite de solicitudes de Gemini, incluso en el modelo de respaldo (error 429). Espera unos minutos antes de volver a intentar, o revisa tu cuota diaria en Google AI Studio.";
            } else if (status == 503) {
                err.content = "El asistente está temporalmente saturado por alta demanda (error 503), incluso tras intentar con el modelo de respaldo. Intenta de nuevo en unos segundos.";
            } else {
                err.content = "Gemini respondió con un error (" + status + "): " + e.getResponseBodyAsString();
            }
            return ResponseEntity.ok(err);
        } catch (Exception e) {
            e.printStackTrace();
            ChatMessage err = new ChatMessage();
            err.role = "model";
            err.content = "Ocurrió un error en el servidor al intentar contactar con el asistente virtual: "
                    + e.getMessage();
            return ResponseEntity.ok(err);
        }
    }

    private Map<String, Object> buildGeminiPayload(List<Map<String, Object>> contents) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("contents", contents);

        // System Instruction
        Map<String, Object> systemInstruction = new HashMap<>();
        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> part = new HashMap<>();
        String instrucciones = """
                IDENTIDAD Y PROPÓSITO

                Eres SofIA, la Asistente Virtual Inteligente de BoticaAnita, una botica ubicada en Lima, Perú.

                Cuando saludes por primera vez en una conversación, preséntate utilizando tu nombre: SofIA.

                Tu propósito es brindar atención amable y clara a los clientes de BoticaAnita, ayudarlos a encontrar medicamentos y otros productos de la botica, proporcionar orientación informativa relacionada con su salud y facilitar su proceso de compra.

                ALCANCE DE LAS CONSULTAS

                1. Atiende exclusivamente consultas relacionadas con BoticaAnita, incluyendo temas de salud, medicamentos, productos farmacéuticos, envíos, métodos de entrega y procesos de compra.

                2. Si recibes una consulta ajena a estas áreas, como operaciones matemáticas, programación, historia u otros temas sin relación con la botica, rechaza responderla amablemente. Explica que tu función está limitada a brindar asistencia sobre BoticaAnita.

                CONSULTA DE PRODUCTOS Y DISPONIBILIDAD

                3. Siempre que un usuario consulte sobre un medicamento, producto o su disponibilidad en la botica, debes utilizar obligatoriamente la función "buscarProductos" para consultar la información correspondiente en la base de datos.

                4. Basa tus respuestas sobre productos exclusivamente en la información obtenida mediante dicha función. No inventes productos, precios, descuentos, características ni información sobre su disponibilidad.

                5. Si la consulta devuelve productos con existencias disponibles (stock > 0), puedes confirmar que el producto está disponible o que se cuenta con stock.

                6. Nunca reveles la cantidad exacta de unidades disponibles de un producto. No menciones cifras concretas de inventario, incluso cuando esa información esté presente en los resultados de búsqueda.

                PRECIOS, OFERTAS Y DESCUENTOS

                7. Cuando un producto tenga un descuento vigente, es decir, cuando "enOferta" sea verdadero y exista un precio de oferta o un precio con descuento inferior al precio de venta regular, informa expresamente al cliente que el producto se encuentra en oferta.

                8. En esos casos, presenta de forma clara y destacada ambos precios:
                   - Precio regular: el valor correspondiente a "precioVenta" o "precioNormal".
                   - Precio de oferta: el valor correspondiente a "precioOferta" o "precioConDescuento".

                   Utiliza una redacción clara y atractiva, por ejemplo:
                   "¡Este producto cuenta con descuento! Su precio regular es S/. 5.00, pero ahora está a solo S/. 3.50".

                9. Si el producto no tiene descuento, informa únicamente su precio regular, sin afirmar que existe una oferta.

                PRODUCTOS NO ENCONTRADOS

                10. Si la función "buscarProductos" devuelve una lista vacía, no menciones errores técnicos ni indiques que ocurrió un problema en el sistema.

                11. En su lugar, explica amablemente que no se encontró una coincidencia exacta y solicita al usuario que repita el nombre del producto o lo escriba de otra manera.

                12. Si el término ingresado presenta una similitud evidente con el nombre de un producto conocido, puedes sugerir esa posibilidad con cautela antes de solicitar una aclaración. Por ejemplo, si el usuario escribe "jaraba", puedes preguntar si se refiere a un jarabe.

                ATENCIÓN ANTE SÍNTOMAS O MALESTARES

                13. Cuando un usuario mencione síntomas o malestares, como dolor de cabeza, fiebre, tos, dolor de estómago o malestar general, responde primero con una expresión breve, cálida y empática que demuestre preocupación por su bienestar.

                   Por ejemplo:
                   "Lamento que te sientas así. Vamos a ayudarte a encontrar información que pueda orientarte".

                14. Después de expresar empatía, puedes continuar con la orientación correspondiente y, cuando proceda, sugerir productos pertinentes de la botica, respetando las demás instrucciones y las precauciones relacionadas con la salud.

                15. Si los síntomas descritos son intensos, persistentes o constituyen señales de alarma, como fiebre alta, dolor muy intenso, dificultad para respirar o molestias que duran varios días, recomienda con delicadeza que el usuario acuda a un médico o al establecimiento de salud más cercano, según corresponda.

                16. Deja claro, cuando resulte pertinente, que la orientación proporcionada por SofIA tiene carácter informativo y no sustituye la evaluación ni la consulta de un profesional de la salud.

                GESTIÓN DEL CARRITO DE COMPRAS

                17. Cuando el usuario solicite agregar un producto al carrito, debes utilizar la función "agregarAlCarrito", indicando obligatoriamente el identificador del producto ("idProducto") y la cantidad solicitada ("cantidad").

                18. Utiliza el identificador correspondiente al producto consultado y la cantidad indicada por el usuario. No inventes identificadores ni cantidades.

                RESTRICCIONES DE CANTIDAD Y VALIDACIÓN DE STOCK

                19. Antes de solicitar que se agregue un producto al carrito, verifica su disponibilidad y el stock actual mediante la información obtenida de la base de datos.

                20. El límite es de 10 unidades de un MISMO producto en el carrito, contando las unidades que el usuario ya tenga de ese producto. La cantidad máxima que se puede agregar en este momento es el menor valor entre las unidades que falten para llegar a 10 y el stock disponible.

                21. Si el usuario solicita una cantidad superior al límite permitido, no ejecutes la función "agregarAlCarrito". Informa amablemente de la cantidad máxima que puede agregar en ese momento y pregunta si desea continuar con esa cantidad o elegir otra menor.

                22. Si no existe stock disponible, informa que el producto no está disponible y no solicites que se agregue al carrito.

                23. Antes de ejecutar "agregarAlCarrito", asegúrate de que la cantidad sea un número entero positivo y que, sumando lo que el usuario ya tenga en el carrito, no se superen las 10 unidades por producto ni el stock disponible.

                24. Si el usuario acepta una cantidad válida, utiliza "agregarAlCarrito" con el identificador correcto del producto y la cantidad confirmada.

                25. Si el stock cambia entre la consulta y el intento de agregado, respeta siempre la disponibilidad actualizada de la base de datos. Nunca confirmes una operación que el sistema no haya completado correctamente.

                26. Ten en cuenta que el propio sistema valida la cantidad y el stock al momento de agregar. Si el sistema rechaza la operación, el cliente verá el motivo y, cuando corresponda, una opción para agregar la cantidad máxima permitida. No insistas en cantidades que el sistema haya rechazado.

                NAVEGACIÓN DENTRO DE LA TIENDA VIRTUAL

                27. Cuando el usuario solicite acceder al proceso de pago, consultar su carrito, visitar el catálogo o ingresar a su perfil, debes utilizar la función "redirigir" e indicar la ruta correspondiente.

                28. Utiliza las siguientes rutas según la acción solicitada:
                   - Proceso de pago: "/pago".
                   - Catálogo de productos: "/catalogo".
                   - Perfil del usuario: "/perfil".

                   Para cualquier otra ruta, utiliza la que corresponda a la funcionalidad solicitada y que esté disponible en la aplicación.

                ESTILO DE COMUNICACIÓN

                29. Mantén una comunicación amable, respetuosa, clara y profesional en todas tus respuestas.

                30. Prioriza la información útil para el cliente, respeta todas las restricciones anteriores y utiliza las funciones disponibles siempre que su uso sea obligatorio según estas instrucciones.
                """;

        part.put("text", instrucciones);
        parts.add(part);
        systemInstruction.put("parts", parts);
        payload.put("systemInstruction", systemInstruction);

        // Tools / Declaración de Funciones
        List<Map<String, Object>> tools = new ArrayList<>();
        Map<String, Object> tool = new HashMap<>();
        List<Map<String, Object>> functionDeclarations = new ArrayList<>();

        // 1. buscarProductos
        Map<String, Object> buscarProdDecl = new HashMap<>();
        buscarProdDecl.put("name", "buscarProductos");
        buscarProdDecl.put("description", "Busca productos de la botica en la base de datos por nombre.");
        Map<String, Object> buscarParams = new HashMap<>();
        buscarParams.put("type", "OBJECT");
        Map<String, Object> buscarProps = new HashMap<>();
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "STRING");
        queryProp.put("description", "El nombre o término del producto a buscar.");
        buscarProps.put("query", queryProp);
        buscarParams.put("properties", buscarProps);
        buscarParams.put("required", Collections.singletonList("query"));
        buscarProdDecl.put("parameters", buscarParams);
        functionDeclarations.add(buscarProdDecl);

        // 2. agregarAlCarrito
        Map<String, Object> addCartDecl = new HashMap<>();
        addCartDecl.put("name", "agregarAlCarrito");
        addCartDecl.put("description", "Añade un producto al carrito de compras del cliente.");
        Map<String, Object> addParams = new HashMap<>();
        addParams.put("type", "OBJECT");
        Map<String, Object> addProps = new HashMap<>();
        Map<String, Object> idProp = new HashMap<>();
        idProp.put("type", "INTEGER");
        idProp.put("description", "El ID del producto a agregar.");
        Map<String, Object> qtyProp = new HashMap<>();
        qtyProp.put("type", "INTEGER");
        qtyProp.put("description", "La cantidad del producto a agregar.");
        addProps.put("idProducto", idProp);
        addProps.put("cantidad", qtyProp);
        addParams.put("properties", addProps);
        addParams.put("required", Arrays.asList("idProducto", "cantidad"));
        addCartDecl.put("parameters", addParams);
        functionDeclarations.add(addCartDecl);

        // 3. redirigir
        Map<String, Object> redirectDecl = new HashMap<>();
        redirectDecl.put("name", "redirigir");
        redirectDecl.put("description", "Redirige al usuario a una ruta interna (ej: /catalogo, /pago, /perfil).");
        Map<String, Object> redParams = new HashMap<>();
        redParams.put("type", "OBJECT");
        Map<String, Object> redProps = new HashMap<>();
        Map<String, Object> routeProp = new HashMap<>();
        routeProp.put("type", "STRING");
        routeProp.put("description", "La ruta de destino (por ejemplo, '/pago').");
        redProps.put("ruta", routeProp);
        redParams.put("properties", redProps);
        redParams.put("required", Collections.singletonList("ruta"));
        redirectDecl.put("parameters", redParams);
        functionDeclarations.add(redirectDecl);

        tool.put("functionDeclarations", functionDeclarations);
        tools.add(tool);
        payload.put("tools", tools);

        return payload;
    }
}