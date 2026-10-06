package dev.dasan.customdungeons.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JarInspectorTest {
    @TempDir Path directory;
    static byte[] jar(String descriptor) throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("paper-plugin.yml"));
            zip.write(descriptor.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return out.toByteArray();
    }
    @Test void acceptsOnlyExpectedNameAndExactVersion() throws Exception {
        var file = directory.resolve("plugin.jar");
        Files.write(file, jar("name: CustomDungeons\nversion: '1.2.3'\n"));
        JarInspector.verify(file, "1.2.3");
        for (String descriptor : new String[]{"name: Other\nversion: '1.2.3'\n",
                "name: CustomDungeons\nversion: '1.2.2'\n", "name: CustomDungeons\n",
                "name: CustomDungeons\nversion: '1.2.3+other'\n", "name: [invalid\n"}) {
            Files.write(file, jar(descriptor));
            assertThrows(java.io.IOException.class, () -> JarInspector.verify(file, "1.2.3"));
        }
    }
    @Test void rejectsOversizedDescriptorAndNonJar() throws Exception {
        var file = directory.resolve("plugin.jar");
        Files.write(file, jar("#" + "x".repeat(70_000)));
        assertThrows(java.io.IOException.class, () -> JarInspector.verify(file, "1.2.3"));
        Files.writeString(file, "not a zip");
        assertThrows(java.io.IOException.class, () -> JarInspector.verify(file, "1.2.3"));
    }
}
