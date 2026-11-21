package dev.goldberry.example.ui.input;

/// What the drag-out card lets the user carry away: the payload its two
/// draggables hold, and the name of the file each is written to.
///
/// The payload stays an enum constant inside the application, where the tray
/// takes it as itself; only the [DragOutExports] offer leaves the window.
enum Keepsake {

    /// A small painted landscape, offered as a PNG image and as a `.png` file.
    PICTURE("picture", "goldberry-picture.png"),

    /// A line of text, offered as text and as a `.txt` file.
    NOTE("note", "goldberry-note.txt");

    private final String label;

    private final String fileName;

    Keepsake(String label, String fileName) {
        this.label = label;
        this.fileName = fileName;
    }

    /// How the readouts name it: "the picture", "the note".
    String label() {
        return label;
    }

    /// The name of the file the offer writes it to.
    String fileName() {
        return fileName;
    }
}
