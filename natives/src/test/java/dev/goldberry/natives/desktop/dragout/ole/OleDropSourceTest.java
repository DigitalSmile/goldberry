package dev.goldberry.natives.desktop.dragout.ole;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.desktop.windows.Com;

/// [OleDropSource], called through its vtable as `DoDragDrop` calls it.
@SuppressWarnings("restricted")
@DisplayName("a drag's IDropSource")
class OleDropSourceTest {

    private static final MethodHandle QUERY_CONTINUE =
            Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle GIVE_FEEDBACK =
            Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    @Test
    @DisplayName("puts both questions to its callbacks and answers with theirs")
    void asks() {
        var heard = new ArrayList<String>();
        var source = new OleDropSource(new OleDropSource.Callbacks() {
            @Override
            public int queryContinueDrag(boolean escape, int keys) {
                heard.add("continue " + escape + " " + keys);
                return OleStructs.DRAGDROP_S_DROP;
            }

            @Override
            public int giveFeedback(int effect) {
                heard.add("feedback " + effect);
                return 0;
            }
        });
        try {
            assertEquals(OleStructs.DRAGDROP_S_DROP, (int) Com.call(source.pointer(), 3, QUERY_CONTINUE, 1, 0x9));
            assertEquals(0, (int) Com.call(source.pointer(), 4, GIVE_FEEDBACK, OleStructs.DROPEFFECT_COPY));
            assertEquals(List.of("continue true 9", "feedback 1"), heard);
        } finally {
            source.release();
        }
    }

    @Test
    @DisplayName("cancels when its callbacks throw, and keeps OLE's cursors")
    void throwing() {
        var source = new OleDropSource(new OleDropSource.Callbacks() {
            @Override
            public int queryContinueDrag(boolean escape, int keys) {
                throw new IllegalStateException("the application's own failure");
            }

            @Override
            public int giveFeedback(int effect) {
                throw new IllegalStateException("the application's own failure");
            }
        });
        try {
            assertEquals(OleStructs.DRAGDROP_S_CANCEL, (int) Com.call(source.pointer(), 3, QUERY_CONTINUE, 0, 1));
            assertEquals(
                    OleStructs.DRAGDROP_S_USEDEFAULTCURSORS, (int) Com.call(source.pointer(), 4, GIVE_FEEDBACK, 1));
        } finally {
            source.release();
        }
    }
}
