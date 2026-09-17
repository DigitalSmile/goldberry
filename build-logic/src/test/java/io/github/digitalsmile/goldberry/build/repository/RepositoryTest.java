package io.github.digitalsmile.goldberry.build.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Repository")
class RepositoryTest {

    @Test
    @DisplayName("turns CRLF and a bare CR into LF, and leaves LF alone")
    void lineFeeds() {
        assertAll(
                () -> assertEquals("a\nb\nc\n", Repository.lineFeeds("a\r\nb\rc\n")),
                () -> assertEquals("a\n\nb", Repository.lineFeeds("a\r\n\r\nb")),
                () -> assertEquals("plain\n", Repository.lineFeeds("plain\n")),
                () -> assertEquals("", Repository.lineFeeds("")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"showcase.yml", "linux.yml", "nightly.yml"})
    @DisplayName("hands a workflow over with no carriage return, whatever the checkout did")
    void workflowsAreLineFeedOnly(String name) {
        var text = Repository.workflow(name);
        assertAll(
                () -> assertFalse(text.contains("\r"), name + " still carries a CR"),
                () -> assertTrue(text.contains("\n\n"), name + " has no blank line, which the guards split on"));
    }

    @Test
    @DisplayName("a missing file is an error, not an empty string")
    void missingFileIsAnError() {
        assertAll(
                () -> assertThrows(AssertionError.class, () -> Repository.read("no/such/file.txt")),
                () -> assertFalse(Repository.exists("no/such/file.txt")));
    }
}
