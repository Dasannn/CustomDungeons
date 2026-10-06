package dev.dasan.customdungeons.regression;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Types;
import javax.tools.*;

/** Uses the JDK parser and symbol resolution, so comments and unrelated get/send methods are ignored. */
final class SourceChecks implements AutoCloseable {
    final StandardJavaFileManager files;
    final JavacTask task;
    final Trees trees;
    final Types types;
    final List<CompilationUnitTree> units = new ArrayList<>();

    SourceChecks() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        files = compiler.getStandardFileManager(null, null, null);
        List<Path> sources;
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            sources = paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        task = (JavacTask) compiler.getTask(null, files, diagnostics,
                List.of("-proc:none", "-classpath", System.getProperty("sourceCheckClasspath")),
                null, files.getJavaFileObjectsFromPaths(sources));
        task.parse().forEach(units::add);
        task.analyze();
        var errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList();
        if (!errors.isEmpty()) throw new AssertionError(errors.toString());
        trees = Trees.instance(task);
        types = task.getTypes();
    }
    TypeMirror type(TreePath parent, Tree node) {
        var type = trees.getTypeMirror(new TreePath(parent, node));
        return type.getKind().isPrimitive() ? types.boxedClass((PrimitiveType) type).asType() : type;
    }
    String location(TreePath path) {
        var unit = path.getCompilationUnit();
        return unit.getSourceFile().getName() + ":" + unit.getLineMap().getLineNumber(
                trees.getSourcePositions().getStartPosition(unit, path.getLeaf()));
    }
    @Override public void close() throws Exception { files.close(); }
}
