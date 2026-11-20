# Requisitos de un juego

Guía para desarrolladores que quieran publicar su juego en Cloud Game. El juego corre en
nuestros servidores y se transmite al navegador del jugador: no hace falta adaptar el código a la
nube, pero sí cumplir unos pocos requisitos.

## Requisitos

| | Requisito |
| --- | --- |
| **Sistema** | Build para **Linux ARM64 (aarch64)**, compatible con Ubuntu 22.04 (glibc 2.35 o anterior). No hay Windows, Wine ni emulación x86. |
| **Motor** | Cualquier motor con build nativa Linux ARM64. Godot y Ren'Py sirven aunque solo tengas build de Windows/x86: con el `.pck` o la carpeta del juego basta y nosotros ponemos el runtime ARM64 de la misma versión. |
| **Arranque** | Se inicia con un solo comando, sin launcher, cuenta, login ni conexión obligatoria a internet. |
| **Ventana** | Abre una ventana (X11, no fullscreen exclusivo) con la resolución que le indiquemos, por parámetro o por archivo de configuración. Por defecto, 1280x720. |
| **Gráficos** | OpenGL (cualquier versión, ES incluido). Vulkan es posible pero hay que probarlo caso por caso. |
| **Sin diálogos al inicio** | Nada de selectores de idioma, configuración inicial ni avisos que haya que aceptar fuera del juego. Si existen, indicanos cómo saltearlos (config precargada o parámetro). |
| **Controles** | Jugable con teclado, mouse y/o gamepad (hasta 4, tipo Xbox, vía SDL2 o evdev). |
| **Audio** | Salida estándar de Linux (PulseAudio, ALSA, SDL u OpenAL). |
| **Partidas guardadas** | Indicanos la carpeta donde el juego guarda config y saves, para conservar el progreso de cada jugador. |
| **Derechos** | Autorización para transmitir el juego desde nuestros servidores. |

## Qué nos tenés que enviar

1. **La build del juego** para Linux ARM64 (`.zip`/`.tar.gz`), o el `.pck`/carpeta del juego si es
   Godot o Ren'Py.
2. **Una ficha técnica** con:
   - Nombre, versión, motor y versión del motor.
   - Comando para iniciarlo y parámetros disponibles (resolución, modo ventana, desactivar sonido).
   - Controles soportados: teclado, mouse (cursor o cámara tipo FPS), cantidad de gamepads.
   - Ruta donde guarda la configuración y las partidas.
   - Dependencias del sistema que necesite (librerías, versión de OpenGL/Vulkan).
3. **Material para el catálogo:** descripción corta, género, portada e imágenes.
4. **La autorización de distribución** por escrito.

Con eso nosotros armamos la imagen del juego, lo probamos en el servidor y te avisamos si hace falta
algún ajuste antes de publicarlo.
