package dev.goldberry.natives.desktop.dragout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;

/// The source side of XDND, version 5, as a state machine over an
/// [XdndTransport].
///
/// <https://freedesktop.org/wiki/Specifications/XDND/> is the protocol. What
/// it does here:
///
/// - **Over another application's window**: `XdndEnter` once, then
///   `XdndPosition` on each move, never more than one unanswered (a move made
///   while waiting is sent when `XdndStatus` comes). `XdndLeave` when the
///   pointer moves off it.
/// - **Over the application's own window**: no XDND at all. The listener hears
///   the point, and the window's own targets are the toolkit's.
/// - **The release**: over an own window it is that window's; over a target
///   that accepted, `XdndDrop`, and the outcome waits for `XdndFinished`; over
///   one whose answer has not come, it waits for that answer first; anywhere
///   else it is cancelled.
/// - **The data**: every `SelectionRequest` for `XdndSelection` is answered
///   from [DragData], `TARGETS` included, until the source is closed. Data too
///   large for one request is refused rather than sent incrementally.
///
/// UI thread only. Driven by [#handle] for each event and [#poll] to read the
/// pointer, both from SDL's X11 event hook.
final class XdndSource {

    private static final Logger LOG = Logs.of(XdndSource.class);

    /// The version this source speaks.
    static final int VERSION = 5;

    /// `XA_ATOM`, predefined.
    static final long XA_ATOM = 4;

    private enum Phase {
        /// The button is held; the pointer is followed.
        DRAGGING,
        /// Released over a target whose `XdndStatus` has not come.
        AWAITING_STATUS,
        /// `XdndDrop` sent; waiting for `XdndFinished`.
        DROPPED,
        /// The listener has heard the ending.
        ENDED
    }

    private final XdndTransport x;
    private final DragData data;
    private final Set<DragAction> actions;
    private final DragSourceListener listener;

    private final long selection;
    private final long enter;
    private final long position;
    private final long status;
    private final long leave;
    private final long drop;
    private final long finished;
    private final long targets;
    private final long copy;
    private final long move;
    private final long link;
    private final long[] typeAtoms;

    private Phase phase = Phase.DRAGGING;

    /// The other application's window the drag is over, or 0.
    private long target;

    private int targetVersion;
    private boolean waiting;
    private boolean pending;
    private boolean accepted;
    private long acceptedAction;

    /// The application's own window the drag is over, or 0.
    private long ownWindow;

    private XdndTransport.@Nullable Pointer last;

    XdndSource(XdndTransport transport, DragData data, Set<DragAction> actions, DragSourceListener listener) {
        this.x = Objects.requireNonNull(transport, "transport");
        this.data = Objects.requireNonNull(data, "data");
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("a drag allows at least one action");
        }
        this.actions = EnumSet.copyOf(actions);
        this.listener = Objects.requireNonNull(listener, "listener");
        selection = x.atom("XdndSelection");
        enter = x.atom("XdndEnter");
        position = x.atom("XdndPosition");
        status = x.atom("XdndStatus");
        leave = x.atom("XdndLeave");
        drop = x.atom("XdndDrop");
        finished = x.atom("XdndFinished");
        targets = x.atom("TARGETS");
        copy = x.atom("XdndActionCopy");
        move = x.atom("XdndActionMove");
        link = x.atom("XdndActionLink");
        var types = data.types();
        typeAtoms = types.stream().mapToLong(x::atom).toArray();
    }

    /// Takes the selection and looks at the pointer for the first time.
    void start() {
        x.ownSelection(selection);
        if (typeAtoms.length > 3) {
            x.typeList(typeAtoms);
        }
        poll();
    }

    /// Whether the listener has heard how the drag ended.
    boolean isEnded() {
        return phase == Phase.ENDED;
    }

    /// Whether the drag is waiting for a target's answer, which is what a
    /// timeout is for.
    boolean isWaiting() {
        return phase == Phase.AWAITING_STATUS || phase == Phase.DROPPED;
    }

    /// Acts on `event`, and answers whether it was this source's (so SDL
    /// should not see it).
    boolean handle(XdndEvent event) {
        return switch (event) {
            case XdndEvent.ClientMessage(var window, var type, var payload) -> {
                if (window != x.sourceWindow()) {
                    yield false;
                }
                if (type == status) {
                    onStatus(payload);
                } else if (type == finished) {
                    onFinished(payload);
                }
                yield true;
            }
            case XdndEvent.SelectionRequest request -> {
                if (request.owner() != x.sourceWindow() || request.selection() != selection) {
                    yield false;
                }
                answer(request);
                yield true;
            }
            case XdndEvent.KeyPress(var keycode) -> {
                if (phase == Phase.DRAGGING && keycode == x.escapeKeycode()) {
                    leaveTarget();
                    end(listener::cancelled);
                    yield true;
                }
                yield false;
            }
            case XdndEvent.Other _ -> false;
        };
    }

    /// Reads the pointer, and follows it while the button is held.
    void poll() {
        if (phase != Phase.DRAGGING) {
            return;
        }
        var pointer = x.pointer();
        if (!pointer.primaryHeld()) {
            release(pointer);
            return;
        }
        if (pointer.equals(last)) {
            return;
        }
        last = pointer;
        follow(pointer);
    }

    /// The target took too long to answer: a drop sent and never finished is
    /// taken as delivered without an action, an answer never given as
    /// nothing taken.
    void timeOut() {
        switch (phase) {
            case DROPPED -> end(() -> listener.delivered(null));
            case AWAITING_STATUS -> {
                leaveTarget();
                end(listener::cancelled);
            }
            default -> {}
        }
    }

    private void follow(XdndTransport.Pointer pointer) {
        var found = x.targetAt(pointer.rootX(), pointer.rootY());
        if (found != null && found.own()) {
            leaveTarget();
            ownWindow = found.window();
            listener.over(found.window(), found.x(), found.y());
            return;
        }
        if (ownWindow != 0) {
            ownWindow = 0;
            listener.outside();
        }
        var window = found == null ? 0 : found.window();
        if (window != target) {
            leaveTarget();
            if (found != null) {
                enter(found);
            }
        }
        if (target != 0) {
            position(pointer);
        }
    }

    private void enter(XdndTransport.Target found) {
        target = found.window();
        targetVersion = Math.min(VERSION, found.version());
        accepted = false;
        waiting = false;
        pending = false;
        var more = typeAtoms.length > 3 ? 1L : 0L;
        x.send(target, enter, new long[] {
            x.sourceWindow(),
            ((long) targetVersion << 24) | more,
            typeAtoms.length > 0 ? typeAtoms[0] : 0,
            typeAtoms.length > 1 ? typeAtoms[1] : 0,
            typeAtoms.length > 2 ? typeAtoms[2] : 0
        });
        x.flush();
    }

    private void position(XdndTransport.Pointer pointer) {
        if (waiting) {
            pending = true;
            return;
        }
        x.send(target, position, new long[] {
            x.sourceWindow(),
            0,
            ((long) (pointer.rootX() & 0xffff) << 16) | (pointer.rootY() & 0xffff),
            0,
            actionAtom(pointer)
        });
        x.flush();
        waiting = true;
        pending = false;
    }

    private void leaveTarget() {
        if (target == 0) {
            return;
        }
        x.send(target, leave, new long[] {x.sourceWindow(), 0, 0, 0, 0});
        x.flush();
        target = 0;
        accepted = false;
        waiting = false;
        pending = false;
    }

    private void onStatus(long[] payload) {
        if (payload[0] != target || target == 0) {
            return;
        }
        waiting = false;
        accepted = (payload[1] & 1) != 0;
        acceptedAction = payload[4];
        if (phase == Phase.AWAITING_STATUS) {
            if (accepted) {
                sendDrop();
            } else {
                leaveTarget();
                end(listener::cancelled);
            }
            return;
        }
        var pointer = last;
        if (pending && pointer != null) {
            position(pointer);
        }
    }

    private void release(XdndTransport.Pointer pointer) {
        var found = x.targetAt(pointer.rootX(), pointer.rootY());
        if (found != null && found.own()) {
            leaveTarget();
            end(() -> listener.releasedOver(found.window(), found.x(), found.y()));
            return;
        }
        if (target == 0) {
            end(listener::cancelled);
            return;
        }
        if (waiting) {
            // The target has not answered the last position. Its answer
            // decides, as the protocol asks a source to wait for it.
            phase = Phase.AWAITING_STATUS;
            return;
        }
        if (accepted) {
            sendDrop();
        } else {
            leaveTarget();
            end(listener::cancelled);
        }
    }

    private void sendDrop() {
        x.send(target, drop, new long[] {x.sourceWindow(), 0, 0, 0, 0});
        x.flush();
        phase = Phase.DROPPED;
    }

    private void onFinished(long[] payload) {
        if (phase != Phase.DROPPED || payload[0] != target) {
            return;
        }
        // Version 5 says whether the drop was taken and what was done; before
        // it a finish means taken, with the action the last status named.
        var taken = targetVersion < 5 || (payload[1] & 1) != 0;
        var action = targetVersion < 5 ? acceptedAction : payload[2];
        target = 0;
        if (taken) {
            var done = actionOf(action);
            end(() -> listener.delivered(done));
        } else {
            end(listener::cancelled);
        }
    }

    private void answer(XdndEvent.SelectionRequest request) {
        var property = request.property() == 0 ? request.target() : request.property();
        if (request.target() == targets) {
            var list = new long[typeAtoms.length + 1];
            System.arraycopy(typeAtoms, 0, list, 0, typeAtoms.length);
            list[typeAtoms.length] = targets;
            x.writeProperty(request.requestor(), property, XA_ATOM, 32, longs(list));
            x.notifySelection(request.requestor(), request.selection(), request.target(), property, request.time());
            x.flush();
            return;
        }
        var name = x.atomName(request.target());
        var bytes = name == null ? null : bytesFor(name);
        if (bytes == null || bytes.length > x.maxPropertyBytes()) {
            if (bytes != null) {
                LOG.debug("{} bytes of {} are more than one X request takes; refused", bytes.length, name);
            }
            x.notifySelection(request.requestor(), request.selection(), request.target(), 0, request.time());
        } else {
            x.writeProperty(request.requestor(), property, request.target(), 8, bytes);
            x.notifySelection(request.requestor(), request.selection(), request.target(), property, request.time());
        }
        x.flush();
    }

    private byte @Nullable [] bytesFor(String name) {
        try {
            return data.bytes(name);
        } catch (RuntimeException e) {
            LOG.warn("a drag's {} failed to make its bytes; refused", name, e);
            return null;
        }
    }

    /// The action the user is asking for: Shift moves and Shift with Control
    /// links, as file managers read them, where the offer allows it; a copy
    /// otherwise, or the first the offer allows.
    private long actionAtom(XdndTransport.Pointer pointer) {
        if (pointer.shift() && pointer.control() && actions.contains(DragAction.LINK)) {
            return link;
        }
        if (pointer.shift() && actions.contains(DragAction.MOVE)) {
            return move;
        }
        if (actions.contains(DragAction.COPY)) {
            return copy;
        }
        return actions.contains(DragAction.MOVE) ? move : link;
    }

    private @Nullable DragAction actionOf(long atom) {
        if (atom == copy) {
            return DragAction.COPY;
        }
        if (atom == move) {
            return DragAction.MOVE;
        }
        if (atom == link) {
            return DragAction.LINK;
        }
        return null;
    }

    private void end(Runnable telling) {
        if (phase == Phase.ENDED) {
            return;
        }
        phase = Phase.ENDED;
        ownWindow = 0;
        telling.run();
    }

    /// `values` as the native-order bytes of C longs, which is what a 32-bit
    /// format property is handed to Xlib as.
    static byte[] longs(long[] values) {
        var buffer = ByteBuffer.allocate(values.length * Long.BYTES).order(ByteOrder.nativeOrder());
        for (var value : values) {
            buffer.putLong(value);
        }
        return buffer.array();
    }
}
