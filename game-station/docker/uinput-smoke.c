/*
 * Smoke test de /dev/uinput — compilar en host o vía Dockerfile de game-station.
 * Se instala en la imagen como /usr/local/bin/cloudgame-uinput-smoke.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define DEVICE_NAME "Cloud Game Uinput Smoke"
#define UINPUT_PATH "/dev/uinput"

static int fail(int code, const char *msg)
{
    fprintf(stderr, "uinput-smoke: %s", msg);
    if (errno)
        fprintf(stderr, " (%s)", strerror(errno));
    fputc('\n', stderr);
    return code;
}

static int find_event_node(char *out, size_t out_len)
{
    FILE *f = fopen("/proc/bus/input/devices", "r");
    if (!f)
        return -1;

    char line[512];
    int in_block = 0;
    out[0] = '\0';

    while (fgets(line, sizeof line, f)) {
        if (line[0] == '\n') {
            in_block = 0;
            continue;
        }
        if (strstr(line, "N: Name=\"" DEVICE_NAME "\""))
            in_block = 1;
        if (in_block && strncmp(line, "H: Handlers=", 12) == 0) {
            const char *ev = strstr(line, "event");
            if (ev) {
                int n = 0;
                if (sscanf(ev, "event%d", &n) == 1) {
                    snprintf(out, out_len, "/dev/input/event%d", n);
                    fclose(f);
                    return 0;
                }
            }
        }
    }
    fclose(f);
    return -1;
}

static int emit_key(int fd, int code, int value)
{
    struct input_event ev;
    memset(&ev, 0, sizeof ev);
    ev.type = EV_KEY;
    ev.code = (unsigned short)code;
    ev.value = value;
    if (write(fd, &ev, sizeof ev) != (ssize_t)sizeof ev)
        return -1;

    memset(&ev, 0, sizeof ev);
    ev.type = EV_SYN;
    ev.code = SYN_REPORT;
    ev.value = 0;
    if (write(fd, &ev, sizeof ev) != (ssize_t)sizeof ev)
        return -1;
    return 0;
}

int main(void)
{
    int ufd = open(UINPUT_PATH, O_WRONLY | O_NONBLOCK);
    if (ufd < 0)
        return fail(2, "no se pudo abrir " UINPUT_PATH);

    if (ioctl(ufd, UI_SET_EVBIT, EV_KEY) < 0) {
        close(ufd);
        return fail(3, "UI_SET_EVBIT EV_KEY");
    }
    if (ioctl(ufd, UI_SET_KEYBIT, BTN_SOUTH) < 0) {
        close(ufd);
        return fail(3, "UI_SET_KEYBIT BTN_SOUTH");
    }

    struct uinput_user_dev uidev;
    memset(&uidev, 0, sizeof uidev);
    snprintf(uidev.name, UINPUT_MAX_NAME_SIZE, "%s", DEVICE_NAME);
    uidev.id.bustype = BUS_USB;
    uidev.id.vendor = 0x045e;
    uidev.id.product = 0x028e;
    uidev.id.version = 0x0110;

    if (write(ufd, &uidev, sizeof uidev) != (ssize_t)sizeof uidev) {
        close(ufd);
        return fail(3, "write uinput_user_dev");
    }
    if (ioctl(ufd, UI_DEV_CREATE) < 0) {
        close(ufd);
        return fail(3, "UI_DEV_CREATE");
    }

    char event_path[64];
    int found = -1;
    for (int i = 0; i < 50; i++) {
        if (find_event_node(event_path, sizeof event_path) == 0) {
            found = 0;
            break;
        }
        usleep(20 * 1000);
    }
    if (found < 0) {
        ioctl(ufd, UI_DEV_DESTROY);
        close(ufd);
        return fail(4, "no apareció nodo event* en /proc/bus/input/devices");
    }

    int efd = open(event_path, O_RDONLY | O_NONBLOCK);
    if (efd < 0) {
        ioctl(ufd, UI_DEV_DESTROY);
        close(ufd);
        return fail(4, "open event node");
    }

    if (emit_key(ufd, BTN_SOUTH, 1) < 0 || emit_key(ufd, BTN_SOUTH, 0) < 0) {
        close(efd);
        ioctl(ufd, UI_DEV_DESTROY);
        close(ufd);
        return fail(3, "escribir input_event");
    }

    int saw_press = 0;
    struct pollfd pfd = {.fd = efd, .events = POLLIN};
    for (int tries = 0; tries < 100 && !saw_press; tries++) {
        int pr = poll(&pfd, 1, 50);
        if (pr <= 0)
            continue;
        struct input_event ev;
        while (read(efd, &ev, sizeof ev) == (ssize_t)sizeof ev) {
            if (ev.type == EV_KEY && ev.code == BTN_SOUTH && ev.value == 1)
                saw_press = 1;
        }
    }

    close(efd);
    ioctl(ufd, UI_DEV_DESTROY);
    close(ufd);

    if (!saw_press) {
        char msg[128];
        snprintf(msg, sizeof msg, "no se leyó EV_KEY BTN_SOUTH en %s", event_path);
        return fail(5, msg);
    }

    printf("ok: pad virtual → %s (BTN_SOUTH 1/0)\n", event_path);
    return 0;
}
