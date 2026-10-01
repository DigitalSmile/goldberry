package io.github.digitalsmile.goldberry.build.publish;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rule that FFmpeg's object code is never published without its source
 * beside it (ADR-0508).
 *
 * <p>{@code goldberry-media} carries FFmpeg, which is LGPL-2.1-or-later, as
 * {@code ffmpeg-<target>} classifier jars (ADR-0495). LGPL-2.1 §4 lets the
 * library be distributed in object form only together with its complete
 * corresponding source, or with equivalent access to copy that source from the
 * same place. The {@code ffmpeg-sources} classifier is that source, published
 * from the same place, under the same coordinates. A snapshot on Central is a
 * distribution as much as a release is, so the rule makes no exception for one.
 *
 * <p>Checked by {@code goldberry.publish} against every publication a build is
 * about to publish, so a build script that attaches the binaries and forgets the
 * source -- or a refactoring that drops the line attaching it -- fails before
 * any module uploads, not on a reader's complaint.
 */
public final class CorrespondingSource {

    /** The classifier every FFmpeg binary jar's starts with: {@code ffmpeg-linux-x64}. */
    public static final String BINARY_PREFIX = "ffmpeg-";

    /** The classifier of the jar holding FFmpeg's and dav1d's complete source. */
    public static final String SOURCES = "ffmpeg-sources";

    private CorrespondingSource() {
    }

    /**
     * The FFmpeg binaries among a publication's classifiers, sorted: every
     * {@code ffmpeg-*} classifier but the source's own. The main jar's classifier
     * is {@code null}, and is not one.
     */
    public static Set<String> binaries(Collection<String> classifiers) {
        var binaries = new TreeSet<String>();
        classifiers.stream()
                .filter(Objects::nonNull)
                .filter(classifier -> classifier.startsWith(BINARY_PREFIX) && !classifier.equals(SOURCES))
                .forEach(binaries::add);
        return binaries;
    }

    /**
     * Refuses a publication that carries FFmpeg's binaries without its source.
     *
     * @param artifactId  the publication's artifact id, for the message
     * @param classifiers every artifact's classifier, {@code null} for the main jar
     * @throws IllegalStateException if an {@code ffmpeg-<target>} classifier is
     *                               there and {@code ffmpeg-sources} is not
     */
    public static void require(String artifactId, Collection<String> classifiers) {
        var binaries = binaries(classifiers);
        if (!binaries.isEmpty() && !classifiers.contains(SOURCES)) {
            throw new IllegalStateException(artifactId + " would publish " + String.join(", ", binaries)
                    + " without " + SOURCES + ". FFmpeg is LGPL-2.1: its object code is distributed only beside"
                    + " its complete corresponding source, from the same place (§4), and a snapshot is a"
                    + " distribution too. Attach :media:ffmpegSourcesJar as the " + SOURCES
                    + " classifier (ADR-0508).");
        }
    }
}
