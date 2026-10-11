package dev.goldberry.example.ui.lookalike.library;

import java.util.List;
import java.util.Set;

/// The library the screen opens on: invented artists, invented songs, five
/// playlists and a shelf of albums.
///
/// Fixed rather than generated, because a golden image of the screen photographs
/// every title in it.
public final class SampleLibrary {

    private static final Track NEON_TIDE =
            track("t01", "Neon Tide", "Paper Lanterns", "Harbour Lights", "3:41", 182_331_004);
    private static final Track SLOW_SATELLITE =
            track("t02", "Slow Satellite", "Mira Okafor", "Low Orbit", "4:12", 96_204_118);
    private static final Track GLASSHOUSE =
            track("t03", "Glasshouse", "The Velvet Static", "Greenroom", "2:58", 54_880_392);
    private static final Track MIDNIGHT_ARCADE =
            track("t04", "Midnight Arcade", "Kilowatt Kids", "Insert Coin", "3:27", 311_092_550);
    private static final Track SALT_AND_CEDAR =
            track("t05", "Salt and Cedar", "Juniper Vale", "Coastal Roads", "3:55", 23_417_903);
    private static final Track PARALLEL_LINES =
            track("t06", "Parallel Lines", "Mira Okafor", "Low Orbit", "3:09", 71_550_016);
    private static final Track GOLDEN_HOUR =
            track("t07", "Golden Hour Drive", "Sundial", "Long Shadows", "4:36", 148_009_871);
    private static final Track PAPER_PLANES =
            track("t08", "Paper Planes", "Juniper Vale", "Coastal Roads", "2:44", 12_904_266);
    private static final Track STATIC_BLOOM =
            track("t09", "Static Bloom", "The Velvet Static", "Greenroom", "3:33", 40_118_725);
    private static final Track NORTHERN_WIRE =
            track("t10", "Northern Wire", "Paper Lanterns", "Harbour Lights", "5:02", 66_301_489);
    private static final Track AFTERGLOW =
            track("t11", "Afterglow (Reprise)", "Sundial", "Long Shadows", "1:58", 8_720_331);
    private static final Track PIXEL_HEART =
            track("t12", "Pixel Heart", "Kilowatt Kids", "Insert Coin", "3:14", 204_551_870);
    private static final Track DEEP_FOCUS =
            track("t13", "Deep Focus", "Lumen Drift", "Quiet Hours", "6:20", 33_009_142);
    private static final Track RAIN_ON_TIN =
            track("t14", "Rain on Tin", "Lumen Drift", "Quiet Hours", "4:47", 19_440_215);
    private static final Track FIRST_LIGHT =
            track("t15", "First Light", "Ana Lindqvist", "Morning Pages", "3:21", 57_093_664);
    private static final Track WARM_CUP =
            track("t16", "Warm Cup", "Ana Lindqvist", "Morning Pages", "2:49", 28_316_007);

    private SampleLibrary() {}

    /// The library, and the songs liked in it to begin with.
    public static Library create() {
        return new Library(
                List.of(
                        new Playlist(
                                Library.LIKED,
                                "Liked Songs",
                                "Playlist",
                                "You",
                                "Every song you gave a heart to.",
                                Cover.LIKED,
                                List.of()),
                        new Playlist(
                                "night-drive",
                                "Night Drive",
                                "Playlist",
                                "You",
                                "Synths, slow beats and a long empty road.",
                                Cover.DUSK,
                                List.of(
                                        NEON_TIDE,
                                        SLOW_SATELLITE,
                                        MIDNIGHT_ARCADE,
                                        GLASSHOUSE,
                                        PARALLEL_LINES,
                                        GOLDEN_HOUR,
                                        NORTHERN_WIRE,
                                        PIXEL_HEART,
                                        AFTERGLOW)),
                        new Playlist(
                                "weekly-finds",
                                "Weekly Finds",
                                "Playlist",
                                "Goldtune",
                                "Fresh songs picked for you, new every Monday.",
                                Cover.LAGOON,
                                List.of(
                                        SALT_AND_CEDAR,
                                        STATIC_BLOOM,
                                        PAPER_PLANES,
                                        FIRST_LIGHT,
                                        NORTHERN_WIRE,
                                        GLASSHOUSE)),
                        new Playlist(
                                "deep-focus",
                                "Deep Focus",
                                "Playlist",
                                "Goldtune",
                                "Keep calm and focus with ambient and post-rock.",
                                Cover.MOSS,
                                List.of(DEEP_FOCUS, RAIN_ON_TIN, SLOW_SATELLITE, AFTERGLOW, NORTHERN_WIRE)),
                        new Playlist(
                                "morning-coffee",
                                "Morning Coffee",
                                "Playlist",
                                "Goldtune",
                                "Soft songs to start the day with.",
                                Cover.GOLD,
                                List.of(FIRST_LIGHT, WARM_CUP, SALT_AND_CEDAR, PAPER_PLANES, GOLDEN_HOUR)),
                        new Playlist(
                                "insert-coin",
                                "Insert Coin",
                                "Album",
                                "Kilowatt Kids",
                                "Kilowatt Kids, 2024.",
                                Cover.EMBER,
                                List.of(MIDNIGHT_ARCADE, PIXEL_HEART))),
                List.of(
                        new Album("Harbour Lights", "Paper Lanterns", 2023, Cover.SLATE),
                        new Album("Low Orbit", "Mira Okafor", 2025, Cover.DUSK),
                        new Album("Greenroom", "The Velvet Static", 2022, Cover.MOSS),
                        new Album("Coastal Roads", "Juniper Vale", 2024, Cover.LAGOON),
                        new Album("Long Shadows", "Sundial", 2021, Cover.ROSE),
                        new Album("Quiet Hours", "Lumen Drift", 2026, Cover.GOLD)));
    }

    /// The tracks with a heart on them when the screen opens.
    public static Set<String> initiallyLiked() {
        return Set.of(NEON_TIDE.id(), MIDNIGHT_ARCADE.id(), GOLDEN_HOUR.id(), FIRST_LIGHT.id(), DEEP_FOCUS.id());
    }

    private static Track track(String id, String title, String artist, String album, String length, long plays) {
        return new Track(id, title, artist, album, Times.parse(length), plays);
    }
}
