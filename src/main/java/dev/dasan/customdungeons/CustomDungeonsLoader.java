package dev.dasan.customdungeons;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

public final class CustomDungeonsLoader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        for (String coordinate : new String[] {
                "com.zaxxer:HikariCP:7.1.0", "org.xerial:sqlite-jdbc:3.53.4.0",
                "com.mysql:mysql-connector-j:26.7.0"}) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinate), null));
        }
        classpathBuilder.addLibrary(resolver);
    }
}
