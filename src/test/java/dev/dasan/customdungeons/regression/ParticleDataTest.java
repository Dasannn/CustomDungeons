package dev.dasan.customdungeons.regression;

import com.sun.source.tree.*;
import com.sun.source.util.TreePathScanner;
import java.util.*;
import javax.lang.model.element.*;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParticleDataTest {
    @Test void allParticleEmissionsSupplyTheDataTypeRequiredByPaper() throws Exception {
        var failures = new ArrayList<String>();
        var checked = new HashSet<Particle>();
        try (var source = new SourceChecks()) {
            for (var unit : source.units) new TreePathScanner<Void, Void>() {
                @Override public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                    var symbol = source.trees.getElement(getCurrentPath());
                    if (symbol instanceof ExecutableElement method) {
                        String name = method.getSimpleName().toString();
                        var args = call.getArguments();
                        boolean emission = name.equals("spawnParticle") || name.equals("setParticle");
                        for (int i = 0; i < args.size(); i++) {
                            var argSymbol = source.trees.getElement(new com.sun.source.util.TreePath(getCurrentPath(), args.get(i)));
                            if (!(argSymbol instanceof VariableElement field)
                                    || field.getKind() != ElementKind.ENUM_CONSTANT
                                    || !field.getEnclosingElement().toString().equals("org.bukkit.Particle")) continue;
                            var particle = Particle.valueOf(field.getSimpleName().toString());
                            checked.add(particle);
                            if (particle.getDataType() == Void.class) continue;
                            int dataIndex = -1;
                            if (emission) {
                                var params = method.getParameters();
                                for (int j = 0; j < params.size(); j++)
                                    if (params.get(j).asType().getKind() == javax.lang.model.type.TypeKind.TYPEVAR) dataIndex = j;
                            }
                            String at = source.location(getCurrentPath()) + " " + particle;
                            if (dataIndex < 0) failures.add(at + " missing " + particle.getDataType().getName());
                            else {
                                var expected = source.task.getElements().getTypeElement(particle.getDataType().getCanonicalName()).asType();
                                if (!source.types.isAssignable(source.type(getCurrentPath(), args.get(dataIndex)), expected))
                                    failures.add(at + " wrong data: " + args.get(dataIndex));
                            }
                        }
                    }
                    return super.visitMethodInvocation(call, unused);
                }
            }.scan(unit, null);
        }
        assertTrue(checked.containsAll(Set.of(Particle.DRAGON_BREATH, Particle.DUST, Particle.BLOCK)));
        assertEquals(List.of(), failures);
    }
}
