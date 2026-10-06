package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Merges versioned defaults only on enable/reload; YAML diagnostics redact source values. */
public final class ConfigMigration {
    private ConfigMigration() {}
    public record Result(int added, int updated, int preserved, boolean changed) {}

    /** Safe command-facing diagnostic; the cause is intended only for console logging. */
    public static final class MigrationException extends IllegalStateException {
        private final String file;
        private final boolean invalidYaml;

        private MigrationException(String file, Exception cause) {
            super("No se pudo migrar " + file + ": "
                    + (cause instanceof InvalidConfigurationException ? "YAML inválido" : "no se pudo escribir")
                    + "; archivo original conservado.",
                    cause instanceof InvalidConfigurationException ? yamlDiagnostic(cause) : cause);
            this.file = file;
            this.invalidYaml = cause instanceof InvalidConfigurationException;
        }

        public String file() { return file; }
        public String messageKey() {
            return invalidYaml ? "config.migration-invalid-yaml" : "config.migration-write-failed";
        }
    }

    private static Throwable yamlDiagnostic(Throwable error) {
        String detail = error.getClass().getName();
        if (error instanceof org.yaml.snakeyaml.error.MarkedYAMLException marked) {
            var mark = marked.getProblemMark();
            if (mark != null) detail += " (línea " + (mark.getLine() + 1) + ", columna " + (mark.getColumn() + 1) + ")";
        }
        // Parser messages, marks.toString() and source snippets may contain credentials.
        // Keep exception types, positions, full stacks and nested causes without those values.
        var safe = new IllegalStateException(detail,
                error.getCause() == null ? null : yamlDiagnostic(error.getCause()));
        safe.setStackTrace(error.getStackTrace());
        for (Throwable suppressed : error.getSuppressed()) safe.addSuppressed(yamlDiagnostic(suppressed));
        return safe;
    }

    public static void run(CustomDungeonsPlugin plugin) {
        for (String resource : List.of("config.yml", "messages.yml", "messages_en.yml")) {
            try {
                var defaults = resource(resource);
                var history = new ArrayList<YamlConfiguration>();
                boolean messages = !resource.equals("config.yml");
                if (messages) {
                    String stem = resource.substring(0, resource.length() - 4);
                    for (int version = 1; version < defaults.getInt("version"); version++) {
                        history.add(resource("defaults-history/" + stem + "-v" + version + ".yml"));
                    }
                }
                var result = migrate(plugin.getDataFolder().toPath().resolve(resource), defaults, history, messages);
                if (result.changed()) plugin.getLogger().info(resource + ": añadidas=" + result.added()
                        + ", actualizadas=" + result.updated() + ", conservadas=" + result.preserved());
            } catch (IOException | InvalidConfigurationException error) {
                var failure = new MigrationException(resource, error);
                plugin.getLogger().log(java.util.logging.Level.SEVERE, failure.getMessage(), failure);
                throw failure;
            }
        }
    }

    private static YamlConfiguration resource(String name) throws IOException, InvalidConfigurationException {
        try (var stream = ConfigMigration.class.getResourceAsStream("/" + name)) {
            if (stream == null) throw new IOException("Missing bundled defaults");
            var yaml = new YamlConfiguration();
            yaml.options().parseComments(true);
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    static Result merge(YamlConfiguration installed, YamlConfiguration defaults,
                        List<YamlConfiguration> history, boolean updateMessages) {
        int added = 0, updated = 0, preserved = 0;
        int oldVersion = installed.getInt("version", 1), newVersion = defaults.getInt("version");
        boolean upgrade = oldVersion < newVersion;
        for (String key : defaults.getKeys(true)) {
            if (key.equals("version") || defaults.isConfigurationSection(key)) continue;
            Object value = defaults.get(key);
            if (!installed.contains(key, true) && !blockedByScalar(installed, key)) {
                installed.set(key, value);
                installed.setComments(key, defaults.getComments(key));
                installed.setInlineComments(key, defaults.getInlineComments(key));
                added++;
            } else if (!Objects.equals(installed.get(key), value) && updateMessages && upgrade
                    && history.stream().anyMatch(previous -> previous.contains(key, true)
                    && !previous.isConfigurationSection(key) && Objects.equals(previous.get(key), installed.get(key)))) {
                installed.set(key, value);
                updated++;
            } else {
                preserved++;
            }
        }
        // A newer installation is never downgraded; an unversioned file represents v1.
        boolean versionChanged = !installed.contains("version", true) || upgrade;
        if (versionChanged) installed.set("version", Math.max(oldVersion, newVersion));
        return new Result(added, updated, preserved, versionChanged || added > 0 || updated > 0);
    }

    private static boolean blockedByScalar(YamlConfiguration installed, String key) {
        for (int dot = key.indexOf('.'); dot >= 0; dot = key.indexOf('.', dot + 1)) {
            String parent = key.substring(0, dot);
            if (installed.contains(parent, true) && !installed.isConfigurationSection(parent)) return true;
        }
        return false;
    }

    static Result migrate(Path file, YamlConfiguration defaults,
                          List<YamlConfiguration> history, boolean updateMessages)
            throws IOException, InvalidConfigurationException {
        var installed = new YamlConfiguration();
        installed.options().parseComments(true);
        boolean exists = Files.exists(file);
        // Strict loading: malformed files must never be replaced with defaults.
        if (exists) installed.load(file.toFile());
        else installed.options().setHeader(defaults.options().getHeader());
        var result = merge(installed, defaults, history, updateMessages);
        if (!result.changed()) return result;
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".migration-", ".yml");
        try {
            Files.writeString(temporary, installed.saveToString(), StandardCharsets.UTF_8);
            if (exists) {
                String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
                // createTempFile prevents collisions on repeated migrations in the same millisecond.
                Path backup = Files.createTempFile(file.getParent(), file.getFileName() + ".bak-" + date + "-", "");
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return result;
    }
}
