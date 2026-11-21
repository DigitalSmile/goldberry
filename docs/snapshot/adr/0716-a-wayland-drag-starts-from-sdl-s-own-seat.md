# ADR-0716: A Wayland drag starts from SDL's own seat

- **Status:** Accepted, as phase 3 of `docs/drag-out.md` (W3.9, Tessera's
  T-20). Amends ADR-0714's D2 on Wayland.
- **Date:** 2026-11-21
- **Relates to:** ADR-0714 (the drag out's decisions), ADR-0003 and ADR-0041
  (SDL as the only desktop backend)

## Context

A drag out on Wayland is `wl_data_device.start_drag(source, origin, icon,
serial)`. The plan had the toolkit bind a `wl_seat`, a `wl_pointer` and a
`wl_data_device` of its own on SDL's `wl_display`, the pointer made early so
that it would hear the press's serial.

Reading the compositors' sources showed that this breaks GNOME. When a
client asks mutter for a second data device on a seat
(`get_data_device` in `meta-wayland-data-device.c`), mutter unlinks the
client's earlier one from the seat's list and does not put it back. SDL's
data device would never hear another drop or another selection, so dropping
in and the clipboard would stop for the rest of the process. Weston keeps
both devices, which is why the plan's reading of the protocol looked safe.

SDL already has what `start_drag` needs: its data device for each seat, and
`last_implicit_grab_serial`, which it updates on every press for its own
`xdg_toplevel.move`. It publishes neither.

## Decision

1. **libgoldberry reads them from SDL.** One translation unit,
   `goldberry_sdl_wayland.c`, is compiled with SDL's own private include
   directories and definitions (taken from the `SDL3-static` target), and
   exports `goldberry_wayland_drag_origin(window, &data_device, &serial)`.
   It picks the seat whose pointer is in the window, the latest serial among
   several, the first seat with a data device otherwise. Native ABI 28. Off
   Wayland it is a stub that answers false, since the export list is one list.
2. **Everything else is spoken from Java** over libwayland-client, opened by
   its soname so it is the instance SDL loaded (`natives.wayland`): an event
   queue of the toolkit's own with its own registry, the `wl_data_source`,
   and the icon surface in a `wl_shm` buffer. `start_drag` is sent on SDL's
   own data device. Every event of every proxy arrives through
   `wl_proxy_add_dispatcher`, one upcall shape for the whole protocol. The
   queue is dispatched once a pump, outside SDL's.
3. **Over the application's own windows, SDL's drop events are the drag's
   position.** SDL's data device is the only target there, and a Wayland
   source is never told where the pointer is. While a drag out is in flight,
   the backend takes SDL's `DROP_*` events for itself: a position drives the
   router, and a "complete", which SDL sends both when the drag leaves and
   when it is let go, marks the window as where it may have been let go. A
   `target` heard by the source before the drop clears the mark, because
   compositors say `target` when the pointer moves to another surface. If
   `dnd_finished` comes with the mark still set, the drag landed in-app;
   otherwise another application took it.
4. **Version 3 of `wl_data_device_manager` or nothing.** Without
   `dnd_drop_performed` and `dnd_finished` the source cannot tell a drop from
   a drag that is still going. An older compositor is refused, and the drag
   stays in-app.

Rejected:

- A data device of the toolkit's own: correct on weston, and it breaks
  SDL's drops and clipboard on GNOME for the life of the process.
- A patch to SDL that adds a drag source: the change ADR-0714 names as the
  reason to withdraw this code, but one this project would carry alone until
  upstream takes it.
- A second connection: `start_drag` needs the origin surface and the serial
  of a press on that client.

## Consequences

- The C file depends on SDL's private structs (`SDL_Window.internal`,
  `SDL_WindowData.waylandData`, `SDL_VideoData.seat_list`, `SDL_WaylandSeat`).
  It is compiled against the SDL revision the superbuild pins, so an SDL
  upgrade that changes them fails the native build, not a user's drag.
- D2 on Wayland takes its feedback from SDL, not from the source. SDL takes
  only files and text, so an offer of other types alone (a PNG with no file,
  an application's own MIME type) gets no in-app feedback over the
  application's windows, and is cancelled when let go there. An image is
  offered as a file too, so pictures still work.
- SDL's own drop handler reads the offer for 14 ms before it gives up, since
  the source is the same process and is not dispatched meanwhile. The UI
  thread pauses that long when a drag is let go over one of its own windows.
- One more upcall shape (nineteen), registered for the native image.
- The picture is shown at the window's scale when that is a whole number,
  and at its pixel size on a fractional one, since a buffer's scale is an
  integer.
