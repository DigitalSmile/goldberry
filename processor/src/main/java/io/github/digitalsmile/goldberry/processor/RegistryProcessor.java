package io.github.digitalsmile.goldberry.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;

/// Turns `@Bind` and `@Action` into the `Bindings` and `Actions` a person would
/// otherwise have written by hand.
///
/// ## Why a processor and not reflection
///
/// `docs/ARCHITECTURE.md` §9: names are bound "against a controller object
/// explicitly … no reflective `#handler` magic". A runtime scanner would break
/// that rule, need the application's package `opens`, cost startup time, and turn
/// a typo into a control that renders perfectly and never moves.
///
/// This writes ordinary Java instead. The output is a file you can open, step
/// into and get a stack trace out of; a typo is a compile error naming the field;
/// and there is nothing on the runtime path at all
/// ([ADR-0096](../../../../book/src/adr/0096-a-registry-is-generated-not-reflected.md)).
///
/// ## What it refuses
///
/// Every rule below is checked here rather than left to fail at run time, because
/// the whole point of moving the wiring to compile time is that the failures move
/// with it: a `@Bind` on something that is not a `Property`, a duplicate path, an
/// `@Action` taking more than one argument or an argument the toolkit cannot
/// parse.
///
/// ## Private members, and why they are no longer refused
///
/// A `private` field is invisible to generated code in the same package, so the
/// first cut refused one and told the author to widen it. That made a model's
/// encapsulation a consequence of how the toolkit reads it, which is backwards.
///
/// A private member now gets a `VarHandle` or a `MethodHandle`, looked up once in
/// the generated class's static initializer through
/// `MethodHandles.privateLookupIn` — which needs no `opens` and no
/// `setAccessible`, because the generated class is in the target's own package
/// and a module always opens its packages to itself. **The name and the type are
/// still resolved at compile time**: the processor checked the member exists, is
/// a `Property`, and has a parameter it can parse, and it writes the exact
/// descriptor it verified. The handle is *access*, not discovery — nothing scans,
/// and there is still no reflection by name at run time
/// ([ADR-0098](../../../../book/src/adr/0098-a-private-member-is-reached-by-a-handle.md)).
///
/// An accessible member is still read directly, because a handle it does not need
/// is a line of generated code a reader has to understand for nothing.
@SupportedAnnotationTypes({
        "io.github.digitalsmile.goldberry.bind.Registry",
        "io.github.digitalsmile.goldberry.bind.Bind",
        "io.github.digitalsmile.goldberry.bind.Action",
})
public final class RegistryProcessor extends AbstractProcessor {

    private static final String REGISTRY = "io.github.digitalsmile.goldberry.bind.Registry";
    private static final String BIND = "io.github.digitalsmile.goldberry.bind.Bind";
    private static final String ACTION = "io.github.digitalsmile.goldberry.bind.Action";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (var annotated : round.getElementsAnnotatedWith(elementFor(REGISTRY))) {
            if (annotated instanceof TypeElement type) {
                generate(type);
            }
        }
        // Anything annotated outside a @Registry class is a member that will
        // silently never be registered, which is the failure mode this whole
        // change exists to remove.
        for (var name : List.of(BIND, ACTION)) {
            for (var member : round.getElementsAnnotatedWith(elementFor(name))) {
                if (member.getEnclosingElement().getAnnotationMirrors().stream()
                        .noneMatch(m -> m.getAnnotationType().toString().equals(REGISTRY))) {
                    error(member, "@" + simple(name) + " is on a member of "
                            + member.getEnclosingElement().getSimpleName()
                            + ", which is not annotated @Registry — nothing would read it");
                }
            }
        }
        return true;
    }

    private TypeElement elementFor(String name) {
        return processingEnv.getElementUtils().getTypeElement(name);
    }

    private void generate(TypeElement type) {
        var binds = new ArrayList<VariableElement>();
        var actions = new ArrayList<ExecutableElement>();
        var paths = new HashMap<String, Element>();

        for (var member : type.getEnclosedElements()) {
            if (member.getKind() == ElementKind.FIELD && annotation(member, BIND) != null) {
                if (checkBind((VariableElement) member, paths)) {
                    binds.add((VariableElement) member);
                }
            } else if (member.getKind() == ElementKind.METHOD && annotation(member, ACTION) != null) {
                if (checkAction((ExecutableElement) member, paths)) {
                    actions.add((ExecutableElement) member);
                }
            }
        }
        if (binds.isEmpty() && actions.isEmpty()) {
            error(type, "@Registry on " + type.getSimpleName()
                    + " with no @Bind or @Action member — it would generate an empty class");
            return;
        }
        write(type, binds, actions);
    }

    /// A `@Bind` field has to be a `Property`, and it may be private — see the
    /// class comment for how one is reached.
    private boolean checkBind(VariableElement field, Map<String, Element> paths) {
        var ok = true;
        var path = annotationValue(field, BIND);
        var type = processingEnv.getTypeUtils().erasure(field.asType()).toString();
        if (!type.equals("io.github.digitalsmile.goldberry.bind.Property")) {
            error(field, "@Bind field " + field.getSimpleName() + " is a " + type
                    + "; a binding is a Property, because markup reads a value that changes");
            ok = false;
        }
        if (!path.matches("[a-zA-Z_][\\w-]*(\\.[a-zA-Z_][\\w-]*)*")) {
            error(field, "\"" + path + "\" is not a dotted path (ADR-0062)");
            ok = false;
        }
        return duplicate(field, path, paths) && ok;
    }

    /// An `@Action` method takes nothing or one value the toolkit can parse.
    private boolean checkAction(ExecutableElement method, Map<String, Element> paths) {
        var ok = true;
        var path = annotationValue(method, ACTION);
        if (method.getParameters().size() > 1) {
            error(method, "@Action method " + method.getSimpleName() + " takes "
                    + method.getParameters().size() + " arguments; a control reports either"
                    + " *that* something happened or *what* it should become, never both");
            ok = false;
        } else if (method.getParameters().size() == 1
                && parse(method.getParameters().getFirst().asType().toString(), "v") == null) {
            error(method, "@Action method " + method.getSimpleName() + " takes a "
                    + method.getParameters().getFirst().asType()
                    + "; a valued action crosses as a String, so the parameter must be one of"
                    + " String, double, int, boolean (or their boxes)");
            ok = false;
        }
        return duplicate(method, path, paths) && ok;
    }

    private boolean duplicate(Element member, String path, Map<String, Element> paths) {
        var previous = paths.putIfAbsent(path, member);
        if (previous != null) {
            error(member, "\"" + path + "\" is already claimed by " + previous.getSimpleName()
                    + "; two features quietly sharing one name is a bug that presents as a"
                    + " value changing by itself");
            return false;
        }
        return true;
    }

    /// The expression that turns a valued action's `String` into the parameter,
    /// or null when the type is one the toolkit cannot parse.
    private static String parse(String type, String value) {
        return switch (type) {
            case "java.lang.String" -> value;
            case "double", "java.lang.Double" -> "Double.parseDouble(" + value + ")";
            case "int", "java.lang.Integer" -> "Integer.parseInt(" + value + ")";
            case "boolean", "java.lang.Boolean" -> "Boolean.parseBoolean(" + value + ")";
            default -> null;
        };
    }

    private void write(TypeElement type, List<VariableElement> binds,
            List<ExecutableElement> actions) {

        var pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        var target = type.getSimpleName().toString();
        var named = annotationValue(type, REGISTRY);
        var name = named.isEmpty() ? target + "Registry" : named;
        var qualified = pkg.isEmpty() ? name : pkg + "." + name;
        var handles = handleNames(binds, actions);

        try (var out = new PrintWriter(
                processingEnv.getFiler().createSourceFile(qualified, type).openWriter())) {
            if (!pkg.isEmpty()) {
                out.println("package " + pkg + ";");
                out.println();
            }
            out.println("/// Generated from " + target + "'s @Bind and @Action members.");
            out.println("///");
            out.println("/// Do not edit. What it writes is the explicit registration §9 asks");
            out.println("/// for — the annotations move the copying, not the explicitness");
            out.println("/// (ADR-0096).");
            out.println("public final class " + name + " {");

            writeHandles(out, target, binds, actions, handles);

            out.println();
            out.println("    private " + name + "() {");
            out.println("    }");

            if (!binds.isEmpty()) {
                out.println();
                out.println("    /// Every @Bind path on " + target + ", strict.");
                out.println("    public static io.github.digitalsmile.goldberry.bind.Bindings"
                        + " bindings(" + target + " target) {");
                out.println("        return io.github.digitalsmile.goldberry.bind.Bindings.strict()");
                for (var i = 0; i < binds.size(); i++) {
                    var field = binds.get(i);
                    out.print("                .bind(\"" + annotationValue(field, BIND)
                            + "\", " + read(field, handles.get(field)) + ")");
                    out.println(i == binds.size() - 1 ? ";" : "");
                }
                out.println("    }");
            }
            if (!actions.isEmpty()) {
                out.println();
                out.println("    /// Every @Action name on " + target + ", strict.");
                out.println("    public static io.github.digitalsmile.goldberry.widgets.Actions"
                        + " actions(" + target + " target) {");
                out.println("        return io.github.digitalsmile.goldberry.widgets"
                        + ".Actions.strict()");
                for (var i = 0; i < actions.size(); i++) {
                    var method = actions.get(i);
                    out.print("                .bind(\"" + annotationValue(method, ACTION) + "\", "
                            + lambda(method, handles.get(method)) + ")");
                    out.println(i == actions.size() - 1 ? ";" : "");
                }
                out.println("    }");
            }
            if (actions.stream().anyMatch(RegistryProcessor::isPrivate)) {
                writeCallHelper(out);
            }
            out.println("}");
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + qualified, e);
        }
    }

    /// A constant name for every **private** member, and nothing for the rest.
    ///
    /// Derived from the member's own name so the generated code reads as itself,
    /// with an index appended only where two members would collide — two
    /// overloads of one `@Action`, or a field and a method sharing a name.
    private static Map<Element, String> handleNames(List<VariableElement> binds,
            List<ExecutableElement> actions) {

        var names = new HashMap<Element, String>();
        var taken = new HashSet<String>();
        var index = 0;
        for (var member : concat(binds, actions)) {
            index++;
            if (!isPrivate(member)) {
                continue;
            }
            var base = (member.getKind() == ElementKind.FIELD ? "BIND_" : "ACTION_")
                    + screamingCase(member.getSimpleName().toString());
            names.put(member, taken.add(base) ? base : base + "_" + index);
        }
        return names;
    }

    private static List<Element> concat(List<VariableElement> binds,
            List<ExecutableElement> actions) {
        var all = new ArrayList<Element>(binds.size() + actions.size());
        all.addAll(binds);
        all.addAll(actions);
        return all;
    }

    /// `setGain` → `SET_GAIN`, so a constant looks like one.
    private static String screamingCase(String name) {
        var out = new StringBuilder(name.length() + 4);
        for (var i = 0; i < name.length(); i++) {
            var c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && !out.isEmpty()
                    && out.charAt(out.length() - 1) != '_') {
                out.append('_');
            }
            out.append(Character.toUpperCase(c));
        }
        return out.toString();
    }

    /// The handle constants and the one static initializer that fills them.
    ///
    /// `privateLookupIn` needs no `opens` and no `setAccessible`: this class is
    /// generated into the target's own package, and a module always opens its
    /// packages to itself. Every name and descriptor below was verified by the
    /// checks above, so the lookup can only fail if this class and its target
    /// were compiled apart and drifted — the same failure a direct field
    /// reference gives as `NoSuchFieldError`, arriving as an
    /// `ExceptionInInitializerError` instead ([ADR-0098]).
    private void writeHandles(PrintWriter out, String target, List<VariableElement> binds,
            List<ExecutableElement> actions, Map<Element, String> handles) {

        if (handles.isEmpty()) {
            return;
        }
        out.println();
        out.println("    // The private members, reached by handle rather than by name");
        out.println("    // (ADR-0098). Looked up once, here, from descriptors the processor");
        out.println("    // verified at compile time.");
        for (var field : binds) {
            if (isPrivate(field)) {
                out.println("    private static final java.lang.invoke.VarHandle "
                        + handles.get(field) + ";");
            }
        }
        for (var method : actions) {
            if (isPrivate(method)) {
                out.println("    private static final java.lang.invoke.MethodHandle "
                        + handles.get(method) + ";");
            }
        }
        out.println();
        out.println("    static {");
        out.println("        try {");
        out.println("            var lookup = java.lang.invoke.MethodHandles.privateLookupIn(");
        out.println("                    " + target + ".class,"
                + " java.lang.invoke.MethodHandles.lookup());");
        for (var field : binds) {
            if (isPrivate(field)) {
                out.println("            " + handles.get(field) + " = lookup.findVarHandle("
                        + target + ".class, \"" + field.getSimpleName() + "\",");
                out.println("                    io.github.digitalsmile.goldberry.bind"
                        + ".Property.class);");
            }
        }
        for (var method : actions) {
            if (isPrivate(method)) {
                out.println("            " + handles.get(method) + " = lookup.findVirtual("
                        + target + ".class, \"" + method.getSimpleName() + "\",");
                out.println("                    java.lang.invoke.MethodType.methodType("
                        + methodType(method) + "));");
            }
        }
        out.println("        } catch (ReflectiveOperationException e) {");
        out.println("            throw new ExceptionInInitializerError(e);");
        out.println("        }");
        out.println("    }");
    }

    /// The `MethodType` arguments for an `@Action` — its return type, then its
    /// one parameter if it has one.
    private String methodType(ExecutableElement method) {
        var types = new ArrayList<String>(2);
        types.add(classLiteral(method.getReturnType().toString()));
        for (var parameter : method.getParameters()) {
            types.add(classLiteral(
                    processingEnv.getTypeUtils().erasure(parameter.asType()).toString()));
        }
        return String.join(", ", types);
    }

    /// `double` → `double.class`, `java.util.List<T>` → `java.util.List.class`.
    private static String classLiteral(String type) {
        var erased = type.contains("<") ? type.substring(0, type.indexOf('<')) : type;
        return erased + ".class";
    }

    /// How a `@Bind` field is read: straight through, or off its handle.
    private static String read(VariableElement field, String handle) {
        return handle == null
                ? "target." + field.getSimpleName()
                : "(io.github.digitalsmile.goldberry.bind.Property<?>) " + handle + ".get(target)";
    }

    /// A method reference for a bare action, and a parsing lambda for a valued
    /// one — which is the boilerplate every application was writing by hand. A
    /// private action goes through its handle instead, which is the same two
    /// shapes with the call replaced.
    private static String lambda(ExecutableElement method, String handle) {
        var name = method.getSimpleName().toString();
        if (method.getParameters().isEmpty()) {
            return handle == null
                    ? "target::" + name
                    : "() -> call(" + handle + ", target)";
        }
        var type = method.getParameters().getFirst().asType().toString();
        return handle == null
                ? "value -> target." + name + "(" + parse(type, "value") + ")"
                : "value -> call(" + handle + ", target, " + parse(type, "value") + ")";
    }

    /// The one helper a private `@Action` needs, written only when there is one.
    ///
    /// `invokeWithArguments` rather than `invokeExact`, because the shapes differ
    /// per action and the conversion it does — boxing the parsed value back to
    /// the parameter's primitive — is exactly what the generated lambda would
    /// otherwise have to spell out per type. An action runs on a user gesture, so
    /// what it costs is not on any path that matters.
    ///
    /// Unchecked exceptions are rethrown as themselves: an action that throws
    /// `IllegalArgumentException` must reach the application's handler looking
    /// like the one it threw, not like a wrapper.
    private static void writeCallHelper(PrintWriter out) {
        out.println();
        out.println("    /// Calls one of the handles above (ADR-0098).");
        out.println("    private static void call(java.lang.invoke.MethodHandle handle,"
                + " Object... arguments) {");
        out.println("        try {");
        out.println("            handle.invokeWithArguments(arguments);");
        out.println("        } catch (RuntimeException | Error e) {");
        out.println("            throw e;");
        out.println("        } catch (Throwable e) {");
        out.println("            throw new IllegalStateException("
                + "\"the action threw a checked exception\", e);");
        out.println("        }");
        out.println("    }");
    }

    private static boolean isPrivate(Element member) {
        return member.getModifiers().contains(Modifier.PRIVATE);
    }

    private static javax.lang.model.element.AnnotationMirror annotation(Element element,
            String name) {
        return element.getAnnotationMirrors().stream()
                .filter(m -> m.getAnnotationType().toString().equals(name))
                .findFirst()
                .orElse(null);
    }

    private String annotationValue(Element element, String name) {
        var mirror = annotation(element, name);
        if (mirror == null) {
            return "";
        }
        for (var entry : mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals("value")) {
                return String.valueOf(entry.getValue().getValue());
            }
        }
        return "";
    }

    private static String simple(String qualified) {
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    private void error(Element where, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, where);
    }
}
