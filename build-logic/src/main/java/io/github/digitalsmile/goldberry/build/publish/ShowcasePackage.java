package io.github.digitalsmile.goldberry.build.publish;

import java.util.List;

/**
 * One packaged showcase, as {@code showcase.yml} archives it and GitHub Packages
 * stores it: {@code io.github.digitalsmile:<kind's artifact id>}, with the target
 * as the classifier (ADR-0335, ADR-0337).
 *
 * <p>The file format is not a free choice. {@code upload-artifact} zips what it is
 * given and its zip drops the executable bit, so anything a unix user has to run
 * is tarred; Windows has no such bit. A jlink image is a directory, so on Windows
 * it is zipped; a native image is one {@code .exe}, so on Windows it travels as
 * itself. The workflow writes the names and this record reads them, so the rule
 * lives here once and {@code ShowcasePackageTest} holds the workflow to it.
 *
 * @param kind   which build of the showcase
 * @param target the platform it was built on, one of {@link #TARGETS}
 */
public record ShowcasePackage(Kind kind, String target) {

    /** The platforms {@code showcase.yml} builds on, for both kinds. */
    public static final List<String> TARGETS = List.of("linux-x64", "macos-aarch64", "windows-x64");

    /** The two builds of the showcase, each its own artifact. */
    public enum Kind {
        /** A jlink runtime image with a launcher -- ADR-0048. */
        RUNTIME_IMAGE("goldberry-showcase", "showcase", "goldberry.showcaseDir"),
        /** A GraalVM native image, one file -- ADR-0159, ADR-0337. */
        NATIVE_IMAGE("goldberry-showcase-native", "showcaseNative", "goldberry.showcaseNativeDir");

        private final String artifactId;
        private final String publicationName;
        private final String directoryProperty;

        Kind(String artifactId, String publicationName, String directoryProperty) {
            this.artifactId = artifactId;
            this.publicationName = publicationName;
            this.directoryProperty = directoryProperty;
        }

        /** The Maven artifact id in GitHub Packages. */
        public String artifactId() {
            return artifactId;
        }

        /**
         * The Gradle publication, and so the task
         * {@code publish<Name>PublicationToGithubPackagesRepository}.
         */
        public String publicationName() {
            return publicationName;
        }

        /** The Gradle property naming the directory the archives were downloaded to. */
        public String directoryProperty() {
            return directoryProperty;
        }

        /** The task the workflow runs to publish this kind. */
        public String publishTask() {
            return "publish" + Character.toUpperCase(publicationName.charAt(0)) + publicationName.substring(1)
                    + "PublicationToGithubPackagesRepository";
        }
    }

    /** How a package is stored. */
    public enum Archive {
        TAR_GZ("tar.gz"),
        ZIP("zip"),
        EXE("exe");

        private final String extension;

        Archive(String extension) {
            this.extension = extension;
        }

        /** The file extension, which is also the Maven artifact extension. */
        public String extension() {
            return extension;
        }
    }

    public ShowcasePackage {
        if (!TARGETS.contains(target)) {
            throw new IllegalArgumentException(
                    "no showcase is built for '" + target + "'; the targets are " + TARGETS);
        }
    }

    /** Every package one {@code showcase.yml} run produces of a kind. */
    public static List<ShowcasePackage> all(Kind kind) {
        return TARGETS.stream().map(target -> new ShowcasePackage(kind, target)).toList();
    }

    /** How this package is stored. */
    public Archive archive() {
        var windows = target.startsWith("windows-");
        return switch (kind) {
            case RUNTIME_IMAGE -> windows ? Archive.ZIP : Archive.TAR_GZ;
            case NATIVE_IMAGE -> windows ? Archive.EXE : Archive.TAR_GZ;
        };
    }

    /** The file name, as the workflow writes it. */
    public String fileName() {
        return kind.artifactId() + "-" + target + "." + archive().extension();
    }
}
