// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;

/**
 * 外部 JAR/AAR 内の {@code .class} ファイルから {@link JavaClassInfo} ヘッダを抽出する
 * ASM ベースの読み取りユーティリティ。
 *
 * <p>メソッド本体 (バイトコード) は読まず、クラス宣言・親クラス・実装インタフェース・
 * 公開メソッドのシグネチャ・公開フィールドだけを取り出して
 * {@link JavaClassInfo} を構築する。
 * 出来上がった ClassInfo は {@link JavaClassInfo#getOrigin()} が
 * {@link JavaClassInfo.Origin#EXTERNAL_JAR} となる。</p>
 */
public final class ExternalClassReader {

    private ExternalClassReader() {
    }

    /**
     * 入力ストリームから {@code .class} を読み、{@link JavaClassInfo} ヘッダを構築する。
     *
     * @param in       {@code .class} のバイト列ストリーム (呼び出し側でクローズ)
     * @param jarPath  クラスを含む JAR/AAR のパス (デバッグ表示用)。null 可。
     * @return 構築済み ClassInfo
     * @throws IOException ASM の読み取りに失敗した場合
     */
    public static JavaClassInfo readHeader(InputStream in, String jarPath) throws IOException {
        ClassReader cr = new ClassReader(in);
        HeaderVisitor v = new HeaderVisitor(jarPath);
        cr.accept(v, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return v.getInfo();
    }

    /** ASM ヘッダだけを抜き出して JavaClassInfo に書き込む内部 ClassVisitor。 */
    private static final class HeaderVisitor extends ClassVisitor {
        private final JavaClassInfo info = new JavaClassInfo();
        private final String jarPath;

        HeaderVisitor(String jarPath) {
            super(Opcodes.ASM9);
            this.jarPath = jarPath;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            String binaryName = name == null ? "" : name.replace('/', '.');
            int dot = binaryName.lastIndexOf('.');
            String pkg = dot >= 0 ? binaryName.substring(0, dot) : "";
            String simple = dot >= 0 ? binaryName.substring(dot + 1) : binaryName;
            // ネストクラスは '$' で区切られる。outer$inner → enclosingClass=outer, simple=inner
            int dollar = simple.indexOf('$');
            if (dollar >= 0) {
                info.setEnclosingClass(simple.substring(0, dollar));
                simple = simple.substring(dollar + 1);
            }
            info.setPackageName(pkg);
            info.setSimpleName(simple);
            info.setKind(deriveKind(access));
            collectModifiers(info, access);
            if (superName != null && !"java/lang/Object".equals(superName)) {
                info.setSuperClass(superName.replace('/', '.'));
            }
            if (interfaces != null) {
                for (String iface : interfaces) {
                    if (iface != null) {
                        info.getInterfaces().add(iface.replace('/', '.'));
                    }
                }
            }
            info.setOrigin(JavaClassInfo.Origin.EXTERNAL_JAR);
            info.setJarPath(jarPath);
            // 外部クラスは詳細メタデータを保持しない (バイトコードを読まないため)
            info.setDetailed(false);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                        String signature, Object value) {
            // synthetic / bridge は除外
            if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
                return null;
            }
            JavaFieldInfo f = new JavaFieldInfo();
            f.setName(name);
            f.setType(toJavaType(descriptor));
            f.setVisibility(deriveVisibility(access));
            f.setStatic((access & Opcodes.ACC_STATIC) != 0);
            f.setFinal((access & Opcodes.ACC_FINAL) != 0);
            info.getFields().add(f);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                          String signature, String[] exceptions) {
            if ((access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) {
                return null;
            }
            JavaMethodInfo m = new JavaMethodInfo();
            String methodName = name;
            boolean isCtor = "<init>".equals(name);
            if (isCtor) {
                methodName = info.getSimpleName();
                m.setConstructor(true);
            } else if ("<clinit>".equals(name)) {
                // 静的初期化子は表示しない
                return null;
            }
            m.setName(methodName);
            Type method = Type.getMethodType(descriptor);
            Type[] argTypes = method.getArgumentTypes();
            for (int i = 0; i < argTypes.length; i++) {
                m.getParameterTypes().add(simpleTypeName(argTypes[i]));
                m.getParameterNames().add("arg" + i);
            }
            if (!isCtor) {
                m.setReturnType(simpleTypeName(method.getReturnType()));
            }
            m.setVisibility(deriveVisibility(access));
            m.setStatic((access & Opcodes.ACC_STATIC) != 0);
            m.setAbstract((access & Opcodes.ACC_ABSTRACT) != 0);
            info.getMethods().add(m);
            return null;
        }

        JavaClassInfo getInfo() {
            return info;
        }
    }

    private static JavaClassInfo.Kind deriveKind(int access) {
        if ((access & Opcodes.ACC_ANNOTATION) != 0) {
            return JavaClassInfo.Kind.ANNOTATION;
        }
        if ((access & Opcodes.ACC_ENUM) != 0) {
            return JavaClassInfo.Kind.ENUM;
        }
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            return JavaClassInfo.Kind.INTERFACE;
        }
        return JavaClassInfo.Kind.CLASS;
    }

    private static void collectModifiers(JavaClassInfo info, int access) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            info.getModifiers().add("public");
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0
                && (access & Opcodes.ACC_INTERFACE) == 0) {
            info.getModifiers().add("abstract");
        }
        if ((access & Opcodes.ACC_FINAL) != 0) {
            info.getModifiers().add("final");
        }
        if ((access & Opcodes.ACC_STATIC) != 0) {
            info.getModifiers().add("static");
        }
    }

    private static Visibility deriveVisibility(int access) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            return Visibility.PUBLIC;
        }
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            return Visibility.PROTECTED;
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            return Visibility.PRIVATE;
        }
        return Visibility.PACKAGE;
    }

    private static String toJavaType(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return "";
        }
        return simpleTypeName(Type.getType(descriptor));
    }

    /**
     * ASM の Type をクラス図表示向けの短い型名に変換する。
     * {@code java/lang/String} → {@code String} のように末尾シンプル名に切り詰める。
     */
    private static String simpleTypeName(Type t) {
        if (t == null) {
            return "";
        }
        String s = t.getClassName();
        // 配列なら "..[]" を残しつつシンプル名にする
        int bracket = s.indexOf('[');
        String base = bracket >= 0 ? s.substring(0, bracket) : s;
        String suffix = bracket >= 0 ? s.substring(bracket) : "";
        int dot = base.lastIndexOf('.');
        if (dot >= 0) {
            base = base.substring(dot + 1);
        }
        return base + suffix;
    }
}
