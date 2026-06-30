// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java.jp;

import com.github.javaparser.ast.modules.ModuleDeclaration;
import com.github.javaparser.ast.modules.ModuleExportsDirective;
import com.github.javaparser.ast.modules.ModuleOpensDirective;
import com.github.javaparser.ast.modules.ModuleProvidesDirective;
import com.github.javaparser.ast.modules.ModuleRequiresDirective;
import com.github.javaparser.ast.modules.ModuleUsesDirective;
import com.github.javaparser.ast.modules.ModuleDirective;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaModuleDirective;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code module-info.java} の {@link ModuleDeclaration} を {@link JavaClassInfo}
 * ({@link JavaClassInfo.Kind#MODULE}) に変換する。
 */
final class ModuleAdapter {

    private ModuleAdapter() {
    }

    static JavaClassInfo adapt(ModuleDeclaration md) {
        JavaClassInfo c = new JavaClassInfo();
        c.setKind(JavaClassInfo.Kind.MODULE);
        c.setSimpleName(md.getNameAsString());
        c.getAnnotations().addAll(JpText.annotations(md));
        if (md.isOpen()) {
            c.getModifiers().add("open");
        }
        for (ModuleDirective d : md.getDirectives()) {
            JavaModuleDirective dir = directive(d);
            if (dir != null) {
                c.getModuleDirectives().add(dir);
            }
        }
        return c;
    }

    private static JavaModuleDirective directive(ModuleDirective d) {
        if (d instanceof ModuleRequiresDirective) {
            ModuleRequiresDirective r = (ModuleRequiresDirective) d;
            List<String> mods = new ArrayList<>();
            r.getModifiers().forEach(m -> mods.add(m.getKeyword().asString()));
            return new JavaModuleDirective(JavaModuleDirective.Kind.REQUIRES,
                    r.getNameAsString(), mods, null);
        }
        if (d instanceof ModuleExportsDirective) {
            ModuleExportsDirective e = (ModuleExportsDirective) d;
            return new JavaModuleDirective(JavaModuleDirective.Kind.EXPORTS,
                    e.getNameAsString(), null, names(e.getModuleNames()));
        }
        if (d instanceof ModuleOpensDirective) {
            ModuleOpensDirective o = (ModuleOpensDirective) d;
            return new JavaModuleDirective(JavaModuleDirective.Kind.OPENS,
                    o.getNameAsString(), null, names(o.getModuleNames()));
        }
        if (d instanceof ModuleUsesDirective) {
            ModuleUsesDirective u = (ModuleUsesDirective) d;
            return new JavaModuleDirective(JavaModuleDirective.Kind.USES,
                    u.getNameAsString(), null, null);
        }
        if (d instanceof ModuleProvidesDirective) {
            ModuleProvidesDirective p = (ModuleProvidesDirective) d;
            return new JavaModuleDirective(JavaModuleDirective.Kind.PROVIDES,
                    p.getNameAsString(), null, names(p.getWith()));
        }
        return null;
    }

    private static List<String> names(Iterable<com.github.javaparser.ast.expr.Name> in) {
        List<String> out = new ArrayList<>();
        for (com.github.javaparser.ast.expr.Name n : in) {
            out.add(n.asString());
        }
        return out;
    }
}
