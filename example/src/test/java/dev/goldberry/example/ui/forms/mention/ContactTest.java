package dev.goldberry.example.ui.forms.mention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.text.mark.MarkKind;

/// Who a query finds.
class ContactTest {

    private static final Contact ANN = new Contact("ann", "Ann Lee");

    @Test
    @DisplayName("a query matches the start of the handle or of any word of the name, ignoring case")
    void matches() {
        assertTrue(ANN.matches(""));
        assertTrue(ANN.matches("AN"));
        assertTrue(ANN.matches("le"));
        assertFalse(ANN.matches("ee"), "not the middle of a word");
        assertFalse(ANN.matches("bo"));
    }

    @Test
    @DisplayName("the mention is the name after an @, an atom naming the handle")
    void mention() {
        assertEquals("@Ann Lee", ANN.mentionText());
        assertEquals(new MarkKind.Mention("user:ann"), ANN.kind());
        assertTrue(ANN.kind().isAtomic());
    }

    @Test
    @DisplayName("the list is in order, and capped")
    void matching() {
        assertEquals(
                List.of("Ann Lee", "Ana Silva"),
                Contacts.matching(Contacts.ALL, "a").stream().map(Contact::name).toList());
        assertEquals(Contacts.SHOWN, Contacts.matching(Contacts.ALL, "").size());
    }

    @Test
    @DisplayName("needs a handle and a name")
    void refusesBlank() {
        assertThrows(IllegalArgumentException.class, () -> new Contact(" ", "Ann"));
        assertThrows(IllegalArgumentException.class, () -> new Contact("ann", ""));
    }
}
