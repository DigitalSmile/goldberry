package dev.goldberry.example.ui.gallery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("a summary")
class SummariesTest {

    /// Built from halves, so this source does not cite a record either.
    private static final String ABBREVIATION = "A" + "DR";

    @ParameterizedTest
    @ValueSource(strings = {"-0107", " 0107", "0107", "s", ""})
    @DisplayName("may not cite a decision record, by number or at all")
    void aCitationIsFound(String suffix) {
        assertTrue(Summaries.citesARecord("See " + ABBREVIATION + suffix + " for why."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Adrift on the road.", "The padre's address.", "A tab strip pages from its ends."})
    @DisplayName("may use words that only contain the letters")
    void ordinaryWordsPass(String text) {
        assertFalse(Summaries.citesARecord(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://github.com/DigitalSmile/goldberry/blob/master/book/src/" + "adr/0107-x.md"})
    @DisplayName("may not link into the log either")
    void aLinkIsFound(String text) {
        assertTrue(Summaries.citesARecord(text));
    }
}
