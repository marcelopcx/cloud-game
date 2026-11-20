-- Tipos de control con los que se puede jugar un título.
INSERT INTO GAMEPAD (name) VALUES
    ('Mando'),
    ('Teclado y ratón'),
    ('Táctil')
ON CONFLICT (name) DO NOTHING;
