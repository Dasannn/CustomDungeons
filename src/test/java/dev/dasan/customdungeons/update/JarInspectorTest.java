package dev.dasan.customdungeons.update;

import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JarInspectorTest {
    static byte[] jar(String descriptor) throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("paper-plugin.yml"));
            zip.write(descriptor.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return out.toByteArray();
    }
    @Test void acceptsOnlyExpectedNameAndExactVersion() throws Exception {
        JarInspector.verify(jar("name: CustomDungeons\nversion: '1.2.3'\n"), "1.2.3");
        for (String descriptor : new String[]{"name: Other\nversion: '1.2.3'\n",
                "name: CustomDungeons\nversion: '1.2.2'\n", "name: CustomDungeons\n",
                "name: CustomDungeons\nversion: '1.2.3+other'\n", "name: [invalid\n"}) {
            byte[] invalid = jar(descriptor);
            assertThrows(java.io.IOException.class, () -> JarInspector.verify(invalid, "1.2.3"));
        }
    }
    @Test void rejectsOversizedDescriptorAndNonJar() throws Exception {
        byte[] oversized = jar("#" + "x".repeat(70_000));
        assertThrows(java.io.IOException.class, () -> JarInspector.verify(oversized, "1.2.3"));
        assertThrows(java.io.IOException.class, () -> JarInspector.verify("not a zip".getBytes(StandardCharsets.UTF_8), "1.2.3"));
    }
    @Test void rejectsMissingAndDuplicateDescriptors() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String name : new String[]{"paper-plugin.yml", "paper-plugio.yml"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("name: CustomDungeons\nversion: '1.2.3'\n".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        // Equal-length entry-name replacement keeps offsets/CRC valid while creating duplicate entries.
        byte[] duplicate = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
                .replace("paper-plugio.yml", "paper-plugin.yml").getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(java.io.IOException.class, () -> JarInspector.verify(duplicate, "1.2.3"));
        byte[] missing = new String(jar("name: CustomDungeons\nversion: '1.2.3'\n"), StandardCharsets.ISO_8859_1)
                .replace("paper-plugin.yml", "other-plugin.yml").getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(java.io.IOException.class, () -> JarInspector.verify(missing, "1.2.3"));
    }
}
