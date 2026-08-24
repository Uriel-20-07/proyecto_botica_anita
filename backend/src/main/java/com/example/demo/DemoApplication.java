package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal de arranque de la aplicación Spring Boot.
 * 
 * @SpringBootApplication combina tres anotaciones:
 *                        - @Configuration: marca esta clase como fuente de
 *                        beans de Spring.
 *                        - @EnableAutoConfiguration: activa la configuración
 *                        automática de Spring.
 *                        - @ComponentScan: escanea el paquete actual y sus
 *                        sub-paquetes en busca de componentes.
 * 
 *                        Esta es la aplicación backend del proyecto MiFarmaCode
 *                        (farmacia en línea).
 */
@SpringBootApplication
public class DemoApplication {

	/**
	 * Punto de entrada de la JVM. Lanza el servidor embebido (Tomcat por defecto)
	 * y registra todos los beans configurados en el contexto de Spring.
	 *
	 * @param args argumentos de línea de comandos (no requeridos).
	 */
	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
