/*
 * The one place libgoldberry reads SDL's private state: on Wayland, the seat a
 * drag out starts from.
 *
 * A drag out on Wayland is `wl_data_device.start_drag`, and it needs two things
 * SDL keeps to itself: the seat's `wl_data_device` and the serial of the press
 * that began the implicit grab. Neither can be had another way without harm.
 * A second `wl_data_device` of the toolkit's own would do on weston, but mutter
 * unlinks a client's earlier device when it asks for another
 * (`get_data_device` in meta-wayland-data-device.c), so SDL's clipboard and its
 * drops would stop for the rest of the process. And the press's serial is heard
 * only by a `wl_pointer` that existed before the press.
 *
 * So this file is compiled against SDL's own sources, with SDL's private
 * include directories and definitions, and answers both from SDL's seat list.
 * It is compiled against the SDL revision the superbuild pins, so a layout
 * that changes upstream fails here at compile time rather than at run time.
 * Everything else about the drag -- the data source, the icon, the protocol --
 * is spoken from Java over libwayland-client.
 *
 * On every other platform, and in an SDL built without Wayland, the function
 * exists and answers false, because the export list is one list for all
 * platforms.
 */
#include "SDL_internal.h"

#ifdef SDL_VIDEO_DRIVER_WAYLAND
#include "video/SDL_sysvideo.h"
#include "video/wayland/SDL_waylanddatamanager.h"
#include "video/wayland/SDL_waylandevents_c.h"
#include "video/wayland/SDL_waylandvideo.h"
#include "video/wayland/SDL_waylandwindow.h"
#endif

#if defined(_WIN32)
#define GOLDBERRY_EXPORT __declspec(dllexport)
#else
#define GOLDBERRY_EXPORT __attribute__((visibility("default")))
#endif

/*
 * Where a drag out of `window` starts: SDL's `wl_data_device` for the seat
 * whose pointer is in the window, and that seat's last implicit-grab serial.
 *
 * The seat whose pointer focus is the window wins; among several, the one with
 * the latest serial. A window no seat's pointer is in falls back to the first
 * seat with a data device, which is what a touch drag on a single-seat desktop
 * needs.
 *
 * Returns false, and writes nothing, off Wayland or where SDL has no data
 * device (a compositor without `wl_data_device_manager`).
 */
GOLDBERRY_EXPORT bool goldberry_wayland_drag_origin(SDL_Window *window, void **data_device, Uint32 *serial) {
#ifdef SDL_VIDEO_DRIVER_WAYLAND
    if (!window || !data_device || !serial) {
        return false;
    }
    SDL_VideoDevice *video = SDL_GetVideoDevice();
    if (!video || !video->name || SDL_strcmp(video->name, "wayland") != 0) {
        return false;
    }
    SDL_WindowData *data = window->internal;
    if (!data || !data->waylandData) {
        return false;
    }
    SDL_WaylandSeat *chosen = NULL;
    bool chosenHasFocus = false;
    SDL_WaylandSeat *seat;
    wl_list_for_each (seat, &data->waylandData->seat_list, link) {
        if (!seat->data_device || !seat->data_device->data_device) {
            continue;
        }
        const bool focus = seat->pointer.focus == data;
        if (!chosen || (focus && !chosenHasFocus)
            || (focus == chosenHasFocus && seat->last_implicit_grab_serial > chosen->last_implicit_grab_serial)) {
            chosen = seat;
            chosenHasFocus = focus;
        }
    }
    if (!chosen) {
        return false;
    }
    *data_device = chosen->data_device->data_device;
    *serial = chosen->last_implicit_grab_serial;
    return true;
#else
    (void)window;
    (void)data_device;
    (void)serial;
    return false;
#endif
}
