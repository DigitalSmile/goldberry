package dev.goldberry.vector.robustness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/// The fuzz corpus: the documents under `robustness/corpus/` that every kind of
/// damage starts from. Between them they reach every part of ThorVG's SVG
/// loader this build compiles: the shapes, path data with every command, both
/// gradients with inheritance and spread, `<symbol>` and `<use>`, clips and
/// masks, `<style>` with a doctype's entity, embedded PNG and JPEG, nested
/// viewports, and the elements it reads and draws nothing for.
final class Corpus {

    /// One document of the corpus.
    ///
    /// @param name  its file name, which a failure names
    /// @param bytes the file
    record Document(String name, byte[] bytes) {

        Document {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(bytes, "bytes");
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final List<String> NAMES = List.of(
            "shapes.svg",
            "paths.svg",
            "gradients.svg",
            "reuse.svg",
            "clip-mask.svg",
            "style.svg",
            "image.svg",
            "nested.svg",
            "text.svg");

    private static final List<Document> DOCUMENTS =
            NAMES.stream().map(Corpus::read).toList();

    private Corpus() {}

    /// Every document, in a fixed order.
    static List<Document> documents() {
        return DOCUMENTS;
    }

    private static Document read(String name) {
        try (var in = Corpus.class.getResourceAsStream("corpus/" + name)) {
            if (in == null) {
                throw new IllegalStateException("the corpus has no " + name);
            }
            return new Document(name, in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
