package dev.dasan.customdungeons.update;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reads only a bounded descriptor, after signature verification; never loads classes. */
public final class JarInspector {
    private static final int MAX_DESCRIPTOR_BYTES = 64 * 1024;
    private static final long MAX_EXPANDED_BYTES = 128L * 1024 * 1024;
    private JarInspector() {}
    public static void verify(byte[] jar, String version) throws IOException {
        byte[] descriptor = null;
        long expanded = 0;
        byte[] buffer = new byte[8192];
        try (var zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("paper-plugin.yml")) {
                    if (descriptor != null || entry.isDirectory() || entry.getSize() > MAX_DESCRIPTOR_BYTES)
                        throw new IOException("Duplicate or invalid descriptor");
                    descriptor = zip.readNBytes(MAX_DESCRIPTOR_BYTES + 1);
                    if (descriptor.length > MAX_DESCRIPTOR_BYTES) throw new IOException("Descriptor too large");
                }
                // ZipInputStream drains entries to find duplicates. Bound total expansion too.
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > MAX_EXPANDED_BYTES) throw new IOException("Jar expansion too large");
                }
                zip.closeEntry();
            }
        }
        if (descriptor == null) throw new IOException("Missing descriptor");
        var yaml = new YamlConfiguration();
        try { yaml.loadFromString(new String(descriptor, StandardCharsets.UTF_8)); }
        catch (org.bukkit.configuration.InvalidConfigurationException | RuntimeException error) {
            throw new IOException("Invalid descriptor", error);
        }
        if (!"CustomDungeons".equals(yaml.get("name")) || !version.equals(yaml.get("version")))
            throw new IOException("Unexpected plugin name or version");
    }
}
