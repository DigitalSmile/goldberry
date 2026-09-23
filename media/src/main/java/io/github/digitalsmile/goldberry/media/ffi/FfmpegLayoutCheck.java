package io.github.digitalsmile.goldberry.media.ffi;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.util.ArrayList;
import java.util.List;

/// Compares the hand-written layouts in [FfmpegStructs] with what the layout probe
/// reported about the FFmpeg actually built (`docs/goldberry-media.md` §2, "Layout
/// agreement").
///
/// It stands in for the guarantee jextract would have given, as `:natives`'
/// `LayoutVerifier` does for `libgoldberry` (ADR-0010), and it is the stronger of
/// the two: it compares Java with the library for this target rather than
/// generated code with other generated code.
///
/// Every disagreement is collected rather than the first one thrown. A padding
/// member declared one int too short moves every field after it, and seeing them
/// all at once shows which one moved.
public final class FfmpegLayoutCheck {

    private FfmpegLayoutCheck() {}

    /// Checks `structs` and the pinned majors against `probe`.
    ///
    /// It checks four things, in both directions where there are two:
    ///
    /// - each library major the probe saw equals [FfmpegLibrary#pinnedMajor()];
    /// - each struct's size and alignment;
    /// - each named field's offset and size;
    /// - that the probe and Java name the same fields. A field only the probe names
    ///   is one the §2 table lists and Java forgot. A field only Java names is one
    ///   nothing verifies.
    ///
    /// @return one sentence per disagreement; empty means the two agree
    public static List<String> verify(FfmpegLayout probe, List<StructLayout> structs) {
        var problems = new ArrayList<String>();

        for (var library : FfmpegLibrary.values()) {
            var reported = probe.major(library);
            if (reported.isEmpty()) {
                problems.add("the layout file does not say which " + library.stem() + " it describes");
            } else if (reported.get() != library.pinnedMajor()) {
                problems.add("the layout file describes " + library.stem() + " " + reported.get()
                        + ", but these bindings are written against " + library.pinnedMajor());
            }
        }

        var declared = new ArrayList<String>();
        for (var struct : structs) {
            var name = struct.name().orElseThrow(() -> new IllegalArgumentException("an unnamed struct layout"));
            declared.add(name);
            var reported = probe.struct(name);
            if (reported.isEmpty()) {
                problems.add(name + " is declared in Java but the probe does not report it,"
                        + " so nothing verifies it — add it to ffmpeg_layout.c");
                continue;
            }
            if (struct.byteSize() != reported.get().first()) {
                problems.add(name + ": Java sizeof=" + struct.byteSize() + ", C sizeof="
                        + reported.get().first());
            }
            if (struct.byteAlignment() != reported.get().second()) {
                problems.add(name + ": Java alignof=" + struct.byteAlignment() + ", C alignof="
                        + reported.get().second());
            }
            var fields = new ArrayList<String>();
            for (var member : struct.memberLayouts()) {
                if (member.name().isEmpty()) {
                    continue; // padding: there is nothing on the C side to compare it with
                }
                var field = member.name().get();
                fields.add(name + "." + field);
                var cField = probe.field(name, field);
                if (cField.isEmpty()) {
                    problems.add(name + "." + field + " is declared in Java but the probe does not report it");
                    continue;
                }
                var offset = struct.byteOffset(MemoryLayout.PathElement.groupElement(field));
                if (offset != cField.get().first()) {
                    problems.add(name + "." + field + ": Java offset=" + offset + ", C offset="
                            + cField.get().first());
                }
                if (member.byteSize() != cField.get().second()) {
                    problems.add(name + "." + field + ": Java sizeof=" + member.byteSize() + ", C sizeof="
                            + cField.get().second());
                }
            }
            for (var reportedField : probe.fields().keySet()) {
                if (reportedField.startsWith(name + ".") && !fields.contains(reportedField)) {
                    problems.add(reportedField + " is reported by the probe but not declared in FfmpegStructs");
                }
            }
        }
        for (var reportedStruct : probe.structs().keySet()) {
            if (!declared.contains(reportedStruct)) {
                problems.add(reportedStruct + " is reported by the probe but not declared in FfmpegStructs");
            }
        }
        return List.copyOf(problems);
    }
}
