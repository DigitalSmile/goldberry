package io.github.digitalsmile.goldberry.render.dialog;

import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.render.Backend;
import io.github.digitalsmile.goldberry.render.window.BackendWindow;

/// The platform's own file dialogs, as the toolkit sees them.
///
/// The second facility on [Backend] that is a *conversation* rather than a
/// value, after the clipboard — and the first that takes as long as a person
/// takes. Everything about this interface follows from that.
///
/// ## Asynchronous, and there is no synchronous version
///
/// [#show] returns immediately and the answer arrives in `onChoice`. There is no
/// blocking overload and there will not be one: the UI thread is the only thread
/// allowed to touch windows, so blocking it until the user has finished browsing
/// their disk stops the frame loop, the animations, the resize handler and the
/// window's own repainting — on Linux it would also stop the DBus pump the portal
/// dialog needs to answer, which is a deadlock rather than a stutter.
///
/// ## The callback is on the UI thread
///
/// It is delivered where an application can act on it: the same thread its
/// widgets live on. The platform does not promise that — SDL's callback arrives
/// on whichever thread the portal answered on — so every implementation here owes
/// the hop, and [io.github.digitalsmile.goldberry.Host#fileDialog] additionally
/// owes the repaint, because a choice that changes the model arrives with no
/// event behind it and nothing would otherwise ask for a frame.
///
/// Exactly once per [#show]. A dialog that is answered twice is a bug in the
/// platform layer and not a case a caller has to handle.
///
/// ## Absence is an ordinary answer
///
/// [#none()] is what a backend with no windowing reports, and it answers every
/// request with [FileChoice.Failed]. [#supported()] is the cheap question a menu
/// asks before offering an "Export…" item, and it is a different question from
/// "will this succeed" — a desktop can have dialogs and still refuse one.
///
/// Confined to the UI thread, like everything else in this package.
public interface FileDialogs {

    /// Whether this platform has file dialogs at all.
    ///
    /// Cheap, and answered from what the backend is rather than from a trial
    /// dialog.
    boolean supported();

    /// Puts a dialog up and returns at once.
    ///
    /// @param owner    the window it should be modal for, or null for none. Not
    ///                 every platform honours it, and none of them require it.
    /// @param spec     which dialog, and what to suggest
    /// @param onChoice told exactly once, on the UI thread
    void show(@Nullable BackendWindow owner, FileDialogSpec spec, Consumer<FileChoice> onChoice);

    /// A platform with no file dialogs.
    ///
    /// Answers **synchronously and with a failure**, not with a cancel: a caller
    /// whose export silently did nothing would look for the bug in its own code,
    /// and "there are no file dialogs here" is something it can say out loud.
    static FileDialogs none() {
        return new FileDialogs() {

            @Override
            public boolean supported() {
                return false;
            }

            @Override
            public void show(@Nullable BackendWindow owner, FileDialogSpec spec, Consumer<FileChoice> onChoice) {
                Objects.requireNonNull(spec, "spec");
                Objects.requireNonNull(onChoice, "onChoice");
                onChoice.accept(FileChoice.failed("this backend has no file dialogs"));
            }

            @Override
            public String toString() {
                return "FileDialogs[none]";
            }
        };
    }
}
