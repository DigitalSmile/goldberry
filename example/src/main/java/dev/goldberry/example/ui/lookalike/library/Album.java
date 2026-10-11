package dev.goldberry.example.ui.lookalike.library;

import java.util.Objects;

/// A record on the shelf of suggestions.
///
/// @param title  its name
/// @param artist who made it
/// @param year   when it came out
/// @param cover  its picture
public record Album(String title, String artist, int year, Cover cover) {

    public Album {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(artist, "artist");
        Objects.requireNonNull(cover, "cover");
    }
}
