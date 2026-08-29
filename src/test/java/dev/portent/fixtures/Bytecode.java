package dev.portent.fixtures;

import java.util.List;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Synthesises class files with ASM so that tests never need a checked-in binary or a download.
 *
 * <p>The generated classes are only ever <em>parsed</em>, so bodies just need to be structurally
 * valid — a null receiver is enough to reference any member.
 */
public final class Bytecode {

    private static final int V = Opcodes.V21;

    private Bytecode() {}

    /** A member of a synthesised API type. */
    public record Member(
            boolean method, String name, String descriptor, List<Annotation> annotations) {

        public static Member method(String name, String descriptor, Annotation... annotations) {
            return new Member(true, name, descriptor, List.of(annotations));
        }

        public static Member field(String name, String descriptor, Annotation... annotations) {
            return new Member(false, name, descriptor, List.of(annotations));
        }
    }

    /** An annotation to stamp on a synthesised member. */
    public record Annotation(String descriptor, boolean visible, String key, Object value) {

        public static Annotation deprecated() {
            return new Annotation("Ljava/lang/Deprecated;", true, null, null);
        }

        public static Annotation deprecatedForRemoval() {
            return new Annotation("Ljava/lang/Deprecated;", true, "forRemoval", Boolean.TRUE);
        }

        public static Annotation apiStatusInternal() {
            return new Annotation(
                    "Lorg/jetbrains/annotations/ApiStatus$Internal;", false, null, null);
        }

        public static Annotation apiStatusExperimental() {
            return new Annotation(
                    "Lorg/jetbrains/annotations/ApiStatus$Experimental;", false, null, null);
        }
    }

    /** One reference a synthesised plugin class makes. */
    public record Reference(int opcode, String owner, String name, String descriptor) {

        public static Reference callInterface(String owner, String name, String descriptor) {
            return new Reference(Opcodes.INVOKEINTERFACE, owner, name, descriptor);
        }

        public static Reference callVirtual(String owner, String name, String descriptor) {
            return new Reference(Opcodes.INVOKEVIRTUAL, owner, name, descriptor);
        }

        public static Reference callStatic(String owner, String name, String descriptor) {
            return new Reference(Opcodes.INVOKESTATIC, owner, name, descriptor);
        }

        public static Reference readStaticField(String owner, String name, String descriptor) {
            return new Reference(Opcodes.GETSTATIC, owner, name, descriptor);
        }

        public static Reference readField(String owner, String name, String descriptor) {
            return new Reference(Opcodes.GETFIELD, owner, name, descriptor);
        }

        boolean isField() {
            return opcode == Opcodes.GETSTATIC
                    || opcode == Opcodes.PUTSTATIC
                    || opcode == Opcodes.GETFIELD
                    || opcode == Opcodes.PUTFIELD;
        }

        boolean needsReceiver() {
            return opcode != Opcodes.INVOKESTATIC && opcode != Opcodes.GETSTATIC;
        }
    }

    /** An interface in the target API. */
    public static byte[] apiInterface(String internalName, List<String> superInterfaces, Member... members) {
        return apiType(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                internalName,
                "java/lang/Object",
                superInterfaces,
                members);
    }

    /** A class in the target API. */
    public static byte[] apiClass(
            String internalName, String superName, List<String> interfaces, Member... members) {
        return apiType(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                internalName,
                superName == null ? "java/lang/Object" : superName,
                interfaces,
                members);
    }

    private static byte[] apiType(
            int access,
            String internalName,
            String superName,
            List<String> interfaces,
            Member... members) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                V,
                access,
                internalName,
                null,
                superName,
                interfaces == null ? null : interfaces.toArray(String[]::new));

        for (Member member : members) {
            if (member.method()) {
                int memberAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
                MethodVisitor mv =
                        writer.visitMethod(memberAccess, member.name(), member.descriptor(), null, null);
                for (Annotation annotation : member.annotations()) {
                    stamp(mv.visitAnnotation(annotation.descriptor(), annotation.visible()), annotation);
                }
                mv.visitEnd();
            } else {
                var fv =
                        writer.visitField(
                                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                                member.name(),
                                member.descriptor(),
                                null,
                                null);
                for (Annotation annotation : member.annotations()) {
                    stamp(fv.visitAnnotation(annotation.descriptor(), annotation.visible()), annotation);
                }
                fv.visitEnd();
            }
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void stamp(AnnotationVisitor av, Annotation annotation) {
        if (annotation.key() != null) {
            av.visit(annotation.key(), annotation.value());
        }
        av.visitEnd();
    }

    /**
     * A plugin class with a single {@code run()V} method that makes every given reference. The
     * calling method is what the report names as evidence.
     */
    public static byte[] pluginClass(String internalName, Reference... references) {
        return pluginClass(internalName, "run", "()V", references);
    }

    public static byte[] pluginClass(
            String internalName, String methodName, String methodDescriptor, Reference... references) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);

        MethodVisitor mv =
                writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, methodDescriptor, null, null);
        mv.visitCode();
        for (Reference reference : references) {
            emit(mv, reference);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A plugin class whose {@code run()V} body loads the given string constants. */
    public static byte[] pluginClassWithConstants(String internalName, String... constants) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode();
        for (String constant : constants) {
            mv.visitLdcInsn(constant);
            mv.visitInsn(Opcodes.POP);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A plugin class compiled for a specific class file major version. */
    public static byte[] pluginClassTargeting(String internalName, int classFileMajor) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(classFileMajor, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void emit(MethodVisitor mv, Reference reference) {
        if (reference.needsReceiver()) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        }
        if (reference.isField()) {
            mv.visitFieldInsn(
                    reference.opcode(), reference.owner(), reference.name(), reference.descriptor());
            pop(mv, Type.getType(reference.descriptor()));
            return;
        }
        for (Type argument : Type.getArgumentTypes(reference.descriptor())) {
            push(mv, argument);
        }
        mv.visitMethodInsn(
                reference.opcode(),
                reference.owner(),
                reference.name(),
                reference.descriptor(),
                reference.opcode() == Opcodes.INVOKEINTERFACE);
        pop(mv, Type.getReturnType(reference.descriptor()));
    }

    /**
     * A class whose {@code run()V} body captures a method reference (an invokedynamic whose target
     * lives in a bootstrap argument), the way {@code list.forEach(Player::sendMessage)} compiles.
     */
    public static byte[] pluginClassWithMethodReference(
            String internalName, String owner, String name, String descriptor) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode();
        Handle bootstrap =
                new Handle(
                        Opcodes.H_INVOKESTATIC,
                        "java/lang/invoke/LambdaMetafactory",
                        "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                                + "Ljava/lang/invoke/CallSite;",
                        false);
        Handle target = new Handle(Opcodes.H_INVOKEINTERFACE, owner, name, descriptor, true);
        mv.visitInvokeDynamicInsn(
                "accept",
                "()Ljava/util/function/Consumer;",
                bootstrap,
                Type.getType("(Ljava/lang/Object;)V"),
                target,
                Type.getType("(L" + owner + ";)V"));
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void push(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.LONG -> mv.visitInsn(Opcodes.LCONST_0);
            case Type.FLOAT -> mv.visitInsn(Opcodes.FCONST_0);
            case Type.DOUBLE -> mv.visitInsn(Opcodes.DCONST_0);
            case Type.OBJECT, Type.ARRAY -> mv.visitInsn(Opcodes.ACONST_NULL);
            default -> mv.visitInsn(Opcodes.ICONST_0);
        }
    }

    private static void pop(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.VOID -> {}
            case Type.LONG, Type.DOUBLE -> mv.visitInsn(Opcodes.POP2);
            default -> mv.visitInsn(Opcodes.POP);
        }
    }
}
