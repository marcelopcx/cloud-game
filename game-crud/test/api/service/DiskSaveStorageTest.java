package api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskSaveStorageTest {

    @TempDir
    Path root;

    @Test
    void writesReplacesAndKeepsTheOldOneWhenTooLarge() throws Exception {
        DiskSaveStorage storage = new DiskSaveStorage(root.toString());
        assertTrue(storage.find(1, "stk").isEmpty());

        assertEquals(3, storage.write(1, "stk", new ByteArrayInputStream(new byte[]{1, 2, 3}), 10));
        assertEquals(3, Files.size(storage.find(1, "stk").orElseThrow()));

        assertThrows(SaveStorage.SaveTooLargeException.class,
                () -> storage.write(1, "stk", new ByteArrayInputStream(new byte[20]), 10));
        assertEquals(3, Files.size(storage.find(1, "stk").orElseThrow()));
        try (var left = Files.list(root.resolve("1"))) {
            assertEquals(1, left.count());
        }
    }
}
