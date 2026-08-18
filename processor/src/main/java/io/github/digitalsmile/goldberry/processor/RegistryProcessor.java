package io.github.digitalsmile.goldberry.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
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
/// with it: a `private` member the generated code cannot see, a `@Bind` on
/// something that is not a `Property`, a duplicate path, an `@Action` taking more
/// than one argument or an argument the toolkit cannot parse.
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

    /// A `@Bind` field has to be a `Property` the generated code can see.
    private boolean checkBind(VariableElement field, Map<String, Element> paths) {
        var ok = true;
        var path = annotationValue(field, BIND);
        if (field.getModifiers().contains(Modifier.PRIVATE)) {
            error(field, "@Bind field " + field.getSimpleName() + " is private; the generated"
                    + " registry is in the same package and cannot see it. Package-private is"
                    + " enough — the accessors are the API, not the field");
            ok = false;
        }
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
        if (method.getModifiers().contains(Modifier.PRIVATE)) {
            error(method, "@Action method " + method.getSimpleName() + " is private;"
                    + " the generated registry is in the same package and cannot see it");
            ok = false;
        }
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
                            + "\", target." + field.getSimpleName() + ")");
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
                            + lambda(target, method) + ")");
                    out.println(i == actions.size() - 1 ? ";" : "");
                }
                out.println("    }");
            }
            out.println("}");
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + qualified, e);
        }
    }

    /// A method reference for a bare action, and a parsing lambda for a valued
    /// one — which is the boilerplate every application was writing by hand.
    private static String lambda(String target, ExecutableElement method) {
        if (method.getParameters().isEmpty()) {
            return "target::" + method.getSimpleName();
        }
        var type = method.getParameters().getFirst().asType().toString();
        return "value -> target." + method.getSimpleName() + "(" + parse(type, "value") + ")";
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
