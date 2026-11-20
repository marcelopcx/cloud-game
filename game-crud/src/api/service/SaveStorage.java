package api.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/** Dónde viven los archivos de save. Hoy disco local ({@link DiskSaveStorage}). */
public interface SaveStorage {

    /** El archivo del save, si existe. */
    Optional<Path> find(int userId, String gameId);

    /**
     * Reemplaza el save de forma atómica: si se corta a mitad, queda el anterior.
     *
     * @return bytes escritos
     * @throws SaveTooLargeException si supera {@code maxBytes} (el save anterior no se toca)
     */
    long write(int userId, String gameId, InputStream body, long maxBytes) throws IOException;

    class SaveTooLargeException extends IOException {
        public SaveTooLargeException(long maxBytes) {
            super("el save supera " + maxBytes + " bytes");
        }
    }
}
