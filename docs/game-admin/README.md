# Game Admin

Panel web para administrar el catálogo de juegos (lista, alta, edición y baja) y la distribución
del home de game-client, contra [game-crud](../game-crud/README.md), y los administradores que entran al panel. Se publica en
`https://<PUBLIC_HOST>/admin/` con basic auth contra la tabla `ADMIN`.

| | |
| --- | --- |
| Código | [`game-admin/`](../../game-admin) |
| Framework | React 19 + TypeScript 6 |
| Build | Vite 8 (`@vitejs/plugin-react`), base `/admin/` |
| Servido por | nginx 1.27 (imagen Docker), en `127.0.0.1:8097` |
| Dependencias de runtime | Solo `react` y `react-dom`. |

## Pantalla

Cuatro pestañas en la barra superior, **Juegos**, **Home** (`#home` en la URL), **Avatares** (`#avatars`) y **Administradores** (`#admins`), sin router. El editor
del home queda montado al cambiar de pestaña, así que el borrador no se pierde. Estética provisoria (paleta e Inter de game-client, oscuro
por defecto y claro con `prefers-color-scheme`) hasta que llegue el diseño final; los colores están
como tokens en `src/index.css`.

| Parte | Componente | Qué hace | game-crud |
| --- | --- | --- | --- |
| Barra superior | `App.tsx` + `HealthBadge` | Pestañas, enlaces a `/docs/` y `/api-docs/`; estado de game-crud cada 15 s (en línea, base caída o sin conexión). | `GET /health` |
| Resumen | `App.tsx` | Juegos, categorías, rating promedio y juegos sin banner 1, banner 2, cover, portada o ícono. | `GET /games`, `GET /categories` |
| Filtros | `components/toolbar/Toolbar.tsx` | Buscador por nombre (espera 250 ms entre teclas) y chips de categoría con su cantidad. Con los dos, la intersección. | `GET /games/search?q=&limit=50`, `GET /categories/{id}/games` |
| Grid | `components/grid/GameGrid.tsx` | Tarjeta por juego: cover (horizontal 920×430, o el póster si no tiene), ícono, rating, ruta, categorías y gamepads, con **Editar**, **Controles** y **Eliminar**. Un clic abre la ficha. | — |
| Ficha | `components/detail/GameDetail.tsx` | Todos los campos, fecha de alta, los controles y las imágenes con su uso en game-client. | `GET /games/{id}`, `GET /games/{id}/inputs` |
| Controles | `components/controls/ControlsModal.tsx` | Editor de los controles del juego (ver [Controles](#controles)). | `GET /inputs`, `GET/PUT /games/{id}/inputs` |
| Formulario | `components/form/GameFormModal.tsx` | `<dialog>` para crear o editar, con vista previa de la tarjeta. | `POST /games`, `PATCH /games/{id}` |
| Borrar | `components/confirm/ConfirmDialog.tsx` | Pide confirmación y muestra el error en el mismo diálogo si falla. | `DELETE /games/{id}` |
| Avatares | `screens/AvatarsScreen.tsx` | Avatares que eligen los jugadores (no suben los suyos): alta (nombre e imagen), edición (nombre, imagen, disponible u oculto) y baja con confirmación. Oculto = no se ofrece, pero quien lo tiene lo conserva. | `GET /avatars?all=true`, `POST /avatars?name=`, `PATCH /avatars/{id}`, `PUT /avatars/{id}/image`, `DELETE /avatars/{id}` |
| Administradores | `screens/AdminsScreen.tsx` | Lista, alta (usuario y contraseña repetida), cambio de contraseña y baja con confirmación. El último no se puede borrar. | `GET/POST /admins`, `PUT /admins/{id}/password`, `DELETE /admins/{id}` |
| Avisos | `components/toast/Toasts.tsx` | Resultado de cada operación; los de éxito se van solos. | — |

Tras crear, editar o borrar se vuelven a pedir la lista, las categorías y el filtro activo.

## Home

`screens/HomeLayoutScreen.tsx` edita la distribución del home (`GET /home`, `PUT /home`; reglas en
[Game Crud → Home](../game-crud/README.md#home)). Se trabaja sobre un borrador y **Guardar home** lo
envía entero; **Descartar cambios** vuelve a lo guardado. Con cambios sin guardar la pestaña muestra
un punto y el navegador avisa antes de cerrar.

| Acción | Cómo |
| --- | --- |
| Agregar sección | Botones **Banner principal** (uno solo, siempre primero), **Sub banner**, **Carrusel**, **Grid** y **Próximamente** (carrusel que se llena solo con los juegos en estado «Próximamente»; no se eligen juegos). |
| Ordenar secciones | Arrastrando desde el asa (⋮⋮) o con las flechas ↑ ↓. El banner principal no se mueve. |
| Elegir juegos | Selector con búsqueda por nombre o categoría (`GamePickerDialog`): en banners elige uno; en carrusel y grid agrega o quita. |
| Ordenar juegos | Arrastrando la tarjeta o con ‹ ›. |
| Títulos | Carrusel, grid y próximamente. |

Una sección incompleta (banner sin juego, carrusel vacío) se marca en rojo y no deja guardar. Si no hay
secciones guardadas, game-client usa su home por defecto; **Empezar desde la actual** carga esa
distribución (por `path`) para editarla. A la derecha, el **Mapa del home** muestra el orden de un vistazo.

## Controles

El botón **Controles** (en la tarjeta y en la ficha) abre el editor de `GAME_INPUT`: lo que el jugador
ve en el modal de controles del reproductor de game-client. Una fila por acción, con su tecla y su
botón de mando (nomenclatura Xbox):

| Columna | Cómo |
| --- | --- |
| Acción | Texto libre con sugerencias de `ACTION`; las nuevas se crean al guardar. No se repiten. |
| Teclado y ratón | Select de `INPUT_KEYBOARD`; `—` = sin tecla. Solo si el juego tiene «Teclado y ratón» en sus gamepads. |
| Mando | Select de `INPUT_CONTROLLER`; `—` = sin botón. Solo si el juego tiene «Mando» en sus gamepads. |

Cada acción necesita tecla o botón. **Guardar controles** reemplaza todos (`PUT /games/{id}/inputs`);
se muestran ordenados como el catálogo de acciones, así que las nuevas quedan al final. Si el juego no
tiene ni «Mando» ni «Teclado y ratón», el editor ofrece ir a **Editar gamepads**. Si se le quitó uno de
esos gamepads, las teclas o botones de ese tipo se descartan al guardar (el editor lo avisa).

## Formulario

Los límites son los de `game-crud/schemas/schema.sql`; el navegador los valida antes de enviar.

| Campo | Regla | Columna |
| --- | --- | --- |
| Nombre | Obligatorio, máx. 100 | `GAME.name` |
| Ruta | Obligatoria, máx. 255. Al crear se completa desde el nombre (`/games/<slug>`) hasta que se escriba a mano | `GAME.path` |
| Descripción | Máx. 500 | `GAME.descr` |
| Rating | 0 a 5, paso 0.1 | `GAME.rating` |
| Contenedores mín. / máx. | Enteros ≥ 1; máx. vacío = sin límite | `min_cont` / `max_cont` |
| Estado | De prueba (solo game-client en desarrollo), Activo (default), Próximamente (visible sin poder jugar) o Desactivado (oculto para los jugadores). La grilla y la ficha lo muestran como badge; los desactivados se ven atenuados | `GAME.status` |
| Póster | Archivo obligatorio al crear | `IMAGE.url_poster` |
| Banner 1, banner 2, cover, portada, ícono | Archivos opcionales | `IMAGE.url_banner_1`, `url_banner_2`, `url_cover`, `url_background`, `url_icon` |
| Categorías | Chips: sugiere las de `GET /categories` y permite crear nuevas | `GAME_CATEGORY` |
| Gamepads | Chips: sugiere los del seed (`Mando`, `Teclado y ratón`, `Táctil`) y los que ya usan los juegos; permite crear nuevos | `GAME_GAMEPAD` |

Las imágenes se eligen como archivos locales (PNG, JPEG, WebP, GIF o ICO, hasta 25 MB; game-crud las
achica y convierte a WebP) y se suben a game-crud al guardar, después del JSON del juego (`PUT /games/{id}/images/{tipo}`): no se aceptan URLs
externas. Cada una muestra una miniatura con la proporción de su tipo en SteamGridDB; **Reemplazar**
pisa la imagen de ese tipo en el servidor (el archivo anterior se borra), **Quitar** la elimina (salvo
el póster) y **Deshacer** descarta el cambio pendiente. Si el juego se guardó pero falla una imagen, el
modal se cierra igual y un aviso pide editarlo para reintentar (al crear, reintentar duplicaría el juego). En los selectores de chips, Enter o coma agregan (primero una sugerencia que empiece por el
texto, si no el texto como nuevo) y Backspace con el campo vacío quita el último.

Al **editar**, el formulario compara con el juego original y envía solo lo que cambió
(`PATCH` parcial). Un campo opcional vaciado se envía como `""` o `null` para borrarlo. Sin
cambios, **Guardar cambios** queda deshabilitado; al cancelar se descartan las ediciones. Al
cancelar un alta, el borrador se conserva hasta crear el juego.

Los errores de game-crud (`{ "error": { "code", "message" } }`) se muestran en el formulario
(`No se pudo crear el juego: <message>`); `GameRequestError` conserva el `status` y el `code`. Si
responde el proxy sin código (`502`–`504`) o no hay respuesta, el aviso es *No se pudo conectar
con game-crud* (`describeError` en `services/api/games.ts`).

## Estructura

```
src/
├── app/            App.tsx: barra con pestañas, catálogo (resumen, filtros, grid), modales y avisos
├── screens/        HomeLayoutScreen: editor de la distribución del home
├── components/     button, confirm, detail, form (+ ImageField), game-picker, grid, health, icons, tag-picker, toast, toolbar
├── hooks/          useCatalog, useFilteredGames, useDebounced, useDragSort, useHealth, useModalDialog, useToasts
├── services/api/   games.ts (list/search/get/create/update/delete), categories.ts, health.ts, home.ts
└── types/          Game, CreateGameRequest, UpdateGameRequest, Category, HomeResponse, UpdateHomeRequest
```

Aliases de import (`vite.config.ts`): `@` → `src`, `@components`, `@hooks`, `@services`, `@screens`.

## Configuración

| Variable | Dónde | Default | Uso |
| --- | --- | --- | --- |
| `VITE_CRUD_URL` | build | `/api` (imagen: `/admin/api`) | Base de game-crud en el navegador. |
| `CRUD_PROXY_TARGET` | `npm run dev` / `preview` | `http://127.0.0.1:8096` | Destino del proxy `/api` de Vite. |
| `BASE_PATH` | build arg de Docker | `/admin/` | Base de Vite (`--base`). |
| `ADMIN_LISTEN` | contenedor | `127.0.0.1:8097` | Dónde escucha el nginx. |
| `CRUD_UPSTREAM` | contenedor | `http://127.0.0.1:8096` | game-crud para `/api/`. |

## Desarrollo local

```bash
# game-crud corriendo en :8096 (ver Game Crud → Desarrollo local)
cd game-admin
npm ci
npm run dev        # http://localhost:5174 (5173 lo usa game-client)
```

## Imagen Docker y acceso

El `Dockerfile` compila con `--base=/admin/` y `VITE_CRUD_URL=/admin/api`, y sirve el resultado con
nginx en `127.0.0.1:8097`. Ese nginx reenvía `/api/` a game-crud (mismo origen: sin CORS).

En el servidor no se expone directo: el nginx de game-client publica `/admin/` con **basic auth**
contra la tabla `ADMIN` de game-crud (`auth_request` a `GET /admins/verify`). El primer
administrador lo crea game-crud con `ADMIN_USER` (default `admin`) y `ADMIN_PASSWORD` del `.env`
(`deploy-loader.sh` la genera la primera vez) si la tabla está vacía; los demás se crean en la
pestaña **Administradores**. Los mismos usuarios abren la documentación (`/docs/`) y la referencia de
APIs (`/api-docs/`). Si game-crud está caído, las tres responden 500.

El CI compila el panel (`npm run build`) en cada push.
