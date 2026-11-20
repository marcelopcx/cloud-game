SELECT 'CREATE DATABASE cloud_game'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cloud_game')\gexec
\c cloud_game

CREATE TABLE IF NOT EXISTS GAME (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    descr VARCHAR(500),
    rating FLOAT CHECK (rating >= 0 AND rating <= 5),
    path VARCHAR(255) NOT NULL,
    min_cont INT DEFAULT 1,
    max_cont INT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('TEST', 'ACTIVE', 'COMING_SOON', 'DISABLED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS CATEGORY (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS IMAGE (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    url_banner_1 VARCHAR(255),
    url_banner_2 VARCHAR(255),
    url_poster VARCHAR(255),
    url_cover VARCHAR(255),
    url_icon VARCHAR(255),
    url_background VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS GAMEPAD (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS GAME_CATEGORY (
    id_game INT,
    id_category INT,
    PRIMARY KEY (id_game, id_category),
    FOREIGN KEY (id_game) REFERENCES GAME(id) ON DELETE CASCADE,
    FOREIGN KEY (id_category) REFERENCES CATEGORY(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS GAME_IMAGE (
    id_game INT,
    id_image INT,
    PRIMARY KEY (id_game, id_image),
    FOREIGN KEY (id_game) REFERENCES GAME(id) ON DELETE CASCADE,
    FOREIGN KEY (id_image) REFERENCES IMAGE(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS GAME_GAMEPAD (
    id_game INT,
    id_gamepad INT,
    PRIMARY KEY (id_game, id_gamepad),
    FOREIGN KEY (id_game) REFERENCES GAME(id) ON DELETE CASCADE,
    FOREIGN KEY (id_gamepad) REFERENCES GAMEPAD(id) ON DELETE CASCADE
);

-- Id 0 = "null" (sin tecla / sin botón): lo inserta el seed con OVERRIDING SYSTEM VALUE.
CREATE TABLE IF NOT EXISTS INPUT_KEYBOARD (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS INPUT_CONTROLLER (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS ACTION (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

-- Qué tecla y/o botón ejecuta cada acción en un juego. Si el juego no tiene
-- teclado (o mando) en GAME_GAMEPAD, esa columna va con 0 ("null").
CREATE TABLE IF NOT EXISTS GAME_INPUT (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_game INT NOT NULL,
    id_input_keyboard INT NOT NULL DEFAULT 0,
    id_input_controller INT NOT NULL DEFAULT 0,
    id_action INT NOT NULL,
    UNIQUE (id_game, id_action),
    CHECK (id_input_keyboard <> 0 OR id_input_controller <> 0),
    FOREIGN KEY (id_game) REFERENCES GAME(id) ON DELETE CASCADE,
    FOREIGN KEY (id_input_keyboard) REFERENCES INPUT_KEYBOARD(id),
    FOREIGN KEY (id_input_controller) REFERENCES INPUT_CONTROLLER(id),
    FOREIGN KEY (id_action) REFERENCES ACTION(id)
);
-- Distribución del home de game-client: secciones en orden (sort_order) y sus juegos en orden.
-- HERO = banner principal, BANNER = sub banner, CAROUSEL = carrusel, GRID = grid con filtro
-- por género (sin juegos = todo el catálogo). Las reglas por tipo las valida HomeService.
CREATE TABLE IF NOT EXISTS HOME_SECTION (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sort_order INT NOT NULL,
    type VARCHAR(20) NOT NULL CONSTRAINT home_section_type_check
        CHECK (type IN ('HERO', 'BANNER', 'CAROUSEL', 'GRID', 'UPCOMING')),
    title VARCHAR(100)
);

-- Borrar un juego lo saca de las secciones (ON DELETE CASCADE).
CREATE TABLE IF NOT EXISTS HOME_SECTION_GAME (
    id_section INT NOT NULL,
    id_game INT NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id_section, id_game),
    FOREIGN KEY (id_section) REFERENCES HOME_SECTION(id) ON DELETE CASCADE,
    FOREIGN KEY (id_game) REFERENCES GAME(id) ON DELETE CASCADE
);

-- Para bases ya creadas: CREATE TABLE IF NOT EXISTS no agrega columnas nuevas.
ALTER TABLE GAME DROP COLUMN IF EXISTS id_container;  -- se agregó sin uso: el juego sale de `path`
ALTER TABLE IMAGE ADD COLUMN IF NOT EXISTS url_icon VARCHAR(255);
-- UPCOMING (carrusel de próximos lanzamientos) se agregó después: se rehace el CHECK.
ALTER TABLE HOME_SECTION DROP CONSTRAINT IF EXISTS home_section_type_check;
ALTER TABLE HOME_SECTION ADD CONSTRAINT home_section_type_check
    CHECK (type IN ('HERO', 'BANNER', 'CAROUSEL', 'GRID', 'UPCOMING'));
-- Estado de publicación: los juegos que ya existían quedan activos.
ALTER TABLE GAME ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('TEST', 'ACTIVE', 'COMING_SOON', 'DISABLED'));
-- Las imágenes se suben después de crear el juego (PUT /games/{id}/images/{tipo}): la fila de IMAGE
-- nace con el primer archivo, que puede no ser el póster. game-admin exige el póster al crear.
ALTER TABLE IMAGE ALTER COLUMN url_poster DROP NOT NULL;

-- Reasignación de imágenes (una sola vez, al crear url_background): banner 1 = banner principal,
-- banner 2 = sub banner, póster = carrusel (vertical), cover = grid (horizontal), portada
-- (url_background) = fondo de la ficha. Antes: póster = grid, cover = hero, banner 1 = carrusel.
DO $$
    BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema() AND table_name = 'image' AND column_name = 'url_background') THEN
        ALTER TABLE IMAGE ADD COLUMN url_background VARCHAR(255);
        -- Los lados derechos leen la fila vieja: el intercambio no se pisa.
        UPDATE IMAGE SET url_background = url_cover,
                url_banner_1   = url_cover,
                url_cover      = url_poster,
                url_poster     = COALESCE(url_banner_1, url_poster)
        WHERE TRUE;
        END IF;
END $$;


-- Jugadores de game-client. "user" es palabra reservada en PostgreSQL, por eso la tabla es USERS.
-- Se identifica por email; la contraseña se guarda con bcrypt en password_hash.
-- username es el nombre que elige el usuario la primera vez que entra.
-- Bases anteriores: la tabla se llamaba APP_USER y tenía id_role → ROLE; se renombra con sus filas.
DO $$
BEGIN
    IF to_regclass('app_user') IS NOT NULL AND to_regclass('users') IS NULL THEN
        ALTER TABLE app_user RENAME TO users;
        ALTER INDEX IF EXISTS app_user_pkey RENAME TO users_pkey;
        ALTER INDEX IF EXISTS app_user_email_key RENAME TO users_email_key;
        ALTER INDEX IF EXISTS app_user_username_key RENAME TO users_username_key;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS USERS (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100)
);

-- Para bases ya creadas con cedula/password: las filas existentes quedan con un email provisorio.
ALTER TABLE USERS ADD COLUMN IF NOT EXISTS email VARCHAR(255);
UPDATE USERS SET email = 'usuario-' || id || '@sin-email.local' WHERE email IS NULL;
ALTER TABLE USERS ALTER COLUMN email SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS users_email_key ON USERS (email);
ALTER TABLE USERS ADD COLUMN IF NOT EXISTS username VARCHAR(100);
UPDATE USERS SET username = 'usuario-' || id WHERE username IS NULL;
ALTER TABLE USERS ALTER COLUMN username SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS users_username_key ON USERS (username);
ALTER TABLE USERS DROP COLUMN IF EXISTS cedula;
ALTER TABLE USERS DROP COLUMN IF EXISTS password;
-- Ya no hay roles: los administradores viven en ADMIN, separados de los jugadores.
ALTER TABLE USERS DROP COLUMN IF EXISTS id_role;
DROP TABLE IF EXISTS ROLE;

-- Avatares que los jugadores pueden elegir (no suben los suyos). Los administra game-admin; el
-- archivo vive en IMAGES_ROOT/avatars/ y se sirve en /media/avatars/. Los inactivos no se ofrecen,
-- pero los que ya lo tienen lo conservan. Con la tabla vacía, game-crud carga los de fábrica.
CREATE TABLE IF NOT EXISTS AVATAR (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    url VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Avatar elegido por el jugador; borrar el avatar lo deja sin ninguno (game-client muestra la inicial).
ALTER TABLE USERS ADD COLUMN IF NOT EXISTS id_avatar INT REFERENCES AVATAR(id) ON DELETE SET NULL;

-- Administradores del panel /admin/, /docs/ y /api-docs/ (nginx los valida con GET /admins/verify).
-- password_hash es bcrypt; el primero lo crea game-crud con ADMIN_USER/ADMIN_PASSWORD si la tabla está vacía.
CREATE TABLE IF NOT EXISTS ADMIN (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Solicitud de amistad: id_sender la emite, id_receiver la recibe.
CREATE TABLE IF NOT EXISTS FRIEND (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_sender INT NOT NULL,
    id_receiver INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    UNIQUE (id_sender, id_receiver),
    CHECK (id_sender <> id_receiver),
    FOREIGN KEY (id_sender) REFERENCES USERS(id) ON DELETE CASCADE,
    FOREIGN KEY (id_receiver) REFERENCES USERS(id) ON DELETE CASCADE
);

-- Juegos de un usuario. Si está jugando (y a qué) no se guarda acá: lo sabe game-dispatcher
-- en memoria (GET /players/playing), así nunca queda un estado viejo en la base.
CREATE TABLE IF NOT EXISTS GAME_USER (
    id_game INT,
    id_user INT,
    PRIMARY KEY (id_game, id_user),
    FOREIGN KEY (id_game) REFERENCES GAME(id) ON DELETE CASCADE,
    FOREIGN KEY (id_user) REFERENCES USERS(id) ON DELETE CASCADE
);
ALTER TABLE GAME_USER DROP COLUMN IF EXISTS status;

-- Save de un jugador con sesión en un juego: el archivo está en SAVES_ROOT/<id_user>/<game_id>.tar.gz.
-- game_id es el id de la estación (último segmento de GAME.path), no GAME.id.
CREATE TABLE IF NOT EXISTS SAVE_GAME (
    id_user INT NOT NULL,
    game_id VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id_user, game_id),
    FOREIGN KEY (id_user) REFERENCES USERS(id) ON DELETE CASCADE
);

-- Cuentas locales: bcrypt de la contraseña (null en filas creadas antes del registro propio).
ALTER TABLE USERS ADD COLUMN IF NOT EXISTS password_hash VARCHAR(100);
