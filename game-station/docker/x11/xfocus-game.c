#include <X11/Xlib.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static Window largest_viewable(Display *dpy, Window win, int depth, int *best_area) {
    Window found = 0;
    XWindowAttributes attr;

    if (depth > 14) {
        return 0;
    }
    /*
     * La raíz (depth 0) nunca es candidata: mide lo mismo que la ventana del
     * juego y, como gana el área estrictamente mayor, se quedaba con el foco.
     * Con foco en la raíz SDL recibe FocusIn con detail NotifyPointer, lo
     * ignora y la ventana nunca tiene SDL_WINDOW_INPUT_FOCUS (ioquake3 no
     * activa el mouse sin eso).
     */
    if (depth > 0
        && XGetWindowAttributes(dpy, win, &attr)
        && attr.map_state == IsViewable
        && attr.width >= 320
        && attr.height >= 240) {
        int area = attr.width * attr.height;
        if (area > *best_area) {
            *best_area = area;
            found = win;
        }
    }

    Window root_ret = 0;
    Window parent = 0;
    Window *kids = NULL;
    unsigned int n = 0;
    if (!XQueryTree(dpy, win, &root_ret, &parent, &kids, &n)) {
        return found;
    }
    for (unsigned int i = 0; i < n; i++) {
        Window sub = largest_viewable(dpy, kids[i], depth + 1, best_area);
        if (sub) {
            found = sub;
        }
    }
    if (kids) {
        XFree(kids);
    }
    return found;
}

static Window find_game(Display *dpy) {
    Window root = DefaultRootWindow(dpy);
    int best_area = 0;
    return largest_viewable(dpy, root, 0, &best_area);
}

static int fit_and_focus(Display *dpy) {
    Window target = find_game(dpy);
    if (!target) {
        return 1;
    }
    int screen = DefaultScreen(dpy);
    int w = DisplayWidth(dpy, screen);
    int h = DisplayHeight(dpy, screen);
    XWindowAttributes attr;
    /* Sin cambios no se reconfigura: cada ConfigureNotify llega al juego como resize. */
    if (!XGetWindowAttributes(dpy, target, &attr)
        || attr.x != 0 || attr.y != 0 || attr.width != w || attr.height != h) {
        XMoveResizeWindow(dpy, target, 0, 0, (unsigned int)w, (unsigned int)h);
    }
    XRaiseWindow(dpy, target);
    Window focused = 0;
    int revert = 0;
    XGetInputFocus(dpy, &focused, &revert);
    if (focused != target) {
        XSetInputFocus(dpy, target, RevertToPointerRoot, CurrentTime);
    }
    XFlush(dpy);
    return 0;
}

int main(int argc, char **argv) {
    const char *display_name = argc > 1 ? argv[1] : NULL;
    unsigned interval_ms = 350;
    int loop = 0;

    if (argc > 2) {
        if (argv[2][0] >= '0' && argv[2][0] <= '9') {
            int parsed = atoi(argv[2]);
            if (parsed > 50 && parsed < 60000) {
                interval_ms = (unsigned)parsed;
            }
            loop = 1;
        } else if (strcmp(argv[2], "loop") == 0) {
            loop = 1;
            if (argc > 3) {
                int parsed = atoi(argv[3]);
                if (parsed > 50 && parsed < 60000) {
                    interval_ms = (unsigned)parsed;
                }
            }
        }
    }

    Display *dpy = XOpenDisplay(display_name);
    if (!dpy) {
        return 1;
    }

    do {
        int rc = fit_and_focus(dpy);
        if (!loop) {
            XCloseDisplay(dpy);
            return rc;
        }
        if (rc == 0) {
            fprintf(stdout, "fit ok %dx%d\n", DisplayWidth(dpy, DefaultScreen(dpy)),
                    DisplayHeight(dpy, DefaultScreen(dpy)));
        }
        usleep(interval_ms * 1000U);
    } while (loop);

    XCloseDisplay(dpy);
    return 0;
}
