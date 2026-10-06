package dev.dasan.customdungeons.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reads only a bounded descriptor, after signature verification; never loads classes. */
public final class JarInspector {
    private static final int MAX_DESCRIPTOR_BYTES = 64 * 1024;
    private JarInspector() {}
    public static void verify(Path jar, String version) throws IOException {
        try (var zip = new ZipFile(jar.toFile())) {
            if (zip.stream().filter(entry -> entry.getName().equals("paper-plugin.yml")).count() != 1)
                throw new IOException("Missing or duplicate descriptor");
            var descriptor = zip.getEntry("paper-plugin.yml");
            if (descriptor.isDirectory() || descriptor.getSize() > MAX_DESCRIPTOR_BYTES) throw new IOException("Invalid descriptor size");
            byte[] bytes;
            try (var stream = zip.getInputStream(descriptor)) { bytes = stream.readNBytes(MAX_DESCRIPTOR_BYTES + 1); }
            if (bytes.length > MAX_DESCRIPTOR_BYTES) throw new IOException("Descriptor too large");
            var yaml = new YamlConfiguration();
            try { yaml.loadFromString(new String(bytes, StandardCharsets.UTF_8)); }
            catch (org.bukkit.configuration.InvalidConfigurationException | RuntimeException error) {
                throw new IOException("Invalid descriptor", error);
            }
            if (!"CustomDungeons".equals(yaml.get("name")) || !version.equals(yaml.get("version")))
                throw new IOException("Unexpected plugin name or version");
        }
    }
}
