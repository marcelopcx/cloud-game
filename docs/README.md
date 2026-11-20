# Documentación

Toda la documentación del proyecto vive en esta carpeta, organizada por componente. Se publica
como sitio en `https://<PUBLIC_HOST>/docs/` ([game-docs](game-docs/README.md)).

| Documento | Contenido |
| --- | --- |
| [Arquitectura](arquitectura.md) | Diagrama objetivo, qué está implementado, topología, flujo de partida y máquina de estados. |
| [Despliegue](despliegue.md) | Servidor Linux: modos (`deploy-loader.sh`, `deploy.sh`, `deploy-all.sh`), `.env`, Compose, TLS, CI/CD, GPU, uinput, firewall y solución de problemas. |
| [nginx](nginx.md) | Puerta de entrada HTTP: instancias, rutas, WebSocket, login de admin, TLS y variables de las plantillas. |
| **Game Station** | |
| [Visión general](game-station/README.md) | Stack, estructura del código, ciclo de vida, configuración, imagen Docker y tests. |
| [API](game-station/api.md) | Contrato REST, `/ws/control`, `/ws/webrtc` y códigos de error. |
| [Video, audio y red](game-station/media.md) | Xvfb, captura y codificación, audio Pulse, filtrado ICE. |
| [Input remoto](game-station/input.md) | DataChannel `input`, datagrama v3, uinput, `libgameinput.so` y XTEST. |
| **Game Loader** | |
| [Guía del loader](game-loader/README.md) | Alcance, API, slots, labels, logs, seguridad, despliegue y cliente con una estación por partida. |
| **Game Client** | |
| [Visión general](game-client/README.md) | Pantallas, `useStation`, WebRTC, configuración, nginx/TLS y scripts. |
| **Catálogo** | |
| [Game Crud](game-crud/README.md) | API del catálogo sobre PostgreSQL: modelo de datos, endpoints, reglas, errores y configuración. |
| [Game Admin](game-admin/README.md) | Panel del catálogo: pantalla, formulario, configuración y administradores y acceso con basic auth. |
| **Juegos** | |
| [Catálogo](../games/README.md) | Los 22 juegos, ROMs que requiere cada port y notas por título. |
| [Manifiesto](games/README.md) | Campos de `manifest.yaml`, detalle de `supertuxkart`/`battleship` y cómo agregar un título. |
| **Herramientas** | |
| [Documentación de APIs](../game-api/README.md) | Scalar en `/api-docs/`: OpenAPI de loader, estación y `game-crud`, con "Probar". |
| [Sitio de documentación](game-docs/README.md) | Esta carpeta publicada con Astro Starlight en `/docs/`: cómo se arma y cómo agregar páginas. |
| **Diagramas** | |
| [`arquitectura.drawio.svg`](diagrams/arquitectura.drawio.svg) | Diagrama estructural fuente (se abre con [draw.io](https://app.diagrams.net/)). |
