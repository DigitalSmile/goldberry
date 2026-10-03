package dev.goldberry.build.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.goldberry.build.repository.Repository;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The CodeQL suite is one file, {@code config/codeql/goldberry.qls}, which the
 * workflow and the local recipe both run, and it leaves out exactly the four
 * queries whose every finding here was a false positive.
 *
 * <p>Two copies of the query list, one in {@code codeql.yml} and one in
 * {@code docs/testing.md}, would drift, and a local run that reads different
 * queries from the dashboard's is a triage of the wrong thing. Excluding a fifth
 * query is a decision, so it is a change to this test as well as to the suite.
 */
@DisplayName("the CodeQL suite")
class CodeQlSuiteTest {

    private static final String SUITE = "config/codeql/goldberry.qls";

    /** The excluded query ids, in the suite's order. */
    private static final List<String> EXCLUDED = List.of(
            "java/local-variable-is-never-read",
            "java/unused-parameter",
            "java/internal-representation-exposure",
            "java/missing-case-in-switch");

    private static final Pattern EXCLUDED_ID = Pattern.compile("^\\s+-\\s+(java/[\\w-]+)\\s*$", Pattern.MULTILINE);

    @Test
    @DisplayName("is what the workflow runs")
    void workflowRunsIt() {
        var workflow = Repository.workflow("codeql.yml");

        assertTrue(workflow.contains("queries: ./" + SUITE), "codeql.yml's init step should name " + SUITE);
    }

    @Test
    @DisplayName("is what the local recipe runs")
    void recipeRunsIt() {
        var testing = Repository.read("docs/testing.md");

        assertTrue(
                testing.contains("codeql database analyze /tmp/gb-db " + SUITE),
                "docs/testing.md's CodeQL recipe should analyse with " + SUITE);
    }

    @Test
    @DisplayName("is security-and-quality less exactly four queries")
    void fourExcluded() {
        var suite = Repository.read(SUITE);

        assertTrue(suite.contains("- import: codeql-suites/java-security-and-quality.qls"), suite);
        assertTrue(suite.contains("from: codeql/java-queries"), suite);
        assertEquals(EXCLUDED, EXCLUDED_ID.matcher(suite).results().map(match -> match.group(1)).toList());
    }
}
