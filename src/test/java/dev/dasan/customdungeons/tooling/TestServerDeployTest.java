package dev.dasan.customdungeons.tooling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TestServerDeployTest {
    @TempDir Path directory;
    @Test void deploySelectsNewestProductJarAndIgnoresSourcesAndJavadoc() throws Exception {
        Path project = directory.resolve("project with spaces");
        Files.createDirectories(project.resolve("scripts")); Files.createDirectories(project.resolve("build/libs"));
        Path server = directory.resolve("server");
        Files.writeString(project.resolve("scripts/test-server.sh"), Files.readString(Path.of("scripts/test-server.sh"))
                .replace("SERVER=\"$HOME/Desktop/Proyectos/plugins/servidor/Servidor-agentes\"", "SERVER=\""+server+"\""));
        executable(project.resolve("gradlew"),"#!/bin/sh\nexit 0\n");
        Path bin = directory.resolve("bin"); Files.createDirectories(bin);
        executable(bin.resolve("pgrep"),"#!/bin/sh\nexit 1\n");
        Path plugins = server.resolve("plugins"); Files.createDirectories(plugins);
        String[] jars = {"CustomDungeons-1.0.0-SNAPSHOT.jar", "CustomDungeons-1.0.1.jar",
                "CustomDungeons-1.0.1-sources.jar", "CustomDungeons-1.0.1-javadoc.jar"};
        for (int i=0;i<jars.length;i++) {
            Path jar = project.resolve("build/libs").resolve(jars[i]);
            Files.writeString(jar,jars[i]); Files.setLastModifiedTime(jar,FileTime.fromMillis(1000L*(i+1)));
        }
        var builder = new ProcessBuilder("bash",project.resolve("scripts/test-server.sh").toString(),"deploy");
        builder.environment().put("PATH",bin+":"+System.getenv("PATH"));
        builder.redirectErrorStream(true);
        var process = builder.start();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertEquals(0,process.waitFor()));
        assertEquals("CustomDungeons-1.0.1.jar",Files.readString(plugins.resolve("CustomDungeons.jar")));
    }
    void executable(Path path,String content) throws Exception {
        Files.writeString(path,content); assertTrue(path.toFile().setExecutable(true));
    }
}
