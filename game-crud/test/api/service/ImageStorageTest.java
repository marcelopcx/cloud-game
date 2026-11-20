package api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import api.exception.ApiException;
import api.model.ImageKind;

class ImageStorageTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 9};

    @TempDir
    Path root;

    @Test
    void storesByContentHashAndDeletesOnlyInsideTheRoot() throws Exception {
        ImageStorage storage = new ImageStorage(root.toString(), 1, new ImageOptimizer(false, "convert"));

        String poster = storage.store(7, ImageKind.POSTER, PNG);
        assertTrue(poster.matches("/media/games/7/poster-[0-9a-f]{16}\\.png"), poster);
        assertTrue(Files.isRegularFile(root.resolve(poster.substring("/media/".length()))));
        assertEquals(poster, storage.store(7, ImageKind.POSTER, PNG));
        assertNotEquals(poster, storage.store(7, ImageKind.POSTER, JPG));

        storage.delete(poster);
        assertFalse(Files.exists(root.resolve(poster.substring("/media/".length()))));
        storage.delete("https://example.com/x.png");
        storage.delete("/media/../outside.png");

        storage.store(7, ImageKind.ICON, WEBP);
        storage.deleteGame(7);
        assertFalse(Files.exists(root.resolve("games/7")));
    }

    @Test
    void detectsFormatFromContentAndEnforcesTheLimit() throws Exception {
        assertEquals("png", ImageStorage.extension(PNG));
        assertEquals("jpg", ImageStorage.extension(JPG));
        assertEquals("webp", ImageStorage.extension(WEBP));
        assertEquals("gif", ImageStorage.extension("GIF89a...".getBytes()));
        assertEquals("ico", ImageStorage.extension(new byte[] {0, 0, 1, 0, 1, 0}));
        ApiException svg = assertThrows(ApiException.class, () -> ImageStorage.extension("<svg/>".getBytes()));
        assertEquals(415, svg.status());

        ImageStorage storage = new ImageStorage(root.toString(), 1, new ImageOptimizer(false, "convert"));
        ApiException big = assertThrows(ApiException.class,
                () -> storage.read(new ByteArrayInputStream(new byte[1024 * 1024 + 1])));
        assertEquals(413, big.status());
        assertThrows(ApiException.class, () -> storage.read(new ByteArrayInputStream(new byte[0])));
    }
}
