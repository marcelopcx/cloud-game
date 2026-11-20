-- Teclas del teclado. El id 0 es "null" (sin tecla asignada).
INSERT INTO INPUT_KEYBOARD (id, name) OVERRIDING SYSTEM VALUE VALUES (0, 'null')
ON CONFLICT (id) DO NOTHING;

INSERT INTO INPUT_KEYBOARD (name) VALUES
    ('A'), ('B'), ('C'), ('D'), ('E'), ('F'), ('G'), ('H'), ('I'), ('J'), ('K'), ('L'), ('M'),
    ('N'), ('O'), ('P'), ('Q'), ('R'), ('S'), ('T'), ('U'), ('V'), ('W'), ('X'), ('Y'), ('Z'),
    ('0'), ('1'), ('2'), ('3'), ('4'), ('5'), ('6'), ('7'), ('8'), ('9'),
    ('F1'), ('F2'), ('F3'), ('F4'), ('F5'), ('F6'), ('F7'), ('F8'), ('F9'), ('F10'), ('F11'), ('F12'),
    ('Espacio'), ('Enter'), ('Escape'), ('Tab'), ('Retroceso'), ('Bloq Mayús'),
    ('Shift izquierdo'), ('Shift derecho'), ('Ctrl izquierdo'), ('Ctrl derecho'),
    ('Alt izquierdo'), ('Alt derecho'),
    ('Flecha arriba'), ('Flecha abajo'), ('Flecha izquierda'), ('Flecha derecha'),
    ('Insertar'), ('Suprimir'), ('Inicio'), ('Fin'), ('Re Pág'), ('Av Pág'),
    ('Numpad 0'), ('Numpad 1'), ('Numpad 2'), ('Numpad 3'), ('Numpad 4'),
    ('Numpad 5'), ('Numpad 6'), ('Numpad 7'), ('Numpad 8'), ('Numpad 9'),
    ('Clic izquierdo'), ('Clic derecho'), ('Clic central'),
    ('Rueda arriba'), ('Rueda abajo'), ('Botón lateral 1'), ('Botón lateral 2'),
    ('Movimiento del ratón')
ON CONFLICT (name) DO NOTHING;
