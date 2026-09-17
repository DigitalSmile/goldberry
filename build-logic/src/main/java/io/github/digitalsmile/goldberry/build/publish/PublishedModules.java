package io.github.digitalsmile.goldberry.build.publish;

import io.github.digitalsmile.goldberry.build.publish.PublishedModule.Bom;
import io.github.digitalsmile.goldberry.build.publish.PublishedModule.Inclusion;
import io.github.digitalsmile.goldberry.build.publish.PublishedModule.Library;
import io.github.digitalsmile.goldberry.build.publish.PublishedModule.Umbrella;

import java.util.List;
import java.util.Optional;

/**
 * Everything published to Maven Central ({@code docs/ARCHITECTURE.md} §15,
 * ADR-0334, ADR-0336).
 *
 * <p>Written down once rather than inferred from which build scripts apply
 * {@code goldberry.publish}, so that applying it to a build-time tool by mistake
 * fails the build instead of putting {@code goldberry-assets} on Central, where
 * nothing can ever take it back. {@code PublishedModulesTest} holds the build
 * scripts to this list in the other direction. The BOM's constraints and the
 * umbrella's dependencies are generated from it, so a new content module is one
 * line here and nothing else.
 */
public final class PublishedModules {

    /** The group every artifact is published under. */
    public static final String GROUP = "io.github.digitalsmile";

    /**
     * Everything, in dependency order. {@code :assets} and {@code :weaver} are
     * build-time tools and {@code :example} is the showcase, which goes to GitHub
     * Packages as an image instead (ADR-0335).
     */
    public static final List<PublishedModule> ALL = List.of(
            new Library("common", Inclusion.REQUIRED),
            new Library("natives", Inclusion.REQUIRED),
            new Library("core", Inclusion.REQUIRED),
            new Library("widgets", Inclusion.REQUIRED),
            new Library("html", Inclusion.OPTIONAL),
            new Library("gpu", Inclusion.OPTIONAL),
            new Bom(),
            new Umbrella());

    private PublishedModules() {
    }

    /** Every library, required and optional. */
    public static List<Library> libraries() {
        return ALL.stream()
                .<Library>mapMulti((module, sink) -> {
                    if (module instanceof Library library) {
                        sink.accept(library);
                    }
                })
                .toList();
    }

    /** The libraries an application gets through the umbrella in that way. */
    public static List<Library> libraries(Inclusion inclusion) {
        return libraries().stream().filter(library -> library.inclusion() == inclusion).toList();
    }

    /** Every Gradle project that applies {@code goldberry.publish}. */
    public static List<String> projectNames() {
        return ALL.stream().map(PublishedModule::project).toList();
    }

    /** The module a Gradle project publishes, if it publishes one. */
    public static Optional<PublishedModule> find(String project) {
        return ALL.stream().filter(module -> module.project().equals(project)).findFirst();
    }

    /** Whether the project of that name ships. */
    public static boolean isPublished(String project) {
        return find(project).isPresent();
    }

    /**
     * The module a Gradle project publishes.
     *
     * @throws IllegalArgumentException for a project that is not published
     */
    public static PublishedModule require(String project) {
        return find(project).orElseThrow(() -> new IllegalArgumentException(
                ":" + project + " is not a published module; the published ones are " + projectNames()
                        + " (PublishedModules, ADR-0334)"));
    }

    /**
     * The artifact id a project is published under.
     *
     * @throws IllegalArgumentException for a project that is not published
     */
    public static String artifactId(String project) {
        return require(project).artifactId();
    }

    /**
     * How a module is shown in a POM's {@code <name>}: {@code Goldberry core},
     * {@code Goldberry BOM}, {@code Goldberry}.
     */
    public static String displayName(PublishedModule module) {
        return switch (module) {
            case Library(var project, var _) -> "Goldberry " + project;
            case Bom _ -> "Goldberry BOM";
            case Umbrella _ -> "Goldberry";
        };
    }
}
