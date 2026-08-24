package com.example.demo.controllers;

import com.example.demo.models.Producto;
import com.example.demo.services.ProductoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    @Autowired
    private ProductoService productoService;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            errorResponse.content = "Hola! Soy el asistente de FarmaCode. Para poder ayudarte de forma inteligente, necesitas configurar una clave de API válida para Gemini (`gemini.api.key`) en el archivo `application.properties` del backend. ¡Es muy sencillo y gratuito!";
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
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(geminiPayload, headers);

                ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
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
                                        
                                        // Agregar la llamada a la función al historial
                                        Map<String, Object> modelCallMap = new HashMap<>();
                                        modelCallMap.put("role", "model");
                                        List<Map<String, Object>> modelParts = new ArrayList<>();
                                        Map<String, Object> modelPart = new HashMap<>();
                                        modelPart.put("functionCall", functionCall);
                                        modelParts.add(modelPart);
                                        modelCallMap.put("parts", modelParts);
                                        contents.add(modelCallMap);

                                        // Agregar la respuesta de la función al historial
                                        Map<String, Object> functionRespMap = new HashMap<>();
                                        functionRespMap.put("role", "function");
                                        List<Map<String, Object>> funcParts = new ArrayList<>();
                                        Map<String, Object> funcPart = new HashMap<>();
                                        
                                        Map<String, Object> functionResponse = new HashMap<>();
                                        functionResponse.put("name", "buscarProductos");
                                        
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
        part.put("text", "Eres el Asistente Virtual Inteligente de FarmaCode, una botica ubicada en Arequipa. Tu objetivo es ayudar a los clientes a encontrar medicamentos, asesorarles con respuestas sobre su salud y facilitarles su compra. \n\n" +
                "REGLAS CRÍTICAS DE COMPORTAMIENTO:\n" +
                "1. SOLO responde consultas que tengan relación con la farmacia FarmaCode (salud, medicamentos, envíos, métodos de entrega y procesos de compra). Si te hacen preguntas fuera de este contexto (como operaciones matemáticas, sumas, historia, programación, etc.), debes rechazar responderlas amablemente indicando que solo estás capacitado para atender consultas relacionadas con la farmacia FarmaCode.\n" +
                "2. NUNCA menciones la cantidad exacta de unidades en stock. Si hay existencias disponibles (stock > 0), limítate a confirmar que 'sí contamos con stock disponible' o 'está disponible', pero jamás menciones números de stock (ej. NO digas 'tenemos 361 unidades').\n" +
                "3. Si un producto está en oferta/descuento (`enOferta` es verdadero y tiene `precioOferta` o `precioConDescuento` menor que `precioVenta`), debes informar explícitamente al cliente que este producto está con descuento y mostrar tanto el precio regular (`precioVenta` o `precioNormal`) como el precio de oferta con descuento (`precioOferta` o `precioConDescuento`) de forma llamativa (ej. '¡Este producto cuenta con descuento! Su precio regular es S/. 5.00, pero ahora está a solo S/. 3.50'). Si no tiene descuento, menciona únicamente su precio normal.\n\n" +
                "Siempre que un usuario te pregunte por medicamentos o productos en stock, DEBES llamar obligatoriamente a la función 'buscarProductos' pasándole la consulta adecuada. " +
                "Si encuentras productos con la función, descríbelos de manera amable indicando sus precios (aplicando la regla de descuento si corresponde) y confirmando que contamos con disponibilidad, respetando las reglas de stock anteriores. " +
                "Si el usuario desea agregar un producto al carrito (por ejemplo, 'agrega un paracetamol al carrito'), DEBES llamar a la función 'agregarAlCarrito' especificando el ID del producto y la cantidad. " +
                "Si el usuario desea ir a pagar, ver su carrito, ir al catálogo o a su perfil, DEBES llamar a la función 'redirigir' especificando la ruta correspondiente (ej: '/pago', '/catalogo', '/perfil'). " +
                "Si el usuario hace consultas médicas graves, recuérdale con empatía que eres un asistente virtual y que debe consultar a un médico especialista.");
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
