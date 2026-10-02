package dev.goldberry.build.publish;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts of a POM that Maven Central refuses a release without, as the
 * publication is about to go.
 *
 * <p>Central validates a deployment only once it has the whole upload, and a
 * failed validation drops the deployment. A snapshot is not validated at all, so
 * a POM that has lost its description publishes as a snapshot every day and
 * fails on release day. The first release did that: {@code goldberry.publish}
 * read {@code project.description} while the plugin was applied, before the
 * build script's own {@code description = ...} line had run. All ten artifacts
 * went up without one, and Central refused every one of them.
 *
 * <p>Checked by {@code goldberry.publish} against every publication a build is
 * about to publish, snapshots and {@code mavenLocal} included, so the same
 * mistake fails the next push to master and not the next tag.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/contributing/releasing.html#where-things-go">Where
 * things go</a>.
 *
 * @param artifactId  the publication's artifact id, for the message
 * @param name        the POM's {@code <name>}, or {@code null}
 * @param description the POM's {@code <description>}, or {@code null}
 * @param url         the POM's {@code <url>}, or {@code null}
 */
public record CentralPom(String artifactId, String name, String description, String url) {

    /** The elements Central wants and this POM leaves out or leaves blank, in document order. */
    public List<String> missing() {
        var elements = new LinkedHashMap<String, String>();
        elements.put("name", name);
        elements.put("description", description);
        elements.put("url", url);
        return elements.entrySet().stream()
                .filter(element -> element.getValue() == null || element.getValue().isBlank())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Refuses a POM that Central would refuse.
     *
     * @throws IllegalStateException if the name, the description or the URL is
     *                               missing or blank
     */
    public void require() {
        var missing = missing();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(artifactId + "'s POM has no " + String.join(", ", missing)
                    + ". Maven Central refuses a release without it, and validates nothing until the whole"
                    + " deployment is up. Give the project a description in its build script; goldberry.publish"
                    + " reads it when the POM is written.");
        }
    }
}
