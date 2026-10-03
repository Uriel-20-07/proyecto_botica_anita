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

    // Modelo principal (mejor calidad, cuota gratuita muy baja: ~20 solicitudes/día).
    @Value("${gemini.model.primary:gemini-3.8-flash}")
    private String geminiModelPrimary;

    // Modelo de respaldo: se usa automáticamente si el principal responde 429 (cuota) o 503
    // (saturado). Flash-Lite tiene cuota gratuita mucho más generosa (~500 solicitudes/día).
    @Value("${gemini.model.fallback:gemini-3.5-flash-lite}")
    private String geminiModelFallback;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Llama a Gemini con el modelo principal; si responde 429 (cuota agotada) o 503
     * (modelo saturado), reintenta automáticamente una vez con el modelo de respaldo.
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

            // Realizar bucle de llamada para resolver llamadas a funciones internas (buscarProductos)
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
                                    Map<String, Object> functionCall = (Map<String, Object>) firstPart.get("functionCall");
                                    String functionName = (String) functionCall.get("name");
                                    Map<String, Object> args = (Map<String, Object>) functionCall.get("args");

                                    // Si es buscarProductos, la resolvemos aquí mismo en el backend
                                    if ("buscarProductos".equals(functionName)) {
                                        String query = args != null && args.containsKey("query") ? (String) args.get("query") : "";
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
                                            simplified.put("precio", enOferta && p.getPrecioOferta() != null ? p.getPrecioOferta() : p.getPrecioVenta());
                                            
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
                err.content = "No se pudo autenticar con Gemini (error " + status + "). Verifica en Google AI Studio que la clave en `gemini.api.key` sea de tipo Auth (empieza con 'AQ.'), esté activa, y que la 'Generative Language API' esté habilitada en el proyecto.";
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
            err.content = "Ocurrió un error en el servidor al intentar contactar con el asistente virtual: " + e.getMessage();
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
        part.put("text", "Te llamas SofIA, la Asistente Virtual Inteligente de BoticaAnita, una botica ubicada en Lima. Preséntate con tu nombre (SofIA) cuando saludes por primera vez. Tu objetivo es ayudar a los clientes a encontrar medicamentos, asesorarles con calidez sobre su salud y facilitarles su compra. \n\n" +
                "REGLAS CRÍTICAS DE COMPORTAMIENTO:\n" +
                "1. SOLO responde consultas que tengan relación con la farmacia BoticaAnita (salud, medicamentos, envíos, métodos de entrega y procesos de compra). Si te hacen preguntas fuera de este contexto (como operaciones matemáticas, sumas, historia, programación, etc.), debes rechazar responderlas amablemente indicando que solo estás capacitada para atender consultas relacionadas con la farmacia BoticaAnita.\n" +
                "2. NUNCA menciones la cantidad exacta de unidades en stock. Si hay existencias disponibles (stock > 0), limítate a confirmar que 'sí contamos con stock disponible' o 'está disponible', pero jamás menciones números de stock (ej. NO digas 'tenemos 361 unidades').\n" +
                "3. Si un producto está en oferta/descuento (`enOferta` es verdadero y tiene `precioOferta` o `precioConDescuento` menor que `precioVenta`), debes informar explícitamente al cliente que este producto está con descuento y mostrar tanto el precio regular (`precioVenta` o `precioNormal`) como el precio de oferta con descuento (`precioOferta` o `precioConDescuento`) de forma llamativa (ej. '¡Este producto cuenta con descuento! Su precio regular es S/. 5.00, pero ahora está a solo S/. 3.50'). Si no tiene descuento, menciona únicamente su precio normal.\n" +
                "4. Si la función 'buscarProductos' devuelve una lista vacía de productos, NUNCA respondas con un error técnico ni digas que 'ocurrió un problema'. En su lugar, asume amablemente que puede haber un error de tipeo o un nombre distinto, y responde algo como: 'No encontré ese producto exactamente, ¿podrías repetirme el nombre o escribirlo de otra forma?'. Si el término se parece mucho a un medicamento conocido (ej. 'jaraba' se parece a 'jarabe'), puedes sugerirlo como posibilidad antes de pedir que lo repita.\n" +
                "5. Cuando el cliente mencione un síntoma o malestar (ej. dolor de cabeza, fiebre, tos, dolor de estómago, malestar general) antes de recomendarle un producto, respóndele primero con una frase breve y cálida que muestre preocupación genuina por su bienestar (ej. 'Lamento que te sientas así, vamos a ayudarte a encontrar algo que te alivie'), y recién después sugiere el producto. Si por lo que describe el malestar suena fuerte, persistente, o es un síntoma de alarma (ej. fiebre alta, dolor muy intenso, dificultad para respirar, dura varios días), agrega con delicadeza una recomendación adicional de que, si no mejora o empeora, acuda a un médico o al establecimiento de salud más cercano, dejando claro que tú eres un apoyo informativo y no reemplazas una consulta médica profesional.\n\n" +
                "Siempre que un usuario te pregunte por medicamentos o productos en stock, DEBES llamar obligatoriamente a la función 'buscarProductos' pasándole la consulta adecuada. " +
                "Si encuentras productos con la función, descríbelos de manera amable indicando sus precios (aplicando la regla de descuento si corresponde) y confirmando que contamos con disponibilidad, respetando las reglas de stock anteriores. " +
                "Si el usuario desea agregar un producto al carrito (por ejemplo, 'agrega un paracetamol al carrito'), DEBES llamar a la función 'agregarAlCarrito' especificando el ID del producto y la cantidad. " +
                "Si el usuario desea ir a pagar, ver su carrito, ir al catálogo o a su perfil, DEBES llamar a la función 'redirigir' especificando la ruta correspondiente (ej: '/pago', '/catalogo', '/perfil').");
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