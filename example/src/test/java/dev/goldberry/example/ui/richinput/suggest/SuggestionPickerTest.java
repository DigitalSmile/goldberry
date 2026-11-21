package dev.goldberry.example.ui.richinput.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a picker hands the field.
class SuggestionPickerTest {

    @Test
    @DisplayName("the field is told every trigger the picker answers")
    void triggers() {
        try (var picker = new SuggestionPicker(Directories.ALL, () -> {
            throw new AssertionError("nothing is shown without a query");
        })) {
            assertEquals(List.of("@", "#", ":", "/"), picker.triggers());
            assertEquals(Set.of("@", "#", ":", "/"), picker.mentions().triggers());
            assertEquals(List.of(), picker.matches());
        }
    }

    @Test
    @DisplayName("the composer's picker answers @ alone")
    void people() {
        try (var picker = SuggestionPicker.people(Contacts.ALL, () -> {
            throw new AssertionError("nothing is shown without a query");
        })) {
            assertEquals(List.of("@"), picker.triggers());
        }
    }
}
