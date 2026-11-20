#define _GNU_SOURCE
#include <dlfcn.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

#define SDL_EVENT_SIZE 56
#define SDL_WINDOWEVENT 0x200u
#define SDL_WINDOWEVENT_FOCUS_LOST 13
#define SDL_WINDOWEVENT_FOCUS_GAINED 12

#define SDL_MOUSEMOTION 0x400u
/* SDL_eventaction */
#define SDL_ADDEVENT 0
#define SDL_PEEKEVENT 1
#define SDL_GETEVENT 2
/* Llamadas a SDL_GetRelativeMouseState para dar por hecho que el juego lee el giro por ahí. */
#define STATE_READER_CALLS 10

#define SDL_JOYAXISMOTION 0x600u
#define SDL_JOYBUTTONDOWN 0x601u
#define SDL_JOYBUTTONUP 0x602u
#define SDL_JOYHATMOTION 0x603u
#define SDL_JOYDEVICEADDED 0x605u

#define SDL_PRESSED 1
#define SDL_RELEASED 0

#define SDL_HAT_CENTERED 0x00
#define SDL_HAT_UP 0x01
#define SDL_HAT_RIGHT 0x02
#define SDL_HAT_DOWN 0x04
#define SDL_HAT_LEFT 0x08
#define SDL_HAT_RIGHTUP 0x03
#define SDL_HAT_RIGHTDOWN 0x06
#define SDL_HAT_LEFTUP 0x09
#define SDL_HAT_LEFTDOWN 0x0c

#define SDL_CONTROLLER_AXIS_MAX 6
#define VIRT_AXES 6
#define VIRT_BUTTONS 11
#define VIRT_HATS 1
#define FAKE_INSTANCE_ID 90210
#define MAX_VPADS 4
#define PAD_SLOT0 64
#define PAD_SLOT_STRIDE 32

struct Look {
    int lock;
    int dx;
    int dy;
    int buttons;
};

#define PAD_MAGIC 0x50414430u

static int pad_off_magic(int slot) { return PAD_SLOT0 + slot * PAD_SLOT_STRIDE; }
static int pad_off_buttons(int slot) { return pad_off_magic(slot) + 4; }
static int pad_off_axes(int slot) { return pad_off_magic(slot) + 8; }
static int pad_off_active(int slot) { return pad_off_magic(slot) + 20; }

#define EVENT_Q 128

typedef struct {
    unsigned char bytes[SDL_EVENT_SIZE];
} QueuedEvent;

static struct Look *look;
static int look_ready;

static uint32_t (*real_relative)(int *, int *);
static uint32_t (*real_mouse_state)(int *, int *);
static void *(*real_mouse_focus)(void);
static void *(*real_keyboard_focus)(void);
static uint32_t (*real_window_id)(void *);
static uint32_t (*real_ticks)(void);
static int (*real_poll)(void *);
static int (*real_peep)(void *, int, int, uint32_t, uint32_t);
static int (*real_num_joysticks)(void);
static int (*real_is_game_controller)(int);
static void *(*real_gc_open)(int);
static void *(*real_joy_open)(int);
static void (*real_gc_close)(void *);
static void (*real_joy_close)(void *);
static char *(*real_gc_mapping)(void *);
static const char *(*real_joy_name)(void *);
static const char *(*real_gc_name)(void *);
static void *(*real_gc_get_joy)(void *);
static int (*real_gc_player)(void *);
static int32_t (*real_joy_instance_id)(void *);
static int (*real_joy_num_axes)(void *);
static int (*real_joy_num_buttons)(void *);
static int (*real_joy_num_hats)(void *);
static int16_t (*real_joy_axis)(void *, int);
static uint8_t (*real_gc_button)(void *, int);
static int16_t (*real_gc_axis)(void *, int);

static char fake_gc[MAX_VPADS][64];
static char fake_joy[MAX_VPADS][64];

static int16_t pad_axes[MAX_VPADS][VIRT_AXES];
static uint32_t pad_buttons[MAX_VPADS];
static int pad_active[MAX_VPADS];

static int16_t prev_axes[MAX_VPADS][VIRT_AXES];
static uint32_t prev_buttons[MAX_VPADS];
static uint8_t prev_hat[MAX_VPADS];
static int prev_inited[MAX_VPADS];
static int device_added[MAX_VPADS];

static int state_reader_calls;
static int logged_look_mode;

static QueuedEvent event_q[EVENT_Q];
static int eq_head;
static int eq_tail;

static int vpad_count(void) {
    const char *env = getenv("CLOUDGAME_VIRTUAL_PADS");
    if (!env) {
        return 0;
    }
    int n = atoi(env);
    if (n < 0) {
        n = 0;
    }
    if (n > MAX_VPADS) {
        n = MAX_VPADS;
    }
    return n;
}

static int fake_slot_gc(void *gc) {
    for (int i = 0; i < MAX_VPADS; i++) {
        if (gc == (void *)fake_gc[i]) {
            return i;
        }
    }
    return -1;
}

static int fake_slot_joy(void *joy) {
    for (int i = 0; i < MAX_VPADS; i++) {
        if (joy == (void *)fake_joy[i]) {
            return i;
        }
    }
    return -1;
}

static int is_fake_joy(void *joy) {
    return fake_slot_joy(joy) >= 0;
}

static int is_fake_gc(void *gc) {
    return fake_slot_gc(gc) >= 0;
}

static int virtual_index(void) {
    int n = 0;
    if (!real_num_joysticks) {
        real_num_joysticks = dlsym(RTLD_NEXT, "SDL_NumJoysticks");
    }
    if (real_num_joysticks) {
        n = real_num_joysticks();
        if (n < 0) {
            n = 0;
        }
    }
    return n;
}

static void open_map(void) {
    int fd;
    if (look_ready) {
        return;
    }
    fd = open("/tmp/game-input", O_RDWR);
    if (fd < 0) {
        return;
    }
    look = mmap(NULL, 4096, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    close(fd);
    if (look == MAP_FAILED) {
        look = NULL;
        return;
    }
    look_ready = 1;
}

static int16_t wire_to_sdl_axis(const int16_t *wire, int sdl_index) {
    switch (sdl_index) {
    case 0:
        return wire[0];
    case 1:
        return wire[1];
    case 2:
        return wire[4];
    case 3:
        return wire[2];
    case 4:
        return wire[3];
    case 5:
        return wire[5];
    default:
        return 0;
    }
}

static void refresh_pad(int slot) {
    volatile uint32_t *words;
    volatile int16_t *axes;
    int16_t wire[VIRT_AXES];
    if (slot < 0 || slot >= MAX_VPADS) {
        return;
    }
    open_map();
    if (!look) {
        pad_active[slot] = 0;
        pad_buttons[slot] = 0;
        memset(pad_axes[slot], 0, sizeof(pad_axes[slot]));
        return;
    }
    words = (volatile uint32_t *)((unsigned char *)look + pad_off_magic(slot));
    if (words[0] != PAD_MAGIC) {
        pad_active[slot] = 0;
        pad_buttons[slot] = 0;
        memset(pad_axes[slot], 0, sizeof(pad_axes[slot]));
        return;
    }
    pad_buttons[slot] = *(volatile uint32_t *)((unsigned char *)look + pad_off_buttons(slot));
    axes = (volatile int16_t *)((unsigned char *)look + pad_off_axes(slot));
    for (int i = 0; i < VIRT_AXES; i++) {
        wire[i] = axes[i];
    }
    for (int i = 0; i < VIRT_AXES; i++) {
        pad_axes[slot][i] = wire_to_sdl_axis(wire, i);
    }
    pad_active[slot] =
            *(volatile int16_t *)((unsigned char *)look + pad_off_active(slot)) != 0;
}

static void enqueue_event(unsigned char *ev) {
    int next = (eq_tail + 1) % EVENT_Q;
    if (next == eq_head) {
        return;
    }
    memcpy(event_q[eq_tail].bytes, ev, SDL_EVENT_SIZE);
    eq_tail = next;
}

static int dequeue_event(void *out) {
    if (eq_head == eq_tail) {
        return 0;
    }
    memcpy(out, event_q[eq_head].bytes, SDL_EVENT_SIZE);
    eq_head = (eq_head + 1) % EVENT_Q;
    return 1;
}

static void put_u32(unsigned char *ev, int off, uint32_t v) {
    memcpy(ev + off, &v, 4);
}

static void put_u8(unsigned char *ev, int off, uint8_t v) {
    ev[off] = v;
}

static void put_i16(unsigned char *ev, int off, int16_t v) {
    memcpy(ev + off, &v, 2);
}

static void put_i32(unsigned char *ev, int off, int32_t v) {
    memcpy(ev + off, &v, 4);
}

static uint8_t dpad_hat(uint32_t buttons) {
    int up = (buttons >> 12) & 1;
    int down = (buttons >> 13) & 1;
    int left = (buttons >> 14) & 1;
    int right = (buttons >> 15) & 1;
    if (up && right) {
        return SDL_HAT_RIGHTUP;
    }
    if (up && left) {
        return SDL_HAT_LEFTUP;
    }
    if (down && right) {
        return SDL_HAT_RIGHTDOWN;
    }
    if (down && left) {
        return SDL_HAT_LEFTDOWN;
    }
    if (up) {
        return SDL_HAT_UP;
    }
    if (down) {
        return SDL_HAT_DOWN;
    }
    if (left) {
        return SDL_HAT_LEFT;
    }
    if (right) {
        return SDL_HAT_RIGHT;
    }
    return SDL_HAT_CENTERED;
}

/*
 * Bit del wire (A0 B1 X2 Y3 LB4 RB5 View8 Menu9 LS10 RS11 d-pad12..15
 * Guide16) para cada botón joystick del string SDL (SdlMapping):
 * a:b0 b:b1 x:b2 y:b3 leftshoulder:b4 rightshoulder:b5 back:b6 start:b7
 * guide:b8 leftstick:b9 rightstick:b10.
 */
static const int JOY_BUTTON_WIRE[VIRT_BUTTONS] = {0, 1, 2, 3, 4, 5, 8, 9, 16, 10, 11};

/* Bit del wire para cada SDL_GameControllerButton (A..DPAD_RIGHT). */
static const int GC_BUTTON_WIRE[] = {0, 1, 2, 3, 8, 16, 9, 10, 11, 4, 5, 12, 13, 14, 15};
#define GC_BUTTONS ((int)(sizeof(GC_BUTTON_WIRE) / sizeof(GC_BUTTON_WIRE[0])))

static int joy_button_down(uint32_t buttons, int b) {
    return (buttons >> JOY_BUTTON_WIRE[b]) & 1u;
}

static int32_t fake_instance_id(int slot) {
    return FAKE_INSTANCE_ID + slot;
}

static void queue_axis(int slot, int axis, int16_t value) {
    unsigned char ev[SDL_EVENT_SIZE];
    memset(ev, 0, sizeof(ev));
    put_u32(ev, 0, SDL_JOYAXISMOTION);
    put_i32(ev, 8, fake_instance_id(slot));
    put_u8(ev, 12, (uint8_t)axis);
    /* SDL_JoyAxisEvent: axis(12) + padding1..3, value en 16. */
    put_i16(ev, 16, value);
    enqueue_event(ev);
}

static void queue_button(int slot, int button, int pressed) {
    unsigned char ev[SDL_EVENT_SIZE];
    memset(ev, 0, sizeof(ev));
    put_u32(ev, 0, pressed ? SDL_JOYBUTTONDOWN : SDL_JOYBUTTONUP);
    put_i32(ev, 8, fake_instance_id(slot));
    put_u8(ev, 12, (uint8_t)button);
    put_u8(ev, 13, pressed ? SDL_PRESSED : SDL_RELEASED);
    enqueue_event(ev);
}

static void queue_device_added(int joy_index) {
    unsigned char ev[SDL_EVENT_SIZE];
    memset(ev, 0, sizeof(ev));
    put_u32(ev, 0, SDL_JOYDEVICEADDED);
    put_i32(ev, 8, joy_index);
    enqueue_event(ev);
}

static void queue_hat(int slot, uint8_t hat) {
    unsigned char ev[SDL_EVENT_SIZE];
    memset(ev, 0, sizeof(ev));
    put_u32(ev, 0, SDL_JOYHATMOTION);
    put_i32(ev, 8, fake_instance_id(slot));
    put_u8(ev, 12, 0);
    put_u8(ev, 13, hat);
    enqueue_event(ev);
}

static void sync_pad_slot(int slot, int joy_index) {
    refresh_pad(slot);
    if (!pad_active[slot]) {
        if (prev_inited[slot]) {
            memset(prev_axes[slot], 0, sizeof(prev_axes[slot]));
            prev_buttons[slot] = 0;
            prev_hat[slot] = SDL_HAT_CENTERED;
            prev_inited[slot] = 0;
            device_added[slot] = 0;
        }
        return;
    }
    if (!device_added[slot]) {
        queue_device_added(joy_index);
        device_added[slot] = 1;
    }
    if (!prev_inited[slot]) {
        for (int i = 0; i < VIRT_AXES; i++) {
            if (pad_axes[slot][i] != 0) {
                queue_axis(slot, i, pad_axes[slot][i]);
            }
            prev_axes[slot][i] = pad_axes[slot][i];
        }
        for (int b = 0; b < VIRT_BUTTONS; b++) {
            if (joy_button_down(pad_buttons[slot], b)) {
                queue_button(slot, b, 1);
            }
        }
        prev_hat[slot] = dpad_hat(pad_buttons[slot]);
        if (prev_hat[slot] != SDL_HAT_CENTERED) {
            queue_hat(slot, prev_hat[slot]);
        }
        prev_buttons[slot] = pad_buttons[slot];
        prev_inited[slot] = 1;
        return;
    }
    for (int i = 0; i < VIRT_AXES; i++) {
        if (pad_axes[slot][i] != prev_axes[slot][i]) {
            queue_axis(slot, i, pad_axes[slot][i]);
            prev_axes[slot][i] = pad_axes[slot][i];
        }
    }
    for (int b = 0; b < VIRT_BUTTONS; b++) {
        int down = joy_button_down(pad_buttons[slot], b);
        if (down != joy_button_down(prev_buttons[slot], b)) {
            queue_button(slot, b, down);
        }
    }
    uint8_t hat = dpad_hat(pad_buttons[slot]);
    if (hat != prev_hat[slot]) {
        queue_hat(slot, hat);
        prev_hat[slot] = hat;
    }
    prev_buttons[slot] = pad_buttons[slot];
}

static void sync_pad_events(void) {
    int base = virtual_index();
    int n = vpad_count();
    for (int s = 0; s < n; s++) {
        sync_pad_slot(s, base + s);
    }
}

static void take(int *dx, int *dy) {
    open_map();
    if (!look) {
        *dx = 0;
        *dy = 0;
        return;
    }
    *dx = __atomic_exchange_n(&look->dx, 0, __ATOMIC_ACQ_REL);
    *dy = __atomic_exchange_n(&look->dy, 0, __ATOMIC_ACQ_REL);
}

static uint32_t relative_state(int *x, int *y) {
    int dx = 0;
    int dy = 0;
    int rx = 0;
    int ry = 0;
    uint32_t buttons = 0;
    if (!real_relative) {
        real_relative = dlsym(RTLD_NEXT, "SDL_GetRelativeMouseState");
    }
    if (real_relative) {
        buttons = real_relative(&rx, &ry);
    }
    take(&dx, &dy);
    if (x) {
        *x = rx + dx;
    }
    if (y) {
        *y = ry + dy;
    }
    return buttons;
}

uint32_t SDL_GetRelativeMouseState(int *x, int *y) {
    if (state_reader_calls < STATE_READER_CALLS) {
        state_reader_calls++;
        if (state_reader_calls == STATE_READER_CALLS && !logged_look_mode) {
            logged_look_mode = 1;
            fprintf(stderr, "[gameinput] mouse look via SDL_GetRelativeMouseState\n");
        }
    }
    return relative_state(x, y);
}

static void *sym(const char *name) {
    return dlsym(RTLD_NEXT, name);
}

/*
 * Juegos que leen el giro de SDL_MOUSEMOTION (ioquake3, Cube 2...) y no de
 * SDL_GetRelativeMouseState: los deltas de /tmp/game-input salen como evento.
 * Si el juego consulta el estado relativo en cada frame, se deja de sintetizar
 * para no robarle los deltas (DarkPlaces ignora los eventos de movimiento).
 *
 * El evento va a la cola real de SDL (SDL_ADDEVENT) y no a event_q: Cube 2
 * no llama SDL_PollEvent, lee con SDL_PumpEvents + SDL_PeepEvents(GETEVENT).
 */
static void queue_look_motion(void) {
    unsigned char ev[SDL_EVENT_SIZE];
    void *win = NULL;
    int dx = 0;
    int dy = 0;
    int mx = 0;
    int my = 0;
    uint32_t buttons = 0;
    if (state_reader_calls >= STATE_READER_CALLS) {
        return;
    }
    take(&dx, &dy);
    if (dx == 0 && dy == 0) {
        return;
    }
    if (!logged_look_mode) {
        logged_look_mode = 1;
        fprintf(stderr, "[gameinput] mouse look via SDL_MOUSEMOTION dx=%d dy=%d\n", dx, dy);
    }
    if (!real_mouse_state) {
        real_mouse_state = sym("SDL_GetMouseState");
    }
    if (!real_mouse_focus) {
        real_mouse_focus = sym("SDL_GetMouseFocus");
    }
    if (!real_keyboard_focus) {
        real_keyboard_focus = sym("SDL_GetKeyboardFocus");
    }
    if (!real_window_id) {
        real_window_id = sym("SDL_GetWindowID");
    }
    if (!real_ticks) {
        real_ticks = sym("SDL_GetTicks");
    }
    if (real_mouse_state) {
        buttons = real_mouse_state(&mx, &my);
    }
    if (real_mouse_focus) {
        win = real_mouse_focus();
    }
    if (!win && real_keyboard_focus) {
        win = real_keyboard_focus();
    }
    memset(ev, 0, sizeof(ev));
    /* SDL_MouseMotionEvent: type, timestamp, windowID, which, state, x, y, xrel, yrel. */
    put_u32(ev, 0, SDL_MOUSEMOTION);
    put_u32(ev, 4, real_ticks ? real_ticks() : 0);
    put_u32(ev, 8, win && real_window_id ? real_window_id(win) : 0);
    put_u32(ev, 12, 0);
    put_u32(ev, 16, buttons);
    put_i32(ev, 20, mx);
    put_i32(ev, 24, my);
    put_i32(ev, 28, dx);
    put_i32(ev, 32, dy);
    if (!real_peep) {
        real_peep = sym("SDL_PeepEvents");
    }
    if (!real_peep || real_peep(ev, 1, SDL_ADDEVENT, 0, 0) != 1) {
        enqueue_event(ev);
    }
}

static void keep_focus(void *event) {
    uint32_t type = 0;
    uint8_t code = 0;
    memcpy(&type, event, sizeof(type));
    if (type != SDL_WINDOWEVENT) {
        return;
    }
    memcpy(&code, (unsigned char *)event + 12, 1);
    if (code == SDL_WINDOWEVENT_FOCUS_LOST) {
        code = SDL_WINDOWEVENT_FOCUS_GAINED;
        memcpy((unsigned char *)event + 12, &code, 1);
    }
}

int SDL_PollEvent(void *event) {
    if (!real_poll) {
        real_poll = dlsym(RTLD_NEXT, "SDL_PollEvent");
    }
    if (!event || !real_poll) {
        return 0;
    }
    sync_pad_events();
    queue_look_motion();
    if (dequeue_event(event)) {
        return 1;
    }
    {
        int pending = real_poll(event);
        if (pending) {
            keep_focus(event);
        }
        return pending;
    }
}

int SDL_PeepEvents(void *events, int numevents, int action, uint32_t minType, uint32_t maxType) {
    if (!real_peep) {
        real_peep = dlsym(RTLD_NEXT, "SDL_PeepEvents");
    }
    if (!real_peep) {
        return -1;
    }
    if ((action == SDL_GETEVENT || action == SDL_PEEKEVENT)
            && minType <= SDL_MOUSEMOTION && maxType >= SDL_MOUSEMOTION) {
        queue_look_motion();
    }
    return real_peep(events, numevents, action, minType, maxType);
}

int SDL_NumJoysticks(void) {
    return virtual_index() + vpad_count();
}

int SDL_IsGameController(int device_index) {
    int base = virtual_index();
    if (device_index >= base && device_index < base + vpad_count()) {
        return 1;
    }
    if (!real_is_game_controller) {
        real_is_game_controller = dlsym(RTLD_NEXT, "SDL_IsGameController");
    }
    if (real_is_game_controller) {
        return real_is_game_controller(device_index);
    }
    return 0;
}

void *SDL_GameControllerOpen(int device_index) {
    int base = virtual_index();
    if (device_index >= base && device_index < base + vpad_count()) {
        return (void *)fake_gc[device_index - base];
    }
    if (!real_gc_open) {
        real_gc_open = dlsym(RTLD_NEXT, "SDL_GameControllerOpen");
    }
    if (real_gc_open) {
        return real_gc_open(device_index);
    }
    return NULL;
}

void *SDL_JoystickOpen(int device_index) {
    int base = virtual_index();
    if (device_index >= base && device_index < base + vpad_count()) {
        return (void *)fake_joy[device_index - base];
    }
    if (!real_joy_open) {
        real_joy_open = dlsym(RTLD_NEXT, "SDL_JoystickOpen");
    }
    if (real_joy_open) {
        return real_joy_open(device_index);
    }
    return NULL;
}

void SDL_GameControllerClose(void *gamecontroller) {
    if (is_fake_gc(gamecontroller)) {
        return;
    }
    if (!real_gc_close) {
        real_gc_close = dlsym(RTLD_NEXT, "SDL_GameControllerClose");
    }
    if (real_gc_close) {
        real_gc_close(gamecontroller);
    }
}

void SDL_JoystickClose(void *joystick) {
    if (is_fake_joy(joystick)) {
        return;
    }
    if (!real_joy_close) {
        real_joy_close = dlsym(RTLD_NEXT, "SDL_JoystickClose");
    }
    if (real_joy_close) {
        real_joy_close(joystick);
    }
}

static const char *vpad_name(int slot) {
    static char buf[32];
    if (slot <= 0) {
        return "Cloud Game Pad";
    }
    snprintf(buf, sizeof(buf), "Cloud Game Pad %d", slot + 1);
    return buf;
}

static char *mapping_for_slot(int slot) {
    const char *cfg = getenv("SDL_GAMECONTROLLERCONFIG");
    if (!cfg || !cfg[0]) {
        return NULL;
    }
    const char *line = cfg;
    for (int i = 0; i < slot; i++) {
        line = strchr(line, '\n');
        if (!line) {
            return NULL;
        }
        line++;
    }
    const char *end = strchr(line, '\n');
    size_t len = end ? (size_t)(end - line) : strlen(line);
    char *copy = malloc(len + 1);
    if (!copy) {
        return NULL;
    }
    memcpy(copy, line, len);
    copy[len] = '\0';
    return copy;
}

void *SDL_GameControllerGetJoystick(void *gamecontroller) {
    int slot = fake_slot_gc(gamecontroller);
    if (slot >= 0) {
        return (void *)fake_joy[slot];
    }
    if (!real_gc_get_joy) {
        real_gc_get_joy = dlsym(RTLD_NEXT, "SDL_GameControllerGetJoystick");
    }
    if (real_gc_get_joy) {
        return real_gc_get_joy(gamecontroller);
    }
    return NULL;
}

const char *SDL_JoystickName(void *joystick) {
    int slot = fake_slot_joy(joystick);
    if (slot >= 0) {
        return vpad_name(slot);
    }
    if (!real_joy_name) {
        real_joy_name = dlsym(RTLD_NEXT, "SDL_JoystickName");
    }
    if (real_joy_name) {
        return real_joy_name(joystick);
    }
    return NULL;
}

const char *SDL_GameControllerName(void *gamecontroller) {
    int slot = fake_slot_gc(gamecontroller);
    if (slot >= 0) {
        return vpad_name(slot);
    }
    if (!real_gc_name) {
        real_gc_name = dlsym(RTLD_NEXT, "SDL_GameControllerName");
    }
    if (real_gc_name) {
        return real_gc_name(gamecontroller);
    }
    return NULL;
}

char *SDL_GameControllerMapping(void *gamecontroller) {
    int slot = fake_slot_gc(gamecontroller);
    if (slot >= 0) {
        return mapping_for_slot(slot);
    }
    if (!real_gc_mapping) {
        real_gc_mapping = dlsym(RTLD_NEXT, "SDL_GameControllerMapping");
    }
    if (real_gc_mapping) {
        return real_gc_mapping(gamecontroller);
    }
    return NULL;
}

int32_t SDL_JoystickInstanceID(void *joystick) {
    int slot = fake_slot_joy(joystick);
    if (slot >= 0) {
        return fake_instance_id(slot);
    }
    if (!real_joy_instance_id) {
        real_joy_instance_id = dlsym(RTLD_NEXT, "SDL_JoystickInstanceID");
    }
    if (real_joy_instance_id) {
        return real_joy_instance_id(joystick);
    }
    return -1;
}

int SDL_JoystickNumAxes(void *joystick) {
    if (is_fake_joy(joystick)) {
        return VIRT_AXES;
    }
    if (!real_joy_num_axes) {
        real_joy_num_axes = dlsym(RTLD_NEXT, "SDL_JoystickNumAxes");
    }
    if (real_joy_num_axes) {
        return real_joy_num_axes(joystick);
    }
    return 0;
}

int SDL_JoystickNumButtons(void *joystick) {
    if (is_fake_joy(joystick)) {
        return VIRT_BUTTONS + VIRT_HATS * 4;
    }
    if (!real_joy_num_buttons) {
        real_joy_num_buttons = dlsym(RTLD_NEXT, "SDL_JoystickNumButtons");
    }
    if (real_joy_num_buttons) {
        return real_joy_num_buttons(joystick);
    }
    return 0;
}

int SDL_JoystickNumHats(void *joystick) {
    if (is_fake_joy(joystick)) {
        return VIRT_HATS;
    }
    if (!real_joy_num_hats) {
        real_joy_num_hats = dlsym(RTLD_NEXT, "SDL_JoystickNumHats");
    }
    if (real_joy_num_hats) {
        return real_joy_num_hats(joystick);
    }
    return 0;
}

int16_t SDL_JoystickGetAxis(void *joystick, int axis) {
    if (!real_joy_axis) {
        real_joy_axis = dlsym(RTLD_NEXT, "SDL_JoystickGetAxis");
    }
    int slot = fake_slot_joy(joystick);
    if (slot >= 0) {
        refresh_pad(slot);
        if (pad_active[slot] && axis >= 0 && axis < VIRT_AXES) {
            return pad_axes[slot][axis];
        }
        return 0;
    }
    if (real_joy_axis) {
        return real_joy_axis(joystick, axis);
    }
    return 0;
}

uint8_t SDL_GameControllerGetButton(void *gamecontroller, int button) {
    if (!real_gc_button) {
        real_gc_button = dlsym(RTLD_NEXT, "SDL_GameControllerGetButton");
    }
    int slot = fake_slot_gc(gamecontroller);
    if (slot >= 0) {
        refresh_pad(slot);
        if (pad_active[slot] && button >= 0 && button < GC_BUTTONS) {
            return (pad_buttons[slot] >> GC_BUTTON_WIRE[button]) & 1u;
        }
        return 0;
    }
    if (real_gc_button) {
        return real_gc_button(gamecontroller, button);
    }
    return 0;
}

int16_t SDL_GameControllerGetAxis(void *gamecontroller, int axis) {
    if (!real_gc_axis) {
        real_gc_axis = dlsym(RTLD_NEXT, "SDL_GameControllerGetAxis");
    }
    int slot = fake_slot_gc(gamecontroller);
    if (slot >= 0) {
        refresh_pad(slot);
        if (pad_active[slot] && axis >= 0 && axis < SDL_CONTROLLER_AXIS_MAX) {
            int16_t wire[VIRT_AXES];
            volatile int16_t *src;
            open_map();
            if (!look) {
                return 0;
            }
            src = (volatile int16_t *)((unsigned char *)look + pad_off_axes(slot));
            for (int i = 0; i < VIRT_AXES; i++) {
                wire[i] = src[i];
            }
            return wire[axis];
        }
        return 0;
    }
    if (real_gc_axis) {
        return real_gc_axis(gamecontroller, axis);
    }
    return 0;
}

int SDL_GameControllerGetPlayerIndex(void *gamecontroller) {
    int slot = fake_slot_gc(gamecontroller);
    if (slot >= 0) {
        return slot;
    }
    if (!real_gc_player) {
        real_gc_player = dlsym(RTLD_NEXT, "SDL_GameControllerGetPlayerIndex");
    }
    if (real_gc_player) {
        int idx = real_gc_player(gamecontroller);
        return idx < 0 ? 0 : idx;
    }
    return 0;
}
