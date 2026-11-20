package com.cloudgame.station.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ida y vuelta de un save: lo que sube un jugador vuelve igual en otra estación (otro root),
 * sin payloads, logs ni {@code savesExclude}. Y un archivo con rutas ajenas se rechaza.
 */
class SaveArchiveTest {

    @TempDir
    Path tmp;

    private SaveArchive archive(Path root) {
        return new SaveArchive(root, root.resolve("cache"), root.resolve("root"),
                List.of(root.resolve("opt/zelda3/saves")), List.of(root.resolve("cache/dusk/pipelines")));
    }

    @Test
    void roundTripKeepsProgressAndSkipsPayloadLogsAndExcludes() throws Exception {
        Path a = Files.createDirectories(tmp.resolve("a"));
        write(a, "cache/soh-ship/save.sav", "progreso");
        write(a, "cache/stk/1.0.0/payload.bin", "payload");
        write(a, "cache/stk/1.0.0/checksum", "sha256:00");
        write(a, "cache/stk.log", "log");
        write(a, "cache/dusk/pipelines/x.bin", "cache grande");
        write(a, "root/.local/share/stk/challenges.xml", "desbloqueado");
        write(a, "opt/zelda3/saves/sram.srm", "zelda");
        Path out = tmp.resolve("save.tar.gz");
        archive(a).pack(out);

        Path b = Files.createDirectories(tmp.resolve("b"));
        archive(b).unpack(out);

        assertEquals("progreso", Files.readString(b.resolve("cache/soh-ship/save.sav")));
        assertEquals("desbloqueado", Files.readString(b.resolve("root/.local/share/stk/challenges.xml")));
        assertEquals("zelda", Files.readString(b.resolve("opt/zelda3/saves/sram.srm")));
        assertFalse(Files.exists(b.resolve("cache/stk/1.0.0")));
        assertFalse(Files.exists(b.resolve("cache/stk.log")));
        assertFalse(Files.exists(b.resolve("cache/dusk/pipelines")));
    }

    @Test
    void emptyAreasStillPackAValidArchive() throws Exception {
        Path a = Files.createDirectories(tmp.resolve("a"));
        Path out = tmp.resolve("save.tar.gz");
        archive(a).pack(out);
        archive(Files.createDirectories(tmp.resolve("b"))).unpack(out);
    }

    @Test
    void rejectsMembersOutsideTheSaveAreas() throws Exception {
        Path evil = Files.createDirectories(tmp.resolve("evil"));
        write(evil, "etc/passwd", "x");
        Path out = tmp.resolve("evil.tar.gz");
        new ProcessBuilder("tar", "-czf", out.toString(), "-C", evil.toString(), "etc").start().waitFor();

        Path b = Files.createDirectories(tmp.resolve("b"));
        assertThrows(IOException.class, () -> archive(b).unpack(out));
        assertFalse(Files.exists(b.resolve("etc")));
    }

    @Test
    void playerSavesRestoresAndUploadsWithTheGrant() throws Exception {
        Path root = Files.createDirectories(tmp.resolve("st"));
        write(root, "cache/soh-ship/save.sav", "v1");
        Path stored = tmp.resolve("stored.tar.gz");
        archive(root).pack(stored);
        Files.delete(root.resolve("cache/soh-ship/save.sav"));

        FakeStore store = new FakeStore(stored);
        PlayerSaves saves = new PlayerSaves(archive(root), store, true, tmp.resolve("unsent"));

        assertFalse(saves.restore("soh", null));
        assertTrue(saves.restore("soh", "grant-1"));
        assertEquals("v1", Files.readString(root.resolve("cache/soh-ship/save.sav")));

        write(root, "cache/soh-ship/save.sav", "v2");
        saves.persist("soh", "grant-1");
        assertEquals("grant-1", store.grant);
        Path back = Files.createDirectories(tmp.resolve("back"));
        archive(back).unpack(store.uploaded);
        assertEquals("v2", Files.readString(back.resolve("cache/soh-ship/save.sav")));
    }

    @Test
    void failedUploadKeepsTheArchiveAside() throws Exception {
        Path root = Files.createDirectories(tmp.resolve("st"));
        write(root, "cache/soh-ship/save.sav", "v2");
        FakeStore store = new FakeStore(null);
        store.failUploads = true;
        new PlayerSaves(archive(root), store, true, tmp.resolve("unsent")).persist("soh", "grant-1");
        try (var kept = Files.list(tmp.resolve("unsent"))) {
            assertEquals(1, kept.count());
        }
    }

    private static void write(Path root, String rel, String content) throws IOException {
        Path file = root.resolve(rel);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** game-crud en memoria. */
    private final class FakeStore extends SaveStore {
        private final Path existing;
        Path uploaded;
        String grant;
        boolean failUploads;

        FakeStore(Path existing) {
            super(new com.cloudgame.station.config.StationProperties());
            this.existing = existing;
        }

        @Override
        public Optional<Path> download(String gameId, String grant, Path dest) throws IOException {
            if (existing == null) {
                return Optional.empty();
            }
            Files.copy(existing, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(dest);
        }

        @Override
        public void upload(String gameId, String grant, Path archive) throws IOException {
            if (failUploads) {
                throw new IOException("crud caído");
            }
            this.grant = grant;
            uploaded = tmp.resolve("uploaded.tar.gz");
            Files.copy(archive, uploaded, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
