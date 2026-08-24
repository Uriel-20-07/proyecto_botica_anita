# Manual de Gestión de Repositorio y Flujo de Trabajo

Este documento establece las normativas oficiales de colaboración, control de versiones e integración de código para el equipo de desarrollo (6 integrantes). Su objetivo es garantizar la calidad del software, evitar conflictos en el código fuente y asegurar un despliegue continuo (CI/CD) exitoso en la infraestructura de Microsoft Azure.

---

## 1. Gestión del Proyecto y Roles

El desarrollo se gestiona integralmente mediante **GitHub Projects** utilizando una metodología ágil basada en tableros Kanban y seguimiento por Sprints.

### 1.1 Estados de las Tareas (Issues)
Todo requerimiento, historia de usuario o corrección debe registrarse como un *Issue* y transitar por los siguientes estados en el tablero:
*   **Por hacer (To Do):** Tareas planificadas y asignadas en espera de ser iniciadas.
*   **En Progreso (In Progress):** El responsable está trabajando activamente en la tarea.
*   **Por Revisar (In Review):** Trabajo finalizado localmente que requiere revisión de código (Pull Request) y validación de calidad.
*   **Finalizado (Done):** Tareas que han superado las pruebas y cuyo código ha sido fusionado a la rama principal.

### 1.2 Roles del Equipo
Cada *Issue* debe tener asignado un responsable con un rol claramente etiquetado para definir su alcance:
*   Líder de Proyecto / Owner
*   Desarrollador Backend
*   Desarrollador Frontend
*   Desarrollador Fullstack
*   Base de Datos
*   QA / Testing
*   Documentador

---

## 2. Estrategia de Ramificación (Branching)

Para aislar el desarrollo y proteger el entorno de producción, implementamos una estrategia de ramas estructurada:

*   **`main` (Producción):** Es la rama principal. Contiene únicamente código estable, auditado y funcional. **Queda estrictamente prohibido realizar *commits* directos a esta rama.**
*   **Ramas de Integración (Ej. `DevBackFa`, `devfront_*`):** Utilizadas para unificar módulos complejos de un mismo entorno (Backend o Frontend) antes de enviarlos a producción.
*   **Ramas de Funcionalidad (`feature/*`):** Creadas para el desarrollo de nuevas historias de usuario (Ej. `feature/stripe-pago`, `feature/DevFrontDi`).
*   **Ramas de Corrección (`arreglo-*` o `fix/*`):** Destinadas exclusivamente a la resolución de errores (Ej. `arreglo-cors`).

---

## 3. Convención de Commits

Para mantener la trazabilidad en el historial de cambios, todo mensaje de *commit* debe ser descriptivo y utilizar prefijos semánticos:
*   `feat:` Implementación de una nueva funcionalidad o módulo.
*   `fix:` Corrección de errores o bugs.
*   `docs:` Actualizaciones en la documentación.
*   `chore:` Tareas de mantenimiento (actualización de dependencias, scripts de CI/CD).

---

## 4. Política de Pull Requests (PR) e Integración Continua

El tránsito de código hacia la rama `main` está protegido por un flujo de revisión estricto. Para que un Pull Request sea aprobado y fusionado, debe cumplir con los siguientes criterios:

1.  **Ejecución de Pipelines de CI:** La creación del PR detonará automáticamente las pruebas de GitHub Actions. Es obligatorio que los flujos de validación finalicen sin errores (Estado: *Success*). Esto incluye:
    *   `CI de Calidad - Equipo de 3`
    *   `Validar Calidad de Código`
    *   Flujos de pre-construcción de Azure Static Web Apps y Spring Boot.
2.  **Revisión Obligatoria (Review Required):** Todo PR requiere revisión humana.
3.  **Aprobación del Owner:** Únicamente el dueño del repositorio (Líder de Proyecto) tiene la autorización para realizar el *Merge* final a `main`, previa verificación de los checks automatizados y resolución de conflictos.

---

## 5. Automatización y Despliegue (CD)

Al realizarse la fusión exitosa hacia `main`, el repositorio está configurado para ejecutar el Despliegue Continuo (CD) sin intervención manual:
*   **Backend (Java/Spring Boot):** Se compila con Maven y se empaqueta para su publicación en Azure Web Apps.
*   **Frontend (Angular):** Se instala, construye y despliega en Azure Static Web Apps.
*   **Mantenimiento de Seguridad:** `Dependabot` monitorea activamente el repositorio para generar actualizaciones automáticas ante vulnerabilidades en las librerías.
