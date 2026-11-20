-- Botones del mando (nomenclatura Xbox). El id 0 es "null" (sin botón asignado).
INSERT INTO INPUT_CONTROLLER (id, name) OVERRIDING SYSTEM VALUE VALUES (0, 'null')
ON CONFLICT (id) DO NOTHING;

INSERT INTO INPUT_CONTROLLER (name) VALUES
    ('A'), ('B'), ('X'), ('Y'),
    ('LB'), ('RB'), ('LT'), ('RT'),
    ('L3'), ('R3'),
    ('Start'), ('Select'),
    ('D-Pad arriba'), ('D-Pad abajo'), ('D-Pad izquierda'), ('D-Pad derecha'),
    ('Stick izquierdo'), ('Stick derecho')
ON CONFLICT (name) DO NOTHING;
