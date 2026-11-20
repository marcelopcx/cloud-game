-- Controles reales de cada juego (binds por defecto del juego, sacados de su código o config
-- en la versión que instala games/<id>/). Botones con nomenclatura Xbox, como los expone el
-- pad virtual de la estación (SdlMapping: A=b0, B=b1, X=b2, Y=b3, LB=b4, RB=b5, Select=b6,
-- Start=b7, gatillos = ejes). NULL = el juego no tiene ese control por defecto.
--
-- Los juegos se buscan por path (/games/<id>): en una base nueva no hay juegos todavía y esto
-- no inserta nada. Correrlo de nuevo después de darlos de alta en game-admin; es idempotente.
-- Solo carga juegos que todavía no tienen controles: después se editan en game-admin (botón
-- Controles), y un cambio acá no pisa lo que ya está en la base.
-- Igual que GameInputService: si el juego no tiene teclado (o mando) en GAME_GAMEPAD, esa
-- columna va con 0 ("null").

INSERT INTO ACTION (name) VALUES
    ('Acelerar'), ('Frenar'), ('Derrapar'), ('Turbo'), ('Reiniciar'), ('Agarrar'),
    ('Sostener linterna'), ('Aturdir espíritus'), ('Silenciar música'),
    ('Avanzar texto'), ('Retroceder'), ('Adelantar'), ('Saltar texto'), ('Ocultar texto')
ON CONFLICT (name) DO NOTHING;

CREATE TEMP TABLE seed_game_input (path TEXT, action TEXT, keyboard TEXT, controller TEXT);

INSERT INTO seed_game_input VALUES
    -- SuperTuxKart 1.5 (keyboard_config.cpp; gamepad_config.cpp, rama con LB/RB fuera de Switch)
    ('/games/supertuxkart', 'Acelerar',          'Flecha arriba',    'Y'),
    ('/games/supertuxkart', 'Frenar',            'Flecha abajo',     'X'),
    ('/games/supertuxkart', 'Mover izquierda',   'Flecha izquierda', NULL),
    ('/games/supertuxkart', 'Mover derecha',     'Flecha derecha',   NULL),
    ('/games/supertuxkart', 'Mover',             NULL,               'Stick izquierdo'),
    ('/games/supertuxkart', 'Usar objeto',       'Espacio',          'B'),
    ('/games/supertuxkart', 'Turbo',             'N',                'LB'),
    ('/games/supertuxkart', 'Derrapar',          'V',                'RB'),
    ('/games/supertuxkart', 'Mirar / Cámara',    'B',                'A'),       -- mirar atrás
    ('/games/supertuxkart', 'Reiniciar',         'Retroceso',        'Select'),  -- rescate
    ('/games/supertuxkart', 'Pausa',             'Escape',           'Start'),
    ('/games/supertuxkart', 'Aceptar',           'Enter',            'A'),
    ('/games/supertuxkart', 'Cancelar',          'Escape',           'B'),

    -- SuperTux 0.6.3 (keyboard_config.cpp, game_controller_manager.cpp)
    ('/games/supertux', 'Mover arriba',          'Flecha arriba',    'D-Pad arriba'),
    ('/games/supertux', 'Mover izquierda',       'Flecha izquierda', 'D-Pad izquierda'),
    ('/games/supertux', 'Mover derecha',         'Flecha derecha',   'D-Pad derecha'),
    ('/games/supertux', 'Agacharse',             'Flecha abajo',     'D-Pad abajo'),
    ('/games/supertux', 'Mover',                 NULL,               'Stick izquierdo'),
    ('/games/supertux', 'Saltar',                'Espacio',          'A'),
    ('/games/supertux', 'Correr',                'Ctrl izquierdo',   'X'),       -- ACTION (también Alt)
    ('/games/supertux', 'Disparar',              'Ctrl izquierdo',   'X'),
    ('/games/supertux', 'Mirar / Cámara',        NULL,               'Stick derecho'),  -- peek (LB/RB; Inicio/Fin/Supr/Av Pág)
    ('/games/supertux', 'Pausa',                 'Escape',           'Start'),
    ('/games/supertux', 'Aceptar',               'Enter',            'A'),
    ('/games/supertux', 'Cancelar',              'Escape',           'B'),

    -- BattleShip / Smash 64 (defaults de libultraship: stick WASD, botones C en flechas / stick derecho)
    ('/games/battleship', 'Mover arriba',        'W',                NULL),
    ('/games/battleship', 'Mover abajo',         'S',                NULL),
    ('/games/battleship', 'Mover izquierda',     'A',                NULL),
    ('/games/battleship', 'Mover derecha',       'D',                NULL),
    ('/games/battleship', 'Mover',               NULL,               'Stick izquierdo'),
    ('/games/battleship', 'Atacar',              'X',                'A'),       -- N64 A
    ('/games/battleship', 'Habilidad',           'C',                'B'),       -- N64 B: especial
    ('/games/battleship', 'Saltar',              'Flecha arriba',    'Stick derecho'),  -- botones C
    ('/games/battleship', 'Bloquear',            'Z',                'LT'),      -- N64 Z: escudo
    ('/games/battleship', 'Agarrar',             'R',                'RT'),      -- N64 R
    ('/games/battleship', 'Pausa',               'Espacio',          'Start'),

    -- Extreme Tux Racer 0.8.1 (racing.cpp: Keyb, Jbutt con índices joydev)
    ('/games/extremetuxracer', 'Acelerar',       'Flecha arriba',    'A'),       -- b0
    ('/games/extremetuxracer', 'Frenar',         'Flecha abajo',     'X'),       -- b2
    ('/games/extremetuxracer', 'Mover izquierda','Flecha izquierda', NULL),
    ('/games/extremetuxracer', 'Mover derecha',  'Flecha derecha',   NULL),
    ('/games/extremetuxracer', 'Mover',          NULL,               'Stick izquierdo'),
    ('/games/extremetuxracer', 'Saltar',         'Espacio',          'Y'),       -- b3
    ('/games/extremetuxracer', 'Habilidad',      'T',                'B'),       -- b1: truco
    ('/games/extremetuxracer', 'Reiniciar',      'R',                NULL),
    ('/games/extremetuxracer', 'Pausa',          'P',                NULL),
    ('/games/extremetuxracer', 'Cancelar',       'Escape',           NULL),      -- abandona la carrera

    -- Neverball 1.6.0 (share/config.c, ball/st_play.c; el joystick usa índices crudos)
    ('/games/neverball', 'Mover arriba',         'Flecha arriba',    NULL),
    ('/games/neverball', 'Mover abajo',          'Flecha abajo',     NULL),
    ('/games/neverball', 'Mover izquierda',      'Flecha izquierda', NULL),
    ('/games/neverball', 'Mover derecha',        'Flecha derecha',   NULL),
    ('/games/neverball', 'Mover',                'Movimiento del ratón', 'Stick izquierdo'),  -- inclinar
    ('/games/neverball', 'Mirar / Cámara',       'D',                'RB'),      -- girar (S / LB, clics)
    ('/games/neverball', 'Reiniciar',            'R',                NULL),
    ('/games/neverball', 'Pausa',                'Escape',           NULL),      -- joystick_button_start=8 (Guide)
    ('/games/neverball', 'Aceptar',              'Enter',            'A'),
    ('/games/neverball', 'Cancelar',             'Escape',           'B'),

    -- Chromium B.S.U. 0.9.16 (MainSDL_Event.cpp: cualquier botón del mando dispara)
    ('/games/chromium-bsu', 'Mover arriba',      'Flecha arriba',    NULL),
    ('/games/chromium-bsu', 'Mover abajo',       'Flecha abajo',     NULL),
    ('/games/chromium-bsu', 'Mover izquierda',   'Flecha izquierda', NULL),
    ('/games/chromium-bsu', 'Mover derecha',     'Flecha derecha',   NULL),
    ('/games/chromium-bsu', 'Mover',             'Movimiento del ratón', 'Stick izquierdo'),
    ('/games/chromium-bsu', 'Disparar',          'Espacio',          'A'),       -- también clic izquierdo
    ('/games/chromium-bsu', 'Usar objeto',       'Enter',            NULL),      -- también clic derecho
    ('/games/chromium-bsu', 'Cambiar arma',      'Clic central',     NULL),      -- siguiente objeto
    ('/games/chromium-bsu', 'Pausa',             'P',                NULL),

    -- AstroMenace 1.3.2 (ScriptEngine/Setup.cpp)
    ('/games/astromenace', 'Mover arriba',       'Flecha arriba',    NULL),
    ('/games/astromenace', 'Mover abajo',        'Flecha abajo',     NULL),
    ('/games/astromenace', 'Mover izquierda',    'Flecha izquierda', NULL),
    ('/games/astromenace', 'Mover derecha',      'Flecha derecha',   NULL),
    ('/games/astromenace', 'Mover',              'Movimiento del ratón', 'Stick izquierdo'),
    ('/games/astromenace', 'Disparar',           'Ctrl izquierdo',   'A'),       -- primarias (también clic izquierdo)
    ('/games/astromenace', 'Ataque fuerte',      'Espacio',          'B'),       -- secundarias (también clic derecho)
    ('/games/astromenace', 'Pausa',              'Escape',           NULL),

    -- Armagetron Advanced 0.2.9 (keys_cursor.cfg, el esquema recomendado del primer arranque)
    ('/games/armagetronad', 'Mover izquierda',   'Flecha izquierda', NULL),
    ('/games/armagetronad', 'Mover derecha',     'Flecha derecha',   NULL),
    ('/games/armagetronad', 'Frenar',            'Flecha abajo',     NULL),
    ('/games/armagetronad', 'Mirar / Cámara',    'V',                NULL),      -- cambiar vista
    ('/games/armagetronad', 'Pausa',             'Escape',           NULL),

    -- OpenTTD 12.1 (toolbar_gui.cpp, main_gui.cpp)
    ('/games/openttd', 'Interactuar',            'Clic izquierdo',   NULL),
    ('/games/openttd', 'Mirar / Cámara',         'Clic derecho',     NULL),      -- arrastrar desplaza la vista
    ('/games/openttd', 'Mover arriba',           'Flecha arriba',    NULL),
    ('/games/openttd', 'Mover abajo',            'Flecha abajo',     NULL),
    ('/games/openttd', 'Mover izquierda',        'Flecha izquierda', NULL),
    ('/games/openttd', 'Mover derecha',          'Flecha derecha',   NULL),
    ('/games/openttd', 'Mapa',                   'F4',               NULL),      -- también M
    ('/games/openttd', 'Pausa',                  'F1',               NULL),
    ('/games/openttd', 'Cancelar',               'Escape',           NULL),

    -- OpenArena 0.8.8 (default.cfg de pak0.pk3)
    ('/games/openarena', 'Mover arriba',         'W',                NULL),
    ('/games/openarena', 'Mover abajo',          'S',                NULL),
    ('/games/openarena', 'Mover izquierda',      'A',                NULL),
    ('/games/openarena', 'Mover derecha',        'D',                NULL),
    ('/games/openarena', 'Mirar / Cámara',       'Movimiento del ratón', NULL),
    ('/games/openarena', 'Saltar',               'Espacio',          NULL),
    ('/games/openarena', 'Agacharse',            'C',                NULL),
    ('/games/openarena', 'Disparar',             'Clic izquierdo',   NULL),      -- también Ctrl
    ('/games/openarena', 'Apuntar',              'Clic central',     NULL),      -- zoom
    ('/games/openarena', 'Cambiar arma',         'Rueda abajo',      NULL),
    ('/games/openarena', 'Usar objeto',          'Enter',            NULL),
    ('/games/openarena', 'Pausa',                'Escape',           NULL),

    -- Cube 2: Sauerbraten 2020.12.27 (data/defaults.cfg)
    ('/games/sauerbraten', 'Mover arriba',       'W',                NULL),
    ('/games/sauerbraten', 'Mover abajo',        'S',                NULL),
    ('/games/sauerbraten', 'Mover izquierda',    'A',                NULL),
    ('/games/sauerbraten', 'Mover derecha',      'D',                NULL),
    ('/games/sauerbraten', 'Mirar / Cámara',     'Movimiento del ratón', NULL),
    ('/games/sauerbraten', 'Saltar',             'Espacio',          NULL),
    ('/games/sauerbraten', 'Disparar',           'Clic izquierdo',   NULL),
    ('/games/sauerbraten', 'Cambiar arma',       'Rueda arriba',     NULL),
    ('/games/sauerbraten', 'Apuntar',            'Z',                NULL),      -- zoom (también G)
    ('/games/sauerbraten', 'Pausa',              'Escape',           NULL),

    -- Freedoom en PrBoom+ 2.6.2 (m_misc.c)
    ('/games/freedoom', 'Mover arriba',          'W',                NULL),
    ('/games/freedoom', 'Mover abajo',           'S',                NULL),
    ('/games/freedoom', 'Mover izquierda',       'A',                NULL),
    ('/games/freedoom', 'Mover derecha',         'D',                NULL),
    ('/games/freedoom', 'Mirar / Cámara',        'Movimiento del ratón', NULL),
    ('/games/freedoom', 'Disparar',              'Clic izquierdo',   NULL),      -- también Ctrl
    ('/games/freedoom', 'Interactuar',           'Espacio',          NULL),
    ('/games/freedoom', 'Correr',                'Shift izquierdo',  NULL),
    ('/games/freedoom', 'Cambiar arma',          'Rueda arriba',     NULL),      -- también 1-9
    ('/games/freedoom', 'Mapa',                  'Tab',              NULL),
    ('/games/freedoom', 'Pausa',                 'Escape',           NULL),

    -- Ship of Harkinian 9.2.3 / Ocarina of Time (defaults de libultraship)
    ('/games/soh', 'Mover arriba',               'W',                NULL),
    ('/games/soh', 'Mover abajo',                'S',                NULL),
    ('/games/soh', 'Mover izquierda',            'A',                NULL),
    ('/games/soh', 'Mover derecha',              'D',                NULL),
    ('/games/soh', 'Mover',                      NULL,               'Stick izquierdo'),
    ('/games/soh', 'Interactuar',                'X',                'A'),       -- N64 A
    ('/games/soh', 'Atacar',                     'C',                'B'),       -- N64 B: espada
    ('/games/soh', 'Apuntar',                    'Z',                'LT'),      -- N64 Z: fijar
    ('/games/soh', 'Bloquear',                   'R',                'RT'),      -- N64 R: escudo
    ('/games/soh', 'Usar objeto',                'Flecha izquierda', 'Stick derecho'),  -- C izq/abajo/der
    ('/games/soh', 'Mirar / Cámara',             'Flecha arriba',    'Stick derecho'),  -- C arriba
    ('/games/soh', 'Mapa',                       'E',                'LB'),      -- N64 L
    ('/games/soh', 'Inventario',                 'Espacio',          'Start'),
    ('/games/soh', 'Pausa',                      'Espacio',          'Start'),

    -- 2 Ship 2 Harkinian 5.0.1 / Majora's Mask (defaults de libultraship)
    ('/games/2ship', 'Mover arriba',             'W',                NULL),
    ('/games/2ship', 'Mover abajo',              'S',                NULL),
    ('/games/2ship', 'Mover izquierda',          'A',                NULL),
    ('/games/2ship', 'Mover derecha',            'D',                NULL),
    ('/games/2ship', 'Mover',                    NULL,               'Stick izquierdo'),
    ('/games/2ship', 'Interactuar',              'X',                'A'),
    ('/games/2ship', 'Atacar',                   'C',                'B'),
    ('/games/2ship', 'Apuntar',                  'Z',                'LT'),
    ('/games/2ship', 'Bloquear',                 'R',                'RT'),
    ('/games/2ship', 'Usar objeto',              'Flecha izquierda', 'Stick derecho'),
    ('/games/2ship', 'Mirar / Cámara',           'Flecha arriba',    'Stick derecho'),
    ('/games/2ship', 'Mapa',                     'E',                'LB'),
    ('/games/2ship', 'Inventario',               'Espacio',          'Start'),
    ('/games/2ship', 'Pausa',                    'Espacio',          'Start'),

    -- Ghostship 3.0.0 / Super Mario 64 (defaults de libultraship)
    ('/games/ghostship', 'Mover arriba',         'W',                NULL),
    ('/games/ghostship', 'Mover abajo',          'S',                NULL),
    ('/games/ghostship', 'Mover izquierda',      'A',                NULL),
    ('/games/ghostship', 'Mover derecha',        'D',                NULL),
    ('/games/ghostship', 'Mover',                NULL,               'Stick izquierdo'),
    ('/games/ghostship', 'Saltar',               'X',                'A'),
    ('/games/ghostship', 'Atacar',               'C',                'B'),
    ('/games/ghostship', 'Agacharse',            'Z',                'LT'),
    ('/games/ghostship', 'Mirar / Cámara',       'Flecha izquierda', 'Stick derecho'),  -- botones C (R cambia modo)
    ('/games/ghostship', 'Pausa',                'Espacio',          'Start'),

    -- SpaghettiKart 1.0.0 / Mario Kart 64 (defaults propios en src/port/Engine.cpp)
    ('/games/spaghettikart', 'Acelerar',         'Shift izquierdo',  'A'),
    ('/games/spaghettikart', 'Frenar',           'Ctrl izquierdo',   'X'),
    ('/games/spaghettikart', 'Mover izquierda',  'Flecha izquierda', NULL),
    ('/games/spaghettikart', 'Mover derecha',    'Flecha derecha',   NULL),
    ('/games/spaghettikart', 'Mover',            NULL,               'Stick izquierdo'),
    ('/games/spaghettikart', 'Usar objeto',      'Z',                'LT'),
    ('/games/spaghettikart', 'Derrapar',         'Espacio',          'RB'),      -- N64 R: salto / derrape
    ('/games/spaghettikart', 'Pausa',            'Enter',            'Start'),

    -- Starship 2.0.0 / Star Fox 64 (teclado libultraship, mando propio en src/port/Engine.cpp)
    ('/games/starship', 'Mover arriba',          'W',                NULL),
    ('/games/starship', 'Mover abajo',           'S',                NULL),
    ('/games/starship', 'Mover izquierda',       'A',                NULL),
    ('/games/starship', 'Mover derecha',         'D',                NULL),
    ('/games/starship', 'Mover',                 NULL,               'Stick izquierdo'),
    ('/games/starship', 'Disparar',              'X',                'A'),       -- N64 A: láser
    ('/games/starship', 'Ataque fuerte',         'C',                'X'),       -- N64 B: bomba
    ('/games/starship', 'Esquivar',              'R',                'RB'),      -- N64 R (Z: Z / LB): inclinar / tonel
    ('/games/starship', 'Turbo',                 'Flecha izquierda', 'Y'),       -- C izquierda (también RT)
    ('/games/starship', 'Frenar',                'Flecha abajo',     'B'),       -- C abajo (también LT)
    ('/games/starship', 'Mirar / Cámara',        'Flecha arriba',    'Stick derecho'),  -- C arriba
    ('/games/starship', 'Pausa',                 'Espacio',          'Start'),

    -- zelda3 / A Link to the Past (zelda3.ini: KeyMap y GamepadMap; ItemSwitchLR = 0)
    ('/games/zelda3', 'Mover arriba',            'Flecha arriba',    'D-Pad arriba'),
    ('/games/zelda3', 'Mover abajo',             'Flecha abajo',     'D-Pad abajo'),
    ('/games/zelda3', 'Mover izquierda',         'Flecha izquierda', 'D-Pad izquierda'),
    ('/games/zelda3', 'Mover derecha',           'Flecha derecha',   'D-Pad derecha'),
    ('/games/zelda3', 'Mover',                   NULL,               'Stick izquierdo'),
    ('/games/zelda3', 'Atacar',                  'Z',                'A'),       -- SNES B: espada
    ('/games/zelda3', 'Interactuar',             'X',                'B'),       -- SNES A
    ('/games/zelda3', 'Usar objeto',             'A',                'X'),       -- SNES Y
    ('/games/zelda3', 'Mapa',                    'S',                'Y'),       -- SNES X
    ('/games/zelda3', 'Inventario',              'Enter',            'Start'),

    -- smw / Super Mario World (smw.ini)
    ('/games/smw', 'Mover arriba',               'Flecha arriba',    'D-Pad arriba'),
    ('/games/smw', 'Mover izquierda',            'Flecha izquierda', 'D-Pad izquierda'),
    ('/games/smw', 'Mover derecha',              'Flecha derecha',   'D-Pad derecha'),
    ('/games/smw', 'Agacharse',                  'Flecha abajo',     'D-Pad abajo'),
    ('/games/smw', 'Mover',                      NULL,               'Stick izquierdo'),
    ('/games/smw', 'Saltar',                     'Z',                'A'),       -- SNES B
    ('/games/smw', 'Habilidad',                  'X',                'B'),       -- SNES A: salto giratorio
    ('/games/smw', 'Correr',                     'A',                'X'),       -- SNES Y (también X: S / Y)
    ('/games/smw', 'Disparar',                   'A',                'X'),       -- bola de fuego
    ('/games/smw', 'Usar objeto',                'Shift derecho',    'Select'),  -- objeto de reserva
    ('/games/smw', 'Mirar / Cámara',             'C',                'LB'),      -- SNES L (R: V / RB)
    ('/games/smw', 'Pausa',                      'Enter',            'Start'),

    -- sm / Super Metroid (sm.ini)
    ('/games/sm', 'Mover arriba',                'Flecha arriba',    'D-Pad arriba'),
    ('/games/sm', 'Mover abajo',                 'Flecha abajo',     'D-Pad abajo'),
    ('/games/sm', 'Mover izquierda',             'Flecha izquierda', 'D-Pad izquierda'),
    ('/games/sm', 'Mover derecha',               'Flecha derecha',   'D-Pad derecha'),
    ('/games/sm', 'Mover',                       NULL,               'Stick izquierdo'),
    ('/games/sm', 'Disparar',                    'S',                'Y'),       -- SNES X
    ('/games/sm', 'Saltar',                      'X',                'B'),       -- SNES A
    ('/games/sm', 'Correr',                      'Z',                'A'),       -- SNES B
    ('/games/sm', 'Cancelar',                    'A',                'X'),       -- SNES Y: cancelar arma
    ('/games/sm', 'Cambiar arma',                'Shift derecho',    'Select'),
    ('/games/sm', 'Apuntar',                     'V',                'RB'),      -- SNES R arriba (L: C / LB abajo)
    ('/games/sm', 'Pausa',                       'Enter',            'Start'),

    -- DevilutionX 1.5.5 / Diablo (Source/diablo.cpp: Keymapper y Padmapper)
    ('/games/devilutionx', 'Mover',              'Clic izquierdo',   'Stick izquierdo'),
    ('/games/devilutionx', 'Mover arriba',       NULL,               'D-Pad arriba'),
    ('/games/devilutionx', 'Mover abajo',        NULL,               'D-Pad abajo'),
    ('/games/devilutionx', 'Mover izquierda',    NULL,               'D-Pad izquierda'),
    ('/games/devilutionx', 'Mover derecha',      NULL,               'D-Pad derecha'),
    ('/games/devilutionx', 'Atacar',             'Clic izquierdo',   'B'),       -- acción primaria
    ('/games/devilutionx', 'Interactuar',        'Clic izquierdo',   'Y'),       -- acción secundaria
    ('/games/devilutionx', 'Habilidad',          'Clic derecho',     'X'),       -- lanzar hechizo
    ('/games/devilutionx', 'Usar objeto',        '1',                'LB'),      -- cinturón / poción de vida
    ('/games/devilutionx', 'Inventario',         'I',                'RT'),
    ('/games/devilutionx', 'Mapa',               'Tab',              'L3'),
    ('/games/devilutionx', 'Pausa',              'Escape',           NULL),      -- mando: Start + Select
    ('/games/devilutionx', 'Cancelar',           'Escape',           'A'),

    -- Dusklight 2.0.2 / Twilight Princess (aurora/lib/dolphin/pad/pad.cpp, perfil estándar;
    -- sin binds de teclado por defecto: se asignan en el menú de controles del juego)
    ('/games/dusklight', 'Mover',                NULL,               'Stick izquierdo'),
    ('/games/dusklight', 'Mirar / Cámara',       NULL,               'Stick derecho'),   -- stick C
    ('/games/dusklight', 'Interactuar',          NULL,               'A'),
    ('/games/dusklight', 'Atacar',               NULL,               'B'),
    ('/games/dusklight', 'Usar objeto',          NULL,               'X'),       -- también Y
    ('/games/dusklight', 'Habilidad',            NULL,               'RB'),      -- GC Z: Midna
    ('/games/dusklight', 'Apuntar',              NULL,               'LT'),      -- GC L: fijar
    ('/games/dusklight', 'Agacharse',            NULL,               'RT'),      -- GC R
    ('/games/dusklight', 'Bloquear',             NULL,               'RT'),
    ('/games/dusklight', 'Inventario',           NULL,               'Start'),

    -- Sparks and Paths / Godot 4.2.1 (InputMap del .pck). Solo mouse y teclado.
    ('/games/sparks-and-paths', 'Sostener linterna', 'Clic izquierdo', NULL),
    ('/games/sparks-and-paths', 'Aturdir espíritus', 'Clic derecho',   NULL),      -- doble clic
    ('/games/sparks-and-paths', 'Silenciar música',  'M',              NULL),

    -- Cursed Ruby / Godot 3.4.3 (InputMap del .pck; botones Godot 3: 0=A 1=B 2=X 3=Y 11=Start 12-15=D-Pad)
    ('/games/cursed-ruby', 'Mover izquierda',    'Flecha izquierda', 'D-Pad izquierda'),
    ('/games/cursed-ruby', 'Mover derecha',      'Flecha derecha',   'D-Pad derecha'),
    ('/games/cursed-ruby', 'Mover arriba',       'Flecha arriba',    'D-Pad arriba'),
    ('/games/cursed-ruby', 'Mover abajo',        'Flecha abajo',     'D-Pad abajo'),
    ('/games/cursed-ruby', 'Saltar',             'Z',                'A'),       -- también avanza diálogos y acepta en el menú
    ('/games/cursed-ruby', 'Atacar',             'X',                'X'),
    ('/games/cursed-ruby', 'Disparar',           'C',                'Y'),       -- lanza (al desbloquearla)
    ('/games/cursed-ruby', 'Esquivar',           'A',                'B'),       -- dash aéreo (al desbloquearlo)
    ('/games/cursed-ruby', 'Pausa',              'Escape',           'Start'),

    -- Iwariwë / Godot 2.1 (engine.cfb del .pck). Sin binds de mando: sus scripts solo leen estas
    -- acciones de teclado. tabulador (Tab) y f2 (F2) no tienen uso claro y no se listan.
    ('/games/iwariwe', 'Mover izquierda',        'Flecha izquierda', NULL),
    ('/games/iwariwe', 'Mover derecha',          'Flecha derecha',   NULL),
    ('/games/iwariwe', 'Saltar',                 'Espacio',          NULL),
    ('/games/iwariwe', 'Interactuar',            'Enter',            NULL),      -- accion
    ('/games/iwariwe', 'Inventario',             'F1',               NULL),      -- items
    ('/games/iwariwe', 'Pausa',                  'P',                NULL),

    -- EVERYDAYNESS;; / Ren'Py 8.5.2 (renpy/common/00keymap.rpy: config.keymap y config.pad_bindings)
    ('/games/everydayness', 'Avanzar texto',     'Clic izquierdo',   'A'),       -- también Enter y Espacio
    ('/games/everydayness', 'Pausa',             'Escape',           'Start'),   -- menú del juego (también clic derecho, B)
    ('/games/everydayness', 'Retroceder',        'Rueda arriba',     'LB'),      -- rollback (también Re Pág)
    ('/games/everydayness', 'Adelantar',         'Rueda abajo',      'RB'),      -- rollforward (también Av Pág)
    ('/games/everydayness', 'Saltar texto',      'Ctrl izquierdo',   NULL),      -- mantener; Tab lo alterna
    ('/games/everydayness', 'Ocultar texto',     'Clic central',     'Y'),       -- también H
    ('/games/everydayness', 'Mover arriba',      'Flecha arriba',    'D-Pad arriba'),
    ('/games/everydayness', 'Mover abajo',       'Flecha abajo',     'D-Pad abajo');

-- Un nombre mal escrito no debe quedar como 0 ("null") en silencio. Sin juegos (initdb corre
-- este archivo antes que seed_input_*) no hay nada que validar.
DO $$
DECLARE
    missing TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM GAME g JOIN seed_game_input s ON s.path = g.path) THEN
        RETURN;
    END IF;
    SELECT string_agg(DISTINCT format('%s=%s', kind, name), ', ') INTO missing
    FROM (
        SELECT 'acción' AS kind, s.action AS name FROM seed_game_input s
            WHERE NOT EXISTS (SELECT 1 FROM ACTION a WHERE a.name = s.action)
        UNION ALL
        SELECT 'tecla', s.keyboard FROM seed_game_input s
            WHERE s.keyboard IS NOT NULL AND NOT EXISTS (SELECT 1 FROM INPUT_KEYBOARD k WHERE k.name = s.keyboard)
        UNION ALL
        SELECT 'botón', s.controller FROM seed_game_input s
            WHERE s.controller IS NOT NULL AND NOT EXISTS (SELECT 1 FROM INPUT_CONTROLLER c WHERE c.name = s.controller)
    ) m;
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'seed_game_inputs: no existen %', missing;
    END IF;
END $$;

INSERT INTO GAME_INPUT (id_game, id_input_keyboard, id_input_controller, id_action)
SELECT id_game, id_keyboard, id_controller, id_action
FROM (
    SELECT g.id AS id_game,
           a.id AS id_action,
           CASE WHEN EXISTS (SELECT 1 FROM GAME_GAMEPAD gg JOIN GAMEPAD gp ON gp.id = gg.id_gamepad
                             WHERE gg.id_game = g.id AND gp.name = 'Teclado y ratón')
                THEN COALESCE(k.id, 0) ELSE 0 END AS id_keyboard,
           CASE WHEN EXISTS (SELECT 1 FROM GAME_GAMEPAD gg JOIN GAMEPAD gp ON gp.id = gg.id_gamepad
                             WHERE gg.id_game = g.id AND gp.name = 'Mando')
                THEN COALESCE(c.id, 0) ELSE 0 END AS id_controller
    FROM seed_game_input s
    JOIN GAME g ON g.path = s.path
    -- Solo juegos sin controles: una vez cargados se editan en game-admin, y deploy-loader.sh
    -- reaplica este archivo en cada despliegue (no tiene que pisar lo editado).
                   AND NOT EXISTS (SELECT 1 FROM GAME_INPUT gi WHERE gi.id_game = g.id)
    JOIN ACTION a ON a.name = s.action
    LEFT JOIN INPUT_KEYBOARD k ON k.name = s.keyboard
    LEFT JOIN INPUT_CONTROLLER c ON c.name = s.controller
) r
WHERE id_keyboard <> 0 OR id_controller <> 0
ON CONFLICT (id_game, id_action) DO NOTHING;

DROP TABLE seed_game_input;
