package dev.goldberry.build.release;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import dev.goldberry.build.version.CalendarVersion;

/**
 * The body of a GitHub Release: the changelog's section for the version, written
 * into the release-notes template.
 *
 * <p>{@code CHANGELOG.md} has one {@code ## <version>} section per release, newest
 * first, and an {@code ## Unreleased} section may sit on top while the next one is
 * being written. Anything inside an HTML comment is not part of any section, which is
 * where the file keeps the template for a new one. The release template
 * ({@code .github/release-notes.md}) holds {@value #CHANGES} where the section goes
 * and {@value #VERSION} wherever the version is named.
 *
 * <p>A release without its section is refused rather than published with an empty
 * body: the notes are written once, by a person, before the tag.
 *
 * @param version the release, such as {@code 2026.2}
 * @param changes the section's text, without its heading
 */
public record ReleaseNotes(CalendarVersion version, String changes) {

    /** Where the template names the version. */
    public static final String VERSION = "{{version}}";

    /** Where the template puts the version's changelog section. */
    public static final String CHANGES = "{{changes}}";

    /** The heading of the section that collects what the next release will hold. */
    public static final String UNRELEASED = "Unreleased";

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern HEADING = Pattern.compile("^## +(\\S+).*$");

    /**
     * One section of the changelog.
     *
     * @param name the heading's first word: a version, or {@value #UNRELEASED}
     * @param body what follows the heading, up to the next section, trimmed
     */
    public record Section(String name, String body) {

        public Section {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(body, "body");
        }

        /** The version the section is for, or empty for {@value #UNRELEASED}. */
        public Optional<CalendarVersion> version() {
            return name.equals(UNRELEASED) ? Optional.empty() : Optional.of(CalendarVersion.parse(name));
        }
    }

    public ReleaseNotes {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(changes, "changes");
        if (changes.isBlank()) {
            throw new IllegalArgumentException("the notes for " + version + " are empty");
        }
    }

    /**
     * The changelog's sections in the order they are written.
     *
     * @throws IllegalArgumentException when a heading names neither a version nor
     *                                  {@value #UNRELEASED}
     */
    public static List<Section> sections(String changelog) {
        var sections = new ArrayList<Section>();
        String name = null;
        var body = new StringBuilder();
        for (var line : COMMENT.matcher(changelog).replaceAll("").lines().toList()) {
            var heading = HEADING.matcher(line);
            if (heading.matches()) {
                if (name != null) {
                    sections.add(new Section(name, body.toString().strip()));
                }
                name = heading.group(1);
                body.setLength(0);
            } else if (name != null) {
                body.append(line).append('\n');
            }
        }
        if (name != null) {
            sections.add(new Section(name, body.toString().strip()));
        }
        sections.forEach(Section::version);
        return List.copyOf(sections);
    }

    /**
     * The notes for {@code version}, from its section of {@code changelog}.
     *
     * @throws IllegalArgumentException when the changelog has no section for it, or an
     *                                  empty one
     */
    public static ReleaseNotes of(String changelog, String version) {
        var release = CalendarVersion.parse(version);
        var sections = sections(changelog);
        return sections.stream()
                .filter(section -> section.version().filter(release::equals).isPresent())
                .findFirst()
                .map(section -> new ReleaseNotes(release, section.body()))
                .orElseThrow(() -> new IllegalArgumentException("CHANGELOG.md has no `## " + release + "` section"
                        + (sections.stream().anyMatch(section -> section.name().equals(UNRELEASED))
                                ? "; rename `## " + UNRELEASED + "` to it before the tag"
                                : "; write one before the tag")));
    }

    /**
     * The template with the version and the changes written in.
     *
     * @throws IllegalArgumentException when the template has nowhere for the changes
     */
    public String render(String template) {
        if (!template.contains(CHANGES)) {
            throw new IllegalArgumentException("the release-notes template has no " + CHANGES);
        }
        return template.replace(CHANGES, changes).replace(VERSION, version.toString());
    }
}
