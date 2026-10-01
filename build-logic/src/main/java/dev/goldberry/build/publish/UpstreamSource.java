package dev.goldberry.build.publish;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An upstream's source at one pinned tag, taken from git as a tar: what the
 * {@code ffmpeg-sources} classifier carries for FFmpeg and dav1d (ADR-0508).
 *
 * <p>From git because the media superbuild builds from git: its
 * {@code ExternalProject}s shallow-clone the same repository at the same tag, so
 * this is the tree the binaries were compiled from and not an upstream tarball
 * that merely shares a version number. The commit is pinned beside the tag in
 * {@code gradle/libs.versions.toml}, and both this and the superbuild refuse a
 * tag that no longer names it -- a tag can be moved, a commit cannot, and the
 * claim "this is the source of those binaries" holds only if both sides check.
 *
 * <p>Fetched into a bare repository that outlives {@code build/}, so a second
 * archive needs no network. {@code git archive} rather than a copy of a working
 * tree, because it writes exactly the committed files: no build products, no
 * {@code .git}, nothing a local checkout picked up. The repository's own
 * attributes are overridden so that no {@code export-ignore} upstream may add
 * later can leave a file out, no {@code export-subst} can rewrite one, and no
 * line ending is converted for the archiving machine: {@code git archive} converts
 * text the way a checkout would, and Git for Windows checks out CRLF by default,
 * so without {@code -text} the same tag archived on two runners is two different
 * tarballs (ADR-0517).
 *
 * @param name       the upstream's name, the archive's top directory's first half
 * @param repository where it is cloned from
 * @param tag        the pinned tag
 * @param commit     the full commit id the tag must name
 */
public record UpstreamSource(String name, String repository, String tag, String commit) {

    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    /**
     * The clone's {@code info/attributes}, which outrank the tree's own: no path
     * is left out of an archive, none is rewritten, and none has its line endings
     * converted.
     */
    static final String ATTRIBUTES = "* -export-ignore -export-subst -text\n";

    public UpstreamSource {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(commit, "commit");
        if (!COMMIT.matcher(commit).matches()) {
            throw new IllegalArgumentException(name + "'s commit pin '" + commit
                    + "' is not a full 40-character commit id (gradle/libs.versions.toml, ADR-0508)");
        }
    }

    /** The archive's top directory: {@code ffmpeg-n8.1.3}. */
    public String directory() {
        return name + "-" + tag;
    }

    /**
     * Writes the tag's tree to {@code archive} as a tar, every path under
     * {@link #directory()}.
     *
     * @param git     the git executable
     * @param clone   the bare repository to fetch into, created if absent
     * @param archive the tar to write
     * @throws IllegalStateException if the tag does not name the pinned commit,
     *                               or git fails
     */
    public void archive(String git, Path clone, Path archive) {
        try {
            Files.createDirectories(clone);
            if (!Files.isRegularFile(clone.resolve("HEAD"))) {
                run(git, clone, "init", "--bare", "--quiet");
            }
            if (!resolvedTag(git, clone).equals(Optional.of(commit))) {
                // `+`: a tag moved upstream is fetched over the old one, and
                // then refused below rather than silently kept.
                run(git, clone, "fetch", "--quiet", "--depth", "1", "--no-tags", repository,
                        "+refs/tags/" + tag + ":refs/tags/" + tag);
            }
            var found = resolvedTag(git, clone).orElse("nothing");
            if (!found.equals(commit)) {
                throw new IllegalStateException(name + "'s tag " + tag + " at " + repository + " names " + found
                        + ", not the pinned " + commit + ". A moved tag is a different source; check upstream"
                        + " and re-pin both in gradle/libs.versions.toml (ADR-0508).");
            }
            var info = clone.resolve("info");
            Files.createDirectories(info);
            Files.writeString(info.resolve("attributes"), ATTRIBUTES, StandardCharsets.UTF_8);
            Files.createDirectories(archive.toAbsolutePath().getParent());
            // tar.umask: the modes are git's 0755 and 0644, not a user's setting.
            run(git, clone, "-c", "tar.umask=0022", "archive", "--format=tar", "--prefix=" + directory() + "/",
                    "--output=" + archive.toAbsolutePath(), commit);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The commit the tag names in the clone, if the clone has it. */
    private Optional<String> resolvedTag(String git, Path clone) throws IOException {
        var result = execute(git, clone, "rev-parse", "--verify", "--quiet", "refs/tags/" + tag + "^{commit}");
        return result.exit() == 0 ? Optional.of(result.output().strip()) : Optional.empty();
    }

    private static void run(String git, Path clone, String... arguments) throws IOException {
        var result = execute(git, clone, arguments);
        if (result.exit() != 0) {
            throw new IllegalStateException("git " + String.join(" ", arguments) + " failed with exit code "
                    + result.exit() + ":\n" + result.output());
        }
    }

    private static Result execute(String git, Path clone, String... arguments) throws IOException {
        var command = new ArrayList<String>(List.of(git, "--git-dir=" + clone.toAbsolutePath()));
        command.addAll(List.of(arguments));
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        // A credential prompt would hang a CI job until its timeout.
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        var process = builder.start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            return new Result(process.waitFor(), output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();
            throw new IllegalStateException("interrupted running git " + String.join(" ", arguments), e);
        }
    }

    private record Result(int exit, String output) {
    }
}
