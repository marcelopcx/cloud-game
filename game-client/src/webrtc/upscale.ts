/**
 * Upscaling del stream en el cliente con AMD FidelityFX Super Resolution 1.0
 * (EASU + RCAS, MIT), portado a WebGL2.
 *
 *   <video> ──requestVideoFrameCallback──► textura ─EASU─► FBO ─RCAS─► <canvas>
 *
 * El <video> sigue en el DOM debajo del canvas: da el srcObject, el audio
 * mudo/autoplay y la geometría que usa el mouse (pointer.ts). El canvas se
 * alinea al rectángulo de `object-fit: contain` del video y no recibe eventos.
 *
 * Automático: en `auto` solo se activa si el stream es más chico que el
 * canvas en píxeles físicos (STATION_STREAM_SIZE < pantalla); a 1:1 o al
 * reducir, EASU empeora la imagen y se deja el <video> nativo. Sin WebGL2, con
 * el contexto perdido o si el render pasa de RENDER_BUDGET_MS, queda el
 * <video> normal.
 */

export type UpscaleMode = 'auto' | 'off';

export type UpscaleStatus = {
    active: boolean;
    /** Texto corto para el overlay de stats. */
    label: string;
};

export const UPSCALE_MODES: readonly UpscaleMode[] = ['auto', 'off'];

/** Nitidez de RCAS en "stops" (0 = máxima). AMD recomienda 0.2 por defecto. */
const SHARPNESS_STOPS = 0.25;
/** Presupuesto por frame (CPU de subida + GPU de los dos pases). */
const RENDER_BUDGET_MS = 4;
/** Frames antes de juzgar el costo: los primeros incluyen compilación y warmup. */
const WARMUP_FRAMES = 90;
const EMA = 0.05;

const VERTEX = `#version 300 es
void main() {
    // Triángulo que cubre la pantalla, sin buffers.
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}`;

/** EASU (Edge Adaptive Spatial Upsampling), versión escalar de ffx_fsr1.h con texelFetch. */
const EASU = `#version 300 es
precision highp float;
precision highp int;
uniform sampler2D uSrc;
uniform vec2 uInSize;
uniform vec2 uOutSize;
out vec4 outColor;

vec3 tap(ivec2 p) {
    return texelFetch(uSrc, clamp(p, ivec2(0), ivec2(uInSize) - 1), 0).rgb;
}

// Luma x2 aproximada (la misma de FSR).
float luma2(vec3 c) { return c.g + 0.5 * (c.r + c.b); }

void setF(inout vec2 dir, inout float len, float w, float lA, float lB, float lC, float lD, float lE) {
    float lenX = max(abs(lD - lC), abs(lC - lB));
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = clamp(abs(dirX) / max(lenX, 1e-5), 0.0, 1.0);
    len += lenX * lenX * w;
    float lenY = max(abs(lE - lC), abs(lC - lA));
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = clamp(abs(dirY) / max(lenY, 1e-5), 0.0, 1.0);
    len += lenY * lenY * w;
}

void tapF(inout vec3 aC, inout float aW, vec2 off, vec2 dir, vec2 len2, float lob, float clp, vec3 c) {
    vec2 v = vec2(dot(off, dir), dot(off, vec2(-dir.y, dir.x))) * len2;
    float d2 = min(dot(v, v), clp);
    float wB = 0.4 * d2 - 1.0;
    float wA = lob * d2 - 1.0;
    wB *= wB;
    wA *= wA;
    wB = 1.5625 * wB - 0.5625;
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

void main() {
    // Fila 0 = arriba del video (texImage2D sin FLIP_Y).
    vec2 ip = vec2(gl_FragCoord.x, uOutSize.y - gl_FragCoord.y) - 0.5;
    vec2 pp = (ip + 0.5) * (uInSize / uOutSize) - 0.5;
    vec2 fp = floor(pp);
    pp -= fp;
    ivec2 o = ivec2(fp);

    //    b c
    //  e f g h
    //  i j k l
    //    n o
    vec3 bC = tap(o + ivec2(0, -1)); vec3 cC = tap(o + ivec2(1, -1));
    vec3 eC = tap(o + ivec2(-1, 0)); vec3 fC = tap(o);
    vec3 gC = tap(o + ivec2(1, 0));  vec3 hC = tap(o + ivec2(2, 0));
    vec3 iC = tap(o + ivec2(-1, 1)); vec3 jC = tap(o + ivec2(0, 1));
    vec3 kC = tap(o + ivec2(1, 1));  vec3 lC = tap(o + ivec2(2, 1));
    vec3 nC = tap(o + ivec2(0, 2));  vec3 oC = tap(o + ivec2(1, 2));
    float bL = luma2(bC), cL = luma2(cC), eL = luma2(eC), fL = luma2(fC);
    float gL = luma2(gC), hL = luma2(hC), iL = luma2(iC), jL = luma2(jC);
    float kL = luma2(kC), lL = luma2(lC), nL = luma2(nC), oL = luma2(oC);

    vec2 dir = vec2(0.0);
    float len = 0.0;
    setF(dir, len, (1.0 - pp.x) * (1.0 - pp.y), bL, eL, fL, gL, jL);
    setF(dir, len, pp.x * (1.0 - pp.y), cL, fL, gL, hL, kL);
    setF(dir, len, (1.0 - pp.x) * pp.y, fL, iL, jL, kL, nL);
    setF(dir, len, pp.x * pp.y, gL, jL, kL, lL, oL);

    float dirR = dot(dir, dir);
    bool zro = dirR < (1.0 / 32768.0);
    dirR = zro ? 1.0 : inversesqrt(dirR);
    dir.x = zro ? 1.0 : dir.x;
    dir *= dirR;
    len = len * 0.5;
    len *= len;
    float stretch = dot(dir, dir) / max(abs(dir.x), abs(dir.y));
    vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);
    float lob = 0.5 - 0.29 * len;
    float clp = 1.0 / lob;

    vec3 min4 = min(min(fC, gC), min(jC, kC));
    vec3 max4 = max(max(fC, gC), max(jC, kC));
    vec3 aC = vec3(0.0);
    float aW = 0.0;
    tapF(aC, aW, vec2(0.0, -1.0) - pp, dir, len2, lob, clp, bC);
    tapF(aC, aW, vec2(1.0, -1.0) - pp, dir, len2, lob, clp, cC);
    tapF(aC, aW, vec2(-1.0, 1.0) - pp, dir, len2, lob, clp, iC);
    tapF(aC, aW, vec2(0.0, 1.0) - pp, dir, len2, lob, clp, jC);
    tapF(aC, aW, vec2(0.0, 0.0) - pp, dir, len2, lob, clp, fC);
    tapF(aC, aW, vec2(-1.0, 0.0) - pp, dir, len2, lob, clp, eC);
    tapF(aC, aW, vec2(1.0, 1.0) - pp, dir, len2, lob, clp, kC);
    tapF(aC, aW, vec2(2.0, 1.0) - pp, dir, len2, lob, clp, lC);
    tapF(aC, aW, vec2(2.0, 0.0) - pp, dir, len2, lob, clp, hC);
    tapF(aC, aW, vec2(1.0, 0.0) - pp, dir, len2, lob, clp, gC);
    tapF(aC, aW, vec2(1.0, 2.0) - pp, dir, len2, lob, clp, oC);
    tapF(aC, aW, vec2(0.0, 2.0) - pp, dir, len2, lob, clp, nC);
    // Dering: nunca fuera del rango de los 4 vecinos más cercanos.
    outColor = vec4(min(max4, max(min4, aC / aW)), 1.0);
}`;

/** RCAS (Robust Contrast Adaptive Sharpening), sin el denoise opcional. */
const RCAS = `#version 300 es
precision highp float;
precision highp int;
uniform sampler2D uSrc;
uniform float uSharp;
out vec4 outColor;
#define RCAS_LIMIT (0.25 - (1.0 / 16.0))

vec3 at(ivec2 p) {
    return texelFetch(uSrc, clamp(p, ivec2(0), textureSize(uSrc, 0) - 1), 0).rgb;
}

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    //    b
    //  d e f
    //    h
    vec3 b = at(p + ivec2(0, 1));
    vec3 d = at(p + ivec2(-1, 0));
    vec3 e = at(p);
    vec3 f = at(p + ivec2(1, 0));
    vec3 h = at(p + ivec2(0, -1));
    vec3 mn4 = min(min(b, d), min(f, h));
    vec3 mx4 = max(max(b, d), max(f, h));
    vec3 hitMin = min(mn4, e) / max(4.0 * mx4, vec3(1e-5));
    vec3 hitMax = (1.0 - max(mx4, e)) / min(4.0 * mn4 - 4.0, vec3(-1e-5));
    vec3 lobeRGB = max(-hitMin, hitMax);
    float lobe = max(-RCAS_LIMIT, min(max(lobeRGB.r, max(lobeRGB.g, lobeRGB.b)), 0.0)) * uSharp;
    outColor = vec4((lobe * (b + d + f + h) + e) / (4.0 * lobe + 1.0), 1.0);
}`;

type VideoWithFrameCb = HTMLVideoElement & {
    requestVideoFrameCallback?: (cb: VideoFrameRequestCallback) => number;
    cancelVideoFrameCallback?: (handle: number) => void;
};

type TimerExt = { TIME_ELAPSED_EXT: number; GPU_DISJOINT_EXT: number };

function compile(gl: WebGL2RenderingContext, fragment: string): WebGLProgram {
    const shader = (type: number, source: string) => {
        const s = gl.createShader(type);
        if (!s) throw new Error('createShader');
        gl.shaderSource(s, source);
        gl.compileShader(s);
        if (!gl.getShaderParameter(s, gl.COMPILE_STATUS) && !gl.isContextLost()) {
            throw new Error(gl.getShaderInfoLog(s) ?? 'shader');
        }
        return s;
    };
    const program = gl.createProgram();
    if (!program) throw new Error('createProgram');
    gl.attachShader(program, shader(gl.VERTEX_SHADER, VERTEX));
    gl.attachShader(program, shader(gl.FRAGMENT_SHADER, fragment));
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS) && !gl.isContextLost()) {
        throw new Error(gl.getProgramInfoLog(program) ?? 'link');
    }
    return program;
}

function texture(gl: WebGL2RenderingContext): WebGLTexture {
    const tex = gl.createTexture();
    if (!tex) throw new Error('createTexture');
    gl.bindTexture(gl.TEXTURE_2D, tex);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    return tex;
}

export function createUpscaler(opts: {
    video: HTMLVideoElement;
    canvas: HTMLCanvasElement;
    mode: UpscaleMode;
    onStatus?: (status: UpscaleStatus) => void;
    onLog?: (line: string) => void;
}) {
    const { video, canvas } = opts;
    const node = video as VideoWithFrameCb;
    let mode = opts.mode;
    let stopped = false;
    let lastLabel = '';

    const report = (active: boolean, label: string) => {
        canvas.hidden = !active;
        if (label === lastLabel) return;
        lastLabel = label;
        opts.onStatus?.({ active, label });
    };

    const gl = canvas.getContext('webgl2', {
        alpha: false,
        antialias: false,
        depth: false,
        stencil: false,
        premultipliedAlpha: false,
        preserveDrawingBuffer: false,
        powerPreference: 'high-performance',
        desynchronized: true,
    });
    if (!gl) {
        report(false, 'upscale: sin WebGL2');
        return { setMode: (next: UpscaleMode) => void (mode = next), stop: () => undefined };
    }

    let easu: WebGLProgram;
    let rcas: WebGLProgram;
    try {
        easu = compile(gl, EASU);
        rcas = compile(gl, RCAS);
    } catch (err) {
        opts.onLog?.(`upscale shader ${err instanceof Error ? err.message : err}`);
        report(false, 'upscale: error de shader');
        return { setMode: (next: UpscaleMode) => void (mode = next), stop: () => undefined };
    }
    const vao = gl.createVertexArray();
    const src = texture(gl);
    const mid = texture(gl);
    const fbo = gl.createFramebuffer();
    const uEasuIn = gl.getUniformLocation(easu, 'uInSize');
    const uEasuOut = gl.getUniformLocation(easu, 'uOutSize');
    const uRcasSharp = gl.getUniformLocation(rcas, 'uSharp');
    gl.useProgram(easu);
    gl.uniform1i(gl.getUniformLocation(easu, 'uSrc'), 0);
    gl.useProgram(rcas);
    gl.uniform1i(gl.getUniformLocation(rcas, 'uSrc'), 0);
    const timer = gl.getExtension('EXT_disjoint_timer_query_webgl2') as TimerExt | null;
    const queries: WebGLQuery[] = [];

    let srcW = 0;
    let srcH = 0;
    let outW = 0;
    let outH = 0;
    let frames = 0;
    let cpuMs = 0;
    let gpuMs = 0;
    let tooSlow = false;
    let lost = false;

    /** Canvas encima del rectángulo visible del video (object-fit: contain). */
    const layout = () => {
        const vw = video.videoWidth;
        const vh = video.videoHeight;
        const cw = video.clientWidth;
        const ch = video.clientHeight;
        if (!vw || !vh || !cw || !ch) return false;
        const scale = Math.min(cw / vw, ch / vh);
        const w = vw * scale;
        const h = vh * scale;
        const left = video.offsetLeft + (cw - w) / 2;
        const top = video.offsetTop + (ch - h) / 2;
        const dpr = window.devicePixelRatio || 1;
        const bw = Math.max(1, Math.round(w * dpr));
        const bh = Math.max(1, Math.round(h * dpr));
        canvas.style.left = `${left}px`;
        canvas.style.top = `${top}px`;
        canvas.style.width = `${w}px`;
        canvas.style.height = `${h}px`;
        if (bw !== outW || bh !== outH) {
            outW = bw;
            outH = bh;
            canvas.width = bw;
            canvas.height = bh;
            gl.bindTexture(gl.TEXTURE_2D, mid);
            gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, bw, bh, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
            gl.bindFramebuffer(gl.FRAMEBUFFER, fbo);
            gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, mid, 0);
            gl.bindFramebuffer(gl.FRAMEBUFFER, null);
        }
        return true;
    };

    const collectGpu = () => {
        if (!timer) return;
        while (queries.length > 0) {
            const q = queries[0];
            if (!gl.getQueryParameter(q, gl.QUERY_RESULT_AVAILABLE)) break;
            const disjoint = gl.getParameter(timer.GPU_DISJOINT_EXT) as boolean;
            const ns = gl.getQueryParameter(q, gl.QUERY_RESULT) as number;
            gl.deleteQuery(q);
            queries.shift();
            if (!disjoint) gpuMs += (ns / 1e6 - gpuMs) * EMA;
        }
    };

    const render = () => {
        if (stopped || lost) return;
        if (mode === 'off') {
            report(false, 'upscale: off');
            return;
        }
        if (tooSlow) {
            report(false, 'upscale: off (lento)');
            return;
        }
        if (video.readyState < 2 || !layout()) return;
        const vw = video.videoWidth;
        const vh = video.videoHeight;
        // Solo si hay que agrandar. Stream ≥ pantalla → video nativo.
        if (vw >= outW && vh >= outH) {
            report(false, `nativo ${vw}×${vh}`);
            return;
        }
        const t0 = performance.now();
        gl.activeTexture(gl.TEXTURE0);
        gl.bindTexture(gl.TEXTURE_2D, src);
        if (vw !== srcW || vh !== srcH) {
            srcW = vw;
            srcH = vh;
            gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, vw, vh, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
        }
        gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 0, gl.RGBA, gl.UNSIGNED_BYTE, video);

        let query: WebGLQuery | null = null;
        if (timer && queries.length < 4) {
            query = gl.createQuery();
            if (query) gl.beginQuery(timer.TIME_ELAPSED_EXT, query);
        }
        gl.bindVertexArray(vao);
        gl.viewport(0, 0, outW, outH);

        gl.bindFramebuffer(gl.FRAMEBUFFER, fbo);
        gl.useProgram(easu);
        gl.uniform2f(uEasuIn, vw, vh);
        gl.uniform2f(uEasuOut, outW, outH);
        gl.drawArrays(gl.TRIANGLES, 0, 3);

        gl.bindFramebuffer(gl.FRAMEBUFFER, null);
        gl.bindTexture(gl.TEXTURE_2D, mid);
        gl.useProgram(rcas);
        gl.uniform1f(uRcasSharp, 2 ** -SHARPNESS_STOPS);
        gl.drawArrays(gl.TRIANGLES, 0, 3);

        if (query && timer) {
            gl.endQuery(timer.TIME_ELAPSED_EXT);
            queries.push(query);
        }
        cpuMs += (performance.now() - t0 - cpuMs) * EMA;
        collectGpu();

        frames++;
        if (frames > WARMUP_FRAMES && cpuMs + gpuMs > RENDER_BUDGET_MS) {
            tooSlow = true;
            opts.onLog?.(
                `upscale off: ${(cpuMs + gpuMs).toFixed(1)} ms/frame > ${RENDER_BUDGET_MS} ms (cpu ${cpuMs.toFixed(1)} gpu ${gpuMs.toFixed(1)})`,
            );
            report(false, 'upscale: off (lento)');
            return;
        }
        const cost = frames > WARMUP_FRAMES ? ` ${(cpuMs + gpuMs).toFixed(1)}ms` : '';
        report(true, `FSR ${vw}×${vh}→${outW}×${outH}${cost}`);
    };

    let handle = 0;
    let raf = 0;
    const onFrame = () => {
        if (stopped) return;
        render();
        handle = node.requestVideoFrameCallback?.(onFrame) ?? 0;
    };
    // Sin requestVideoFrameCallback: un frame por repintado del navegador.
    const onRaf = () => {
        if (stopped) return;
        render();
        raf = window.requestAnimationFrame(onRaf);
    };
    if (node.requestVideoFrameCallback) handle = node.requestVideoFrameCallback(onFrame);
    else raf = window.requestAnimationFrame(onRaf);

    // Resize/fullscreen con el video en pausa: repinta sin esperar un frame nuevo.
    const resize = new ResizeObserver(() => render());
    resize.observe(video);

    const onLost = (ev: Event) => {
        ev.preventDefault();
        lost = true;
        report(false, 'upscale: contexto perdido');
    };
    canvas.addEventListener('webglcontextlost', onLost);

    report(false, mode === 'off' ? 'upscale: off' : 'upscale: esperando video');

    return {
        setMode: (next: UpscaleMode) => {
            if (next === mode) return;
            mode = next;
            // Cambio manual: vuelve a medir desde cero.
            tooSlow = false;
            frames = 0;
            cpuMs = 0;
            gpuMs = 0;
            render();
        },
        stop: () => {
            stopped = true;
            if (handle && node.cancelVideoFrameCallback) node.cancelVideoFrameCallback(handle);
            if (raf) window.cancelAnimationFrame(raf);
            resize.disconnect();
            canvas.removeEventListener('webglcontextlost', onLost);
            canvas.hidden = true;
            if (!gl.isContextLost()) {
                for (const q of queries) gl.deleteQuery(q);
                gl.deleteTexture(src);
                gl.deleteTexture(mid);
                gl.deleteFramebuffer(fbo);
                gl.deleteVertexArray(vao);
                gl.deleteProgram(easu);
                gl.deleteProgram(rcas);
            }
        },
    };
}
