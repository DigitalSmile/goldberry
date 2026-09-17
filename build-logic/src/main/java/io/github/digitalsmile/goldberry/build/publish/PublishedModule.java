package io.github.digitalsmile.goldberry.build.publish;

/**
 * One thing Goldberry publishes to Maven Central, and the Gradle project it is
 * built from ({@code docs/ARCHITECTURE.md} §15, ADR-0334, ADR-0336).
 *
 * <p>Three kinds, because they are published three different ways: a
 * {@link Library} is a {@code java-library} with code in it; the {@link Bom} is a
 * {@code java-platform} that pins every other artifact's version; the
 * {@link Umbrella} is the one dependency an application starts from, carrying the
 * required libraries and naming the optional ones.
 */
public sealed interface PublishedModule
        permits PublishedModule.Library, PublishedModule.Bom, PublishedModule.Umbrella {

    /** What every artifact id but the umbrella's starts with. */
    String ARTIFACT_PREFIX = "goldberry-";

    /** The Gradle project name, without the colon. */
    String project();

    /** The Maven artifact id. */
    String artifactId();

    /** Whether an application gets a library through the umbrella, or opts into it. */
    enum Inclusion {
        /** A compile dependency of {@code goldberry}: every application has it. */
        REQUIRED,
        /**
         * An {@code <optional>} dependency of {@code goldberry}: listed, versioned
         * by the BOM, and added by an application that wants it -- the content
         * modules, and {@code :gpu} (ADR-0190).
         */
        OPTIONAL
    }

    /** A module with code in it, published as {@code goldberry-<project>}. */
    record Library(String project, Inclusion inclusion) implements PublishedModule {
        @Override
        public String artifactId() {
            return ARTIFACT_PREFIX + project;
        }
    }

    /** {@code goldberry-bom}, the version alignment for everything else. */
    record Bom() implements PublishedModule {
        @Override
        public String project() {
            return "bom";
        }

        @Override
        public String artifactId() {
            return ARTIFACT_PREFIX + "bom";
        }
    }

    /** {@code goldberry}, the one dependency to start from. */
    record Umbrella() implements PublishedModule {
        @Override
        public String project() {
            return "toolkit";
        }

        @Override
        public String artifactId() {
            return "goldberry";
        }
    }
}
