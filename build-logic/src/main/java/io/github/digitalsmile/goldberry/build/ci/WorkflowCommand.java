package io.github.digitalsmile.goldberry.build.ci;

import java.util.Map;
import java.util.Objects;

/**
 * One GitHub Actions workflow command -- a line such as
 * {@code ::error title=...::message} that the runner turns into an annotation
 * (ADR-0338).
 *
 * <p>Annotations are what makes a CI failure readable from outside. A job log
 * cannot be read without signing in, but a check run's annotations are served by
 * the public API, so a failure written as one names itself to anyone -- including
 * a tool that has no credentials. Before this, every red run said only
 * "Process completed with exit code 1".
 *
 * <p>Escaping follows the runner's own rules: {@code %}, CR and LF in the message;
 * those plus {@code :} and {@code ,} in a property. Getting it wrong is silent --
 * a newline ends the command and the rest of the message becomes an ordinary log
 * line.
 *
 * @param level   the annotation's severity
 * @param title   a short heading, shown above the message
 * @param message the body, newlines allowed
 */
public record WorkflowCommand(Level level, String title, String message) {

    /**
     * The most a single annotation carries. The runner accepts more and the UI
     * truncates it anyway; a cap keeps a stack trace from filling a log line.
     */
    public static final int MAX_MESSAGE_LENGTH = 4000;

    /** The environment variable every GitHub Actions runner sets to {@code true}. */
    public static final String GITHUB_ACTIONS = "GITHUB_ACTIONS";

    /** Severity, named as the runner names the command. */
    public enum Level {
        ERROR("error"),
        WARNING("warning"),
        NOTICE("notice");

        private final String command;

        Level(String command) {
            this.command = command;
        }

        /** @return the command word, e.g. {@code error} */
        public String command() {
            return command;
        }
    }

    public WorkflowCommand {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(message, "message");
    }

    /** @return an error annotation */
    public static WorkflowCommand error(String title, String message) {
        return new WorkflowCommand(Level.ERROR, title, message);
    }

    /**
     * @param environment the process environment, usually {@link System#getenv()}
     * @return whether this process runs on a GitHub Actions runner
     */
    public static boolean onGithubActions(Map<String, String> environment) {
        return "true".equalsIgnoreCase(environment.get(GITHUB_ACTIONS));
    }

    /** @return the single line the runner reads, escaped */
    public String render() {
        var body = message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH) + "\n..."
                : message;
        return "::" + level.command() + " title=" + escapeProperty(title) + "::" + escapeData(body);
    }

    @Override
    public String toString() {
        return render();
    }

    /** Escapes a command's message. */
    static String escapeData(String value) {
        return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
    }

    /** Escapes a property value, which also may not contain its delimiters. */
    static String escapeProperty(String value) {
        return escapeData(value).replace(":", "%3A").replace(",", "%2C");
    }
}
