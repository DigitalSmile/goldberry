package dev.goldberry.example.ui.collections;

import java.util.List;

/// One of the Company: the row type the table and the slot share.
///
/// A record and not three parallel strings, so a sort on `kindred` is a
/// `Comparator` over a field rather than over a cell's drawn text.
///
/// @param id      what selection carries
/// @param name    the label
/// @param kindred the people they come from
/// @param realm   where they set out
/// @param leagues how far they walked
record Walker(String id, String name, String kindred, String realm, int leagues) {

    /// Nine of them, with kindreds that repeat and leagues that do not, so a sort
    /// on either column is worth pressing.
    static final List<Walker> COMPANY = List.of(
            new Walker("frodo", "Frodo", "Hobbit", "The Shire", 1795),
            new Walker("samwise", "Samwise", "Hobbit", "The Shire", 1795),
            new Walker("meriadoc", "Meriadoc", "Hobbit", "Buckland", 1240),
            new Walker("peregrin", "Peregrin", "Hobbit", "Tuckborough", 1240),
            new Walker("aragorn", "Aragorn", "Man", "Arnor", 2310),
            new Walker("boromir", "Boromir", "Man", "Gondor", 980),
            new Walker("legolas", "Legolas", "Elf", "Mirkwood", 2110),
            new Walker("gimli", "Gimli", "Dwarf", "Erebor", 2110),
            new Walker("gandalf", "Gandalf", "Maia", "—", 2680));
}
