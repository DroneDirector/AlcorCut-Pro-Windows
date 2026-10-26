package com.dronedirector.veyrocutpro.editor



enum class TransitionGroup(val label: String) { BASIC("Basic"), WIPES("Wipes & shapes"), MOTION("Motion"), FANCY("Stylized"), CINEMATIC("Cinematic") }

/** How the incoming clip is combined with the outgoing one. */
enum class TransitionKind { UNIFORM, MASK, MOVE }

enum class TransitionType(val id: Float, val label: String, val group: TransitionGroup, val kind: TransitionKind) {
    NONE(0f, "Cut", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    CROSSFADE(1f, "Cross dissolve", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    DIP_BLACK(2f, "Dip to black", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    DIP_WHITE(3f, "Dip to white", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    FLASH(4f, "Flash", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    BLUR(5f, "Blur dissolve", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    /** For jump cuts (interviews), in-between frames drawn new from optical flow (MorphCut.kt); a soft dissolve until analysed. */
    SMOOTH_CUT(7f, "Morph cut", TransitionGroup.BASIC, TransitionKind.UNIFORM),

    WIPE_LEFT(10f, "Wipe left", TransitionGroup.WIPES, TransitionKind.MASK),
    WIPE_RIGHT(11f, "Wipe right", TransitionGroup.WIPES, TransitionKind.MASK),
    WIPE_UP(12f, "Wipe up", TransitionGroup.WIPES, TransitionKind.MASK),
    WIPE_DOWN(13f, "Wipe down", TransitionGroup.WIPES, TransitionKind.MASK),
    DIAGONAL(14f, "Diagonal", TransitionGroup.WIPES, TransitionKind.MASK),
    IRIS(15f, "Iris", TransitionGroup.WIPES, TransitionKind.MASK),
    CLOCK(16f, "Clock", TransitionGroup.WIPES, TransitionKind.MASK),
    BLINDS(17f, "Blinds", TransitionGroup.WIPES, TransitionKind.MASK),
    BARN_DOOR(18f, "Barn door", TransitionGroup.WIPES, TransitionKind.MASK),
    MOSAIC(19f, "Mosaic", TransitionGroup.WIPES, TransitionKind.MASK),

    PUSH_LEFT(30f, "Push left", TransitionGroup.MOTION, TransitionKind.MOVE),
    PUSH_RIGHT(31f, "Push right", TransitionGroup.MOTION, TransitionKind.MOVE),
    PUSH_UP(32f, "Push up", TransitionGroup.MOTION, TransitionKind.MOVE),
    PUSH_DOWN(33f, "Push down", TransitionGroup.MOTION, TransitionKind.MOVE),
    WHIP_LEFT(34f, "Whip pan left", TransitionGroup.MOTION, TransitionKind.MOVE),
    WHIP_RIGHT(35f, "Whip pan right", TransitionGroup.MOTION, TransitionKind.MOVE),
    ZOOM_IN(36f, "Zoom in", TransitionGroup.MOTION, TransitionKind.UNIFORM),
    ZOOM_OUT(37f, "Zoom out", TransitionGroup.MOTION, TransitionKind.UNIFORM),
    SPIN(38f, "Spin", TransitionGroup.MOTION, TransitionKind.UNIFORM),

    LIGHT_LEAK(6f, "Light leak", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    INK(20f, "Ink bleed", TransitionGroup.FANCY, TransitionKind.MASK),
    SPARKLE(21f, "Sparkle dissolve", TransitionGroup.FANCY, TransitionKind.MASK),
    GLITCH(40f, "Glitch", TransitionGroup.FANCY, TransitionKind.MASK),
    PIXELATE(41f, "Pixelate", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    CHROMA(42f, "Chromatic", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    RIPPLE(43f, "Ripple", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    LIGHT_SPEED(44f, "Light speed", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    SWIRL(45f, "Swirl", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    GLITCH_MOSH(53f, "Glitch 2 · Datamosh", TransitionGroup.FANCY, TransitionKind.MASK),
    GLITCH_VHS(54f, "Glitch 3 · VHS rewind", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    GLITCH_SORT(63f, "Glitch 4 · Pixel sort", TransitionGroup.FANCY, TransitionKind.UNIFORM),

    // Cinematic set: original AlcorCut Pro shaders (written for this app, no third-party code).
    FILM_BURN(50f, "Film burn", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    ANAMORPHIC(51f, "Anamorphic flare", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    LENS_WARP(52f, "Lens warp", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    LETTERBOX(55f, "Letterbox close", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    APERTURE(56f, "Aperture", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    SMOKE(57f, "Smoke", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    SHATTER(58f, "Shatter", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    KALEIDO(59f, "Kaleidoscope", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    CUBE(60f, "Cube turn", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    FLIP(61f, "Card flip", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    SUN_RAYS(62f, "Sun rays", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    NEON(64f, "Neon edges", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    EMBERS(65f, "Ember drift", TransitionGroup.CINEMATIC, TransitionKind.MASK),
}

/**
 * Transition from a clip into the next one: the picture ([type]) and the sound ([audio]).
 * [audio] / [audioMs] are nullable so projects saved before audio transitions still load.
 */
data class Transition(
    val type: TransitionType = TransitionType.NONE,
    val durationMs: Long = 800L,
    val audio: AudioTransitionType? = null,
    val audioMs: Long? = null,
    /** Sound effect on the cut (impact, whoosh…); null = none. */
    val sfx: TransitionSfx? = null,
    val sfxVol: Float? = null,
    /** An imported sound (UserSfxLibrary id); used instead of [sfx] when set. */
    val userSfx: String? = null,
) {
    val sfxVolume: Float get() = sfxVol ?: 0.8f
    val audioType: AudioTransitionType get() = audio ?: AudioTransitionType.AUTO
    val audioLen: Long get() = audioMs ?: 600L
}

/** How the sound changes from one clip to the next. */
enum class AudioTransitionType(val label: String, val detail: String) {
    AUTO("Match video", "Smooth crossfade over the video transition; nothing on a plain cut."),
    CUT("Hard cut", "Sound switches in the middle of the transition, no fade."),
    CONSTANT_GAIN("Constant gain", "Straight-line crossfade. Can dip slightly in the middle."),
    CONSTANT_POWER("Constant power", "Equal-power crossfade — no dip in loudness. Best all-rounder."),
    EXPONENTIAL("Exponential fade", "Outgoing sound drops away quickly, incoming swells in late. Natural for music."),
    DIP("Dip to silence", "Outgoing fades out, a short silence, then the next clip fades in."),
    QUICK_OUT("Quick out, slow in", "Old sound disappears fast; new sound eases in."),
    QUICK_IN("Slow out, quick in", "Old sound lingers and fades; new sound arrives fast."),
}

object TransitionMath {
    private fun ss(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
    fun ease(x: Float): Float = if (x < 0.5f) 4 * x * x * x else 1 - Math.pow((-2.0 * x + 2), 3.0).toFloat() / 2

    /** How much of the incoming clip shows for uniform transitions (same as the shader). */
    fun uniformReveal(t: TransitionType, p: Float): Float = when (t.id.toInt()) {
        1 -> p
        2, 3 -> ss(0.48f, 0.52f, p)
        4 -> ss(0.35f, 0.65f, p)
        5, 6 -> ss(0.3f, 0.7f, p)
        7 -> ss(0.1f, 0.9f, p)
        36, 37, 38 -> ss(0.4f, 0.6f, p)
        41 -> ss(0.45f, 0.55f, p)
        42, 43, 44, 45 -> ss(0.25f, 0.75f, p)
        51, 52, 59, 63, 64 -> ss(0.4f, 0.6f, p)
        54 -> ss(0.45f, 0.55f, p)
        55, 56 -> ss(0.49f, 0.51f, p)
        62 -> ss(0.35f, 0.65f, p)
        else -> 1f
    }

    /** Movement (fraction of the frame, x right, y down) for push / whip transitions. */
    fun move(t: TransitionType, p: Float, incoming: Boolean): Pair<Float, Float> {
        val e = if (t == TransitionType.WHIP_LEFT || t == TransitionType.WHIP_RIGHT) ease(ease(p)) else ease(p)
        return when (t) {
            TransitionType.PUSH_LEFT, TransitionType.WHIP_LEFT -> (if (incoming) 1 - e else -e) to 0f
            TransitionType.PUSH_RIGHT, TransitionType.WHIP_RIGHT -> (if (incoming) -(1 - e) else e) to 0f
            TransitionType.PUSH_UP -> 0f to (if (incoming) 1 - e else -e)
            TransitionType.PUSH_DOWN -> 0f to (if (incoming) -(1 - e) else e)
            else -> 0f to 0f
        }
    }

    /**
     * Shared transition shader body (GLSL ES 1.0 and AGSL both accept it). Needs `tex(uv)` and
     * uniforms uType, uP (0..1), uSide (0 outgoing, 1 incoming), uMode (0 opaque, 1 alpha = reveal,
     * 2 alpha = 1 - reveal) and uSize (pixels). uv has its origin bottom-left.
     */
    const val CORE = """
        float rnd(vec2 c) { return fract(sin(dot(c, vec2(12.9898, 78.233))) * 43758.5453); }
        float vnoise(vec2 q) {
            vec2 i = floor(q); vec2 f = fract(q); f = f * f * (3.0 - 2.0 * f);
            float a = rnd(i); float b = rnd(i + vec2(1.0, 0.0)); float c = rnd(i + vec2(0.0, 1.0)); float d = rnd(i + vec2(1.0, 1.0));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }
        float fbm(vec2 q) { float v = 0.0; float a = 0.5; for (int i = 0; i < 5; i++) { v += a * vnoise(q); q *= 2.0; a *= 0.5; } return v; }
        bool isT(float id) { return abs(uType - id) < 0.5; }
        float easeF(float x) { if (x < 0.5) return 4.0 * x * x * x; float y = -2.0 * x + 2.0; return 1.0 - y * y * y / 2.0; }
        float sstep(float a, float b, float x) { return smoothstep(a, b, x); }
        float cover(float v, float e, float s) { return 1.0 - smoothstep(e - s, e + s, v); }
        vec4 mtex(vec2 uv) { vec2 m = abs(mod(uv + 1.0, 2.0) - 1.0); return tex(m); }


        // ---------------- Cinematic set (original shaders) ----------------
        float lum(vec3 v) { return dot(v, vec3(0.2126, 0.7152, 0.0722)); }
        float burnField(vec2 uv, float aspect) { vec2 q = uv * vec2(aspect, 1.0); return length(q - vec2(0.32 * aspect, 0.62)) * 0.75 + fbm(q * 2.6) * 0.6; }
        float burnEdge(float p) { return mix(-0.05, 1.75, p); }
        vec2 moshFlow(vec2 uv, float k) { vec2 b = floor(uv * vec2(20.0, 12.0)); return (vec2(rnd(b + 0.7), rnd(b + 5.1)) - 0.5) * 0.09 * k; }
        float moshMask(vec2 uv, float p) {
            float k = 1.0 - abs(2.0 * p - 1.0);
            vec2 b = floor((uv - moshFlow(uv, k)) * vec2(20.0, 12.0));
            return step(rnd(b + 9.0) * 0.7 + (1.0 - uv.y) * 0.25 + 0.02, p);
        }
        float smokeField(vec2 uv, float aspect, float p) { return fbm(uv * vec2(aspect, 1.0) * 2.2 + vec2(p * 0.8, p * 0.3)) * 0.7 + uv.x * 0.3; }
        float emberField(vec2 uv, float aspect) {
            vec2 g = floor(uv * uSize / 2.5);
            return uv.x * 0.7 + fbm(uv * vec2(aspect, 1.0) * 3.0) * 0.3 + (rnd(g) - 0.5) * 0.06;
        }
        // Box (cube) face hit: xy = face uv, z = 1 when the ray hits it.
        vec3 boxFace(vec2 uv0, float aspect, float p, float rightFace) {
            float ang = easeF(p) * 1.5707963; float k = 1.0 - abs(2.0 * p - 1.0);
            vec3 sp = vec3((uv0.x * 2.0 - 1.0) * aspect, uv0.y * 2.0 - 1.0, 0.0);
            vec3 o = vec3(0.0, 0.0, 3.2);
            vec3 d = sp - o;
            vec3 cen = vec3(0.0, 0.0, -aspect - k * 0.8);
            float cs = cos(ang); float sn = sin(ang);
            vec3 ol = o - cen;
            vec3 ol2 = vec3(ol.x * cs + ol.z * sn, ol.y, -ol.x * sn + ol.z * cs);
            vec3 dl = vec3(d.x * cs + d.z * sn, d.y, -d.x * sn + d.z * cs);
            float t = 0.0; vec2 f = vec2(-1.0);
            if (rightFace > 0.5) {
                if (dl.x < -1e-5) { t = (aspect - ol2.x) / dl.x; vec3 h = ol2 + dl * t; f = vec2(-h.z / aspect * 0.5 + 0.5, h.y * 0.5 + 0.5); }
            } else {
                if (dl.z < -1e-5) { t = (aspect - ol2.z) / dl.z; vec3 h = ol2 + dl * t; f = vec2(h.x / aspect * 0.5 + 0.5, h.y * 0.5 + 0.5); }
            }
            float hit = step(0.0, f.x) * step(f.x, 1.0) * step(0.0, f.y) * step(f.y, 1.0) * step(0.0, t);
            return vec3(f, hit);
        }
        // Card flip: xy = card uv (unmirrored on the back), z = hit.
        vec3 flipFace(vec2 uv0, float aspect, float p) {
            float a = easeF(p) * 3.14159265; float k = 1.0 - abs(2.0 * p - 1.0);
            vec3 sp = vec3((uv0.x * 2.0 - 1.0) * aspect, uv0.y * 2.0 - 1.0, 0.0);
            vec3 o = vec3(0.0, 0.0, 3.5);
            vec3 d = sp - o;
            vec3 cen = vec3(0.0, 0.0, -k * 1.2);
            float cs = cos(a); float sn = sin(a);
            vec3 ol = o - cen;
            vec3 ol2 = vec3(ol.x * cs + ol.z * sn, ol.y, -ol.x * sn + ol.z * cs);
            vec3 dl = vec3(d.x * cs + d.z * sn, d.y, -d.x * sn + d.z * cs);
            float hit = 0.0; vec2 f = vec2(-1.0);
            if (abs(dl.z) > 1e-5) {
                float t = -ol2.z / dl.z; vec3 h = ol2 + dl * t;
                f = vec2(h.x / aspect * 0.5 + 0.5, h.y * 0.5 + 0.5);
                hit = step(0.0, f.x) * step(f.x, 1.0) * step(0.0, f.y) * step(f.y, 1.0) * step(0.0, t);
                if (p >= 0.5) f.x = 1.0 - f.x;
            }
            return vec3(f, hit);
        }
        vec2 shardC(vec2 cl) { return (cl + 0.2 + 0.6 * vec2(rnd(cl + 3.1), rnd(cl + 7.7))) / vec2(6.0, 4.0); }
        float shardT(vec2 cl, float p) { vec2 cc = shardC(cl); float dly = length((cc - vec2(0.5, 0.55)) * vec2(1.6, 1.0)) * 0.55; return clamp((p * 1.35 - dly) / 0.8, 0.0, 1.0); }
        vec3 shardNear(vec2 q) {
            vec2 g = vec2(6.0, 4.0); vec2 b = floor(q * g);
            vec2 asp = vec2(uSize.x / uSize.y, 1.0);
            float d1 = 9.0; float d2 = 9.0; vec2 best = b;
            for (int j = -1; j <= 1; j++) {
                for (int i = -1; i <= 1; i++) {
                    vec2 cl = clamp(b + vec2(float(i), float(j)), vec2(0.0), g - 1.0);
                    float d = length((q - shardC(cl)) * asp);
                    if (d < d1) { d2 = d1; d1 = d; best = cl; }
                    else if (d < d2 && length(cl - best) > 0.5) { d2 = d; }
                }
            }
            return vec3(best, d2 - d1);
        }
        // Shatter: xy = where in the outgoing picture, z = edge distance, w = 1 + fall (0 = no shard here).
        vec4 shatter(vec2 uv, float p) {
            vec4 res = vec4(0.0);
            float aspect = uSize.x / uSize.y;
            for (int j = 0; j < 4; j++) {
                for (int i = 0; i < 6; i++) {
                    vec2 cl = vec2(float(i), float(j));
                    if (res.w < 0.5) {
                        float t = shardT(cl, p); vec2 cen = shardC(cl);
                        vec2 off = vec2((rnd(cl + 1.3) - 0.5) * 0.5 * t, -1.6 * t * t + 0.12 * t);
                        float ang = (rnd(cl + 4.0) - 0.5) * 2.5 * t;
                        float cs = cos(ang); float sn = sin(ang);
                        vec2 v = (uv - off - cen) * vec2(aspect, 1.0);
                        v = vec2(v.x * cs - v.y * sn, v.x * sn + v.y * cs) / vec2(aspect, 1.0);
                        vec2 q = cen + v / (1.0 - 0.35 * t);
                        if (q.x >= 0.0 && q.x <= 1.0 && q.y >= 0.0 && q.y <= 1.0) {
                            vec3 nb = shardNear(q);
                            if (length(nb.xy - cl) < 0.5) res = vec4(q, nb.z, 1.0 + t);
                        }
                    }
                }
            }
            return res;
        }

        float revealC(vec2 uv) {
            float p = uP; float aspect = uSize.x / uSize.y;
            if (isT(50.0)) return 1.0 - smoothstep(-0.015, 0.015, burnField(uv, aspect) - burnEdge(p));
            if (isT(51.0) || isT(52.0) || isT(59.0) || isT(63.0) || isT(64.0)) return sstep(0.4, 0.6, p);
            if (isT(54.0)) return sstep(0.45, 0.55, p);
            if (isT(55.0) || isT(56.0)) return sstep(0.49, 0.51, p);
            if (isT(62.0)) return sstep(0.35, 0.65, p);
            if (isT(53.0)) return moshMask(uv, p);
            if (isT(57.0)) return smoothstep(-0.18, 0.18, mix(-0.35, 1.35, p) - smokeField(uv, aspect, p));
            if (isT(58.0)) return 1.0 - step(0.5, shatter(uv, p).w);
            if (isT(60.0)) return boxFace(uv, aspect, p, 1.0).z;
            if (isT(61.0)) return step(0.5, p) * flipFace(uv, aspect, p).z;
            if (isT(65.0)) return step(emberField(uv, aspect), mix(-0.1, 1.1, p));
            return 1.0;
        }

        vec4 cine(vec2 uv0) {
            float p = uP;
            float k = 1.0 - abs(2.0 * p - 1.0);
            bool inc = uSide > 0.5;
            float aspect = uSize.x / uSize.y;
            vec2 c = uv0 - 0.5;
            vec2 uv = uv0;
            vec3 col = vec3(0.0);
            if (isT(50.0)) {
                // Film burn: a hot edge eats the picture from a burn point; charring ahead of it.
                col = tex(uv0).rgb;
                float v = burnField(uv0, aspect) - burnEdge(p);
                col *= 1.0 - 0.75 * (1.0 - smoothstep(0.0, 0.14, v)) * step(0.0, v);
                float fire = 1.0 - smoothstep(0.0, 0.07, abs(v));
                col += mix(vec3(1.0, 0.32, 0.04), vec3(1.0, 0.93, 0.72), 1.0 - smoothstep(0.0, 0.025, abs(v))) * fire * 1.7;
                col += vec3(0.28, 0.09, 0.0) * k * (0.7 + 0.3 * rnd(vec2(floor(p * 24.0), 1.0)));
            } else if (isT(51.0)) {
                // Anamorphic flare: a long blue streak with halo and lens ghosts sweeps across.
                col = mtex(uv0).rgb;
                float cx = mix(-0.3, 1.3, easeF(p)); float cy = 0.56;
                float dx = (uv0.x - cx) * aspect; float dy = uv0.y - cy;
                float streak = exp(-dy * dy * 1800.0) * exp(-abs(dx) * 0.9);
                float halo = exp(-(dx * dx + dy * dy) * 30.0);
                float gh = 0.0;
                for (int i = 1; i <= 3; i++) {
                    float fi = float(i);
                    vec2 gp = vec2(0.5) - (vec2(cx, cy) - 0.5) * (0.45 * fi);
                    float gl = length((uv0 - gp) * vec2(aspect, 1.0));
                    gh += exp(-pow(gl - 0.045 * fi, 2.0) * 3000.0) * 0.35 + exp(-gl * gl * 300.0) * 0.12;
                }
                col += max(col - 0.7, 0.0) * k * 1.5;
                col += (vec3(0.3, 0.55, 1.0) * streak * 2.4 + vec3(1.0, 0.9, 0.8) * halo * 1.4 + vec3(0.55, 0.75, 1.0) * gh) * k * 1.3 + vec3(k * k * 0.15);
            } else if (isT(52.0)) {
                // Lens warp: the outgoing shot bulges like a fisheye, the next one snaps in from a pinch.
                vec2 q = c * vec2(aspect, 1.0);
                float d2 = dot(q, q);
                float a = k * k * 1.8;
                float f = 1.0 + a * d2 * 2.0;
                float z = inc ? mix(1.3, 1.0, easeF(p)) : mix(1.0, 1.3, easeF(p));
                vec2 base = inc ? c * f : c / f;
                vec2 u1 = base / z + 0.5;
                vec2 u2 = base * (1.0 + 0.025 * k) / z + 0.5;
                vec2 u3 = base * (1.0 - 0.025 * k) / z + 0.5;
                col = vec3(mtex(u2).r, mtex(u1).g, mtex(u3).b);
                col *= 1.0 - k * 0.9 * d2;
            } else if (isT(53.0)) {
                // Datamosh: blocks smear along their own motion, colours posterize and swap.
                vec2 g = vec2(20.0, 12.0);
                vec2 bk = floor(uv0 * g);
                vec2 f = moshFlow(uv0, k);
                col = mtex(uv0 - f * (1.0 + rnd(bk + 2.0) * 1.5)).rgb;
                float lv = mix(64.0, 14.0, k);
                col = floor(col * lv + 0.5) / lv;
                vec2 fr = fract(uv0 * g);
                col *= 1.0 - 0.18 * k * clamp(step(fr.x, 0.06) + step(fr.y, 0.06), 0.0, 1.0);
                float sw = step(0.9, rnd(bk + floor(p * 10.0)));
                col = mix(col, col.brg * vec3(1.1, 0.9, 1.2), sw * k * 0.45);
            } else if (isT(54.0)) {
                // VHS rewind: the picture rolls, tracking bands tear across, colours bleed.
                float dir = inc ? -1.0 : 1.0;
                float roll = dir * k * k * 0.9;
                uv.y = fract(uv0.y + roll);
                float by = fract(p * 1.7);
                float band = exp(-pow((uv0.y - by) * 14.0, 2.0));
                uv.x += (rnd(vec2(floor(uv0.y * uSize.y / 2.0), floor(p * 60.0))) - 0.5) * 0.03 * k + band * 0.04 * k + sin(uv0.y * 120.0 + p * 40.0) * 0.003 * k;
                col = vec3(tex(uv + vec2(0.006 * k, 0.0)).r, tex(uv).g, tex(uv - vec2(0.004 * k, 0.0)).b);
                col = mix(col, vec3(lum(col)), 0.4 * k);
                col += (rnd(uv0 * uSize + p * 100.0) - 0.5) * 0.25 * k + band * 0.35 * k;
                col *= 1.0 - 0.18 * k * step(0.5, fract(uv0.y * uSize.y / 3.0));
                float wr = fract(uv0.y + roll);
                col *= mix(1.0, smoothstep(0.0, 0.025, min(wr, 1.0 - wr)), step(0.05, k));
                col *= mix(vec3(1.0), vec3(1.06, 0.94, 1.1), k);
            } else if (isT(55.0)) {
                // Letterbox close: cinema bars close to black and open on the next shot, with a slow push.
                float z = inc ? mix(1.12, 1.0, easeF(p)) : mix(1.0, 1.12, easeF(p));
                col = mtex(c / z + 0.5).rgb;
                float h = 0.5 * pow(k, 2.5);
                float bar = max(1.0 - smoothstep(h - 0.003, h + 0.003, uv0.y), smoothstep(1.0 - h - 0.003, 1.0 - h + 0.003, uv0.y));
                float edge = (1.0 - smoothstep(0.0, 0.004, abs(uv0.y - h))) + (1.0 - smoothstep(0.0, 0.004, abs(uv0.y - (1.0 - h))));
                col = col * (1.0 - bar) + vec3(0.95, 0.72, 0.35) * edge * 0.55 * k;
            } else if (isT(56.0)) {
                // Aperture: six iris blades close to black and open on the next shot.
                col = mtex(uv0).rgb;
                vec2 q = c * vec2(aspect, 1.0);
                float rr = length(q);
                float th = atan(q.y, q.x) + p * 1.2 + 3.14159265;
                float seg = 1.0471976;
                float hx = rr * cos(mod(th, seg) - seg * 0.5);
                float R = 1.08 * pow(1.0 - k, 0.8);
                float inside = 1.0 - smoothstep(R - 0.004, R + 0.004, hx);
                float bl = fract((th + hx * 2.0) / seg);
                vec3 metal = vec3(0.07, 0.07, 0.08) + vec3(0.13) * smoothstep(0.92, 1.0, bl) + vec3(0.04) * (1.0 - rr);
                col = mix(metal, col, inside);
                col += vec3(0.6, 0.5, 0.35) * (1.0 - smoothstep(0.0, 0.01, abs(hx - R))) * 0.5 * step(0.02, k);
            } else if (isT(57.0)) {
                // Smoke: drifting haze rolls through and clears on the next shot.
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 4; i++) {
                    float a = float(i) * 1.5707963;
                    acc += mtex(uv0 + vec2(cos(a), sin(a)) * 0.006 * k).rgb;
                }
                col = acc / 4.0;
                float haze = k * (0.55 + 0.45 * fbm(uv0 * vec2(aspect, 1.0) * 4.0 - vec2(p * 1.2, p * 0.4)));
                float edge = 1.0 - smoothstep(0.0, 0.25, abs(mix(-0.35, 1.35, p) - smokeField(uv0, aspect, p)));
                col = mix(col, vec3(0.86, 0.88, 0.92), clamp(haze * 1.1 + edge * 0.35 * k, 0.0, 0.92));
            } else if (isT(58.0)) {
                // Shatter: the shot cracks, then falls away in spinning shards.
                if (inc) col = tex(uv0).rgb * mix(0.55, 1.0, p);
                else {
                    vec4 sh = shatter(uv0, p);
                    if (sh.w > 0.5) {
                        col = tex(sh.xy).rgb * (1.0 - 0.3 * (sh.w - 1.0));
                        float crack = (1.0 - smoothstep(0.0, 0.004, sh.z)) * smoothstep(0.0, 0.06, p);
                        col = mix(col, vec3(0.9, 0.95, 1.0), crack * 0.8);
                    }
                }
            } else if (isT(59.0)) {
                // Kaleidoscope: the frame folds into a turning six-way mirror and unfolds as the next shot.
                vec2 q = c * vec2(aspect, 1.0);
                float rr = length(q);
                float th = atan(q.y, q.x) + p * 2.5;
                float seg = 1.0471976;
                float tf = abs(mod(th, seg) - seg * 0.5);
                vec2 kq = vec2(cos(tf), sin(tf)) * rr * (1.0 + k * 0.5);
                col = mix(mtex(uv0).rgb, mtex(kq / vec2(aspect, 1.0) + 0.5).rgb, smoothstep(0.05, 0.45, k));
                col = mix(vec3(lum(col)), col, 1.0 + 0.4 * k);
                col += vec3(1.0, 0.9, 0.75) * exp(-rr * rr * 20.0) * k * 0.5;
            } else if (isT(60.0)) {
                // Cube turn: the shot turns away as a side of a 3D cube, the next side turns in.
                vec3 hf = boxFace(uv0, aspect, p, inc ? 1.0 : 0.0);
                float ang = easeF(p) * 1.5707963;
                if (hf.z > 0.5) col = tex(hf.xy).rgb * (inc ? sin(ang) * 0.55 + 0.45 : cos(ang) * 0.55 + 0.45);
            } else if (isT(61.0)) {
                // Card flip: the frame flips over like a card, with the next shot on its back.
                vec3 hf = flipFace(uv0, aspect, p);
                bool mine = inc ? p >= 0.5 : p < 0.5;
                if (hf.z > 0.5 && mine) col = tex(hf.xy).rgb * (abs(cos(easeF(p) * 3.14159265)) * 0.5 + 0.5);
            } else if (isT(62.0)) {
                // Sun rays: light breaks through the bright parts of the frame in god rays.
                col = mtex(uv0).rgb;
                vec2 L = vec2(mix(0.85, 0.6, p), 0.85);
                vec2 dl = L - uv0;
                float acc = 0.0; float w = 1.0;
                float jit = rnd(uv0 * uSize);
                for (int i = 0; i < 16; i++) {
                    vec2 sp = uv0 + dl * ((float(i) + jit) / 16.0) * 0.6 * k;
                    acc += max(lum(mtex(sp).rgb) - 0.5, 0.0) * w;
                    w *= 0.93;
                }
                float n = vnoise(vec2(atan(dl.y, dl.x) * 14.0, p * 3.0));
                vec3 warm = vec3(1.0, 0.78, 0.45);
                col += warm * (acc * 0.2 + 0.45 * pow(n, 3.0) * exp(-length(dl * vec2(aspect, 1.0)) * 2.0)) * k * 1.3 + warm * k * k * 0.18;
            } else if (isT(63.0)) {
                // Pixel sort: bright pixels drip down the frame in sorted streaks.
                col = mtex(uv0).rgb;
                float bl = lum(col);
                float cw = floor(uv0.x * uSize.x / 2.0);
                float len = k * (0.15 + 0.35 * rnd(vec2(cw, 3.0)));
                float thr = mix(1.0, 0.42, k);
                for (int i = 1; i <= 10; i++) {
                    vec3 v = mtex(uv0 + vec2(0.0, len * float(i) / 10.0)).rgb;
                    float l = lum(v);
                    if (l > bl && l > thr) { col = v; bl = l; }
                }
            } else if (isT(64.0)) {
                // Neon edges: the shot turns into glowing neon outlines, then the next shot lights up.
                col = mtex(uv0).rgb;
                vec2 px = 1.5 / uSize;
                float tl = lum(mtex(uv0 + vec2(-px.x, px.y)).rgb); float tc = lum(mtex(uv0 + vec2(0.0, px.y)).rgb); float tr = lum(mtex(uv0 + px).rgb);
                float ml = lum(mtex(uv0 - vec2(px.x, 0.0)).rgb); float mr = lum(mtex(uv0 + vec2(px.x, 0.0)).rgb);
                float bl = lum(mtex(uv0 - px).rgb); float bc = lum(mtex(uv0 - vec2(0.0, px.y)).rgb); float br = lum(mtex(uv0 + vec2(px.x, -px.y)).rgb);
                float gx = (tr + 2.0 * mr + br) - (tl + 2.0 * ml + bl);
                float gy = (tl + 2.0 * tc + tr) - (bl + 2.0 * bc + br);
                float e = smoothstep(0.08, 0.35, length(vec2(gx, gy)));
                float hue = fract(uv0.x * 0.6 + uv0.y * 0.3 + p * 0.8);
                vec3 neon = 0.5 + 0.5 * cos(6.2831853 * (hue + vec3(0.0, 0.33, 0.67)));
                col = mix(col, col * 0.15 + neon * e * 2.2, smoothstep(0.0, 0.6, k));
            } else if (isT(65.0)) {
                // Ember drift: the shot burns away into glowing embers that float upward.
                float th = emberField(uv0, aspect);
                float e = mix(-0.1, 1.1, p);
                float ahead = th - e;
                uv.y += (1.0 - smoothstep(0.0, 0.15, ahead)) * step(0.0, ahead) * 0.01 * sin(uv0.x * 80.0 + p * 30.0);
                col = tex(uv).rgb;
                vec2 g = floor(uv0 * uSize / 2.5);
                float flick = 0.6 + 0.4 * rnd(g + floor(p * 30.0));
                float ember = 1.0 - smoothstep(0.0, 0.035, abs(ahead));
                col *= 1.0 - 0.6 * (1.0 - smoothstep(0.0, 0.1, ahead)) * step(0.0, ahead);
                col += vec3(1.0, 0.45, 0.1) * ember * flick * 1.8;
                float sp = step(0.994, rnd(floor((uv0 - vec2(0.0, p * 0.35)) * uSize / 4.0)));
                col += vec3(1.0, 0.55, 0.15) * sp * (1.0 - smoothstep(0.0, 0.3, -ahead)) * step(ahead, 0.0) * 2.0;
            } else {
                col = tex(uv0).rgb;
            }
            float rv = revealC(uv0);
            float al = uMode > 1.5 ? 1.0 - rv : (uMode > 0.5 ? rv : 1.0);
            return vec4(clamp(col, 0.0, 1.0), al);
        }

        float reveal(vec2 uv) {
            if (uType > 49.5) return revealC(uv);
            float p = uP;
            float aspect = uSize.x / uSize.y;
            if (isT(1.0)) return p;
            if (isT(2.0) || isT(3.0)) return sstep(0.48, 0.52, p);
            if (isT(4.0)) return sstep(0.35, 0.65, p);
            if (isT(5.0) || isT(6.0)) return sstep(0.3, 0.7, p);
            if (isT(7.0)) return sstep(0.1, 0.9, p);
            if (isT(36.0) || isT(37.0) || isT(38.0)) return sstep(0.4, 0.6, p);
            if (isT(41.0)) return sstep(0.45, 0.55, p);
            if (isT(42.0) || isT(43.0) || isT(44.0) || isT(45.0)) return sstep(0.25, 0.75, p);
            float e = mix(-0.05, 1.05, p);
            if (isT(10.0)) return cover(1.0 - uv.x, e, 0.02);
            if (isT(11.0)) return cover(uv.x, e, 0.02);
            if (isT(12.0)) return cover(uv.y, e, 0.02);
            if (isT(13.0)) return cover(1.0 - uv.y, e, 0.02);
            if (isT(14.0)) return cover((uv.x + 1.0 - uv.y) * 0.5, e, 0.03);
            vec2 c = (uv - 0.5) * vec2(aspect, 1.0);
            if (isT(15.0)) { float md = length(vec2(0.5 * aspect, 0.5)); return cover(length(c), p * md * 1.06, 0.012); }
            if (isT(16.0)) { float a = fract(atan(c.x, c.y) / 6.2831853 + 1.0); return cover(a, mix(-0.02, 1.02, p), 0.006); }
            if (isT(17.0)) return cover(fract(uv.y * 10.0), e, 0.02);
            if (isT(18.0)) return cover(abs(uv.x - 0.5) * 2.0, e, 0.02);
            if (isT(19.0)) return step(rnd(floor(uv * vec2(14.0 * aspect, 14.0))) * 0.98 + 0.01, p);
            if (isT(20.0)) { float v = mix(uv.x, fbm(uv * vec2(aspect, 1.0) * 3.0), 0.6); return cover(v, mix(-0.15, 1.15, p), 0.05); }
            if (isT(21.0)) return step(rnd(floor(uv * uSize / 3.0)) * 0.98 + 0.01, p);
            if (isT(40.0)) { float b = rnd(vec2(floor(uv.y * 18.0), floor(p * 12.0))); return step(0.5 + (b - 0.5) * 0.4, p); }
            return 1.0;
        }

        vec4 shade(vec2 uv0) {
            if (uType < 0.5) return tex(uv0);
            if (uType > 49.5) return cine(uv0);
            float p = uP;
            float k = 1.0 - abs(2.0 * p - 1.0);
            bool inc = uSide > 0.5;
            float aspect = uSize.x / uSize.y;
            vec2 uv = uv0;
            vec2 c = uv - 0.5;
            if (isT(36.0)) { float s = inc ? mix(0.5, 1.0, easeF(p)) : mix(1.0, 2.5, easeF(p)); uv = c / s + 0.5; }
            if (isT(7.0)) { float s = inc ? mix(1.03, 1.0, easeF(p)) : mix(1.0, 1.03, easeF(p)); uv = c / s + 0.5; }
            if (isT(37.0)) { float s = inc ? mix(2.5, 1.0, easeF(p)) : mix(1.0, 0.5, easeF(p)); uv = c / s + 0.5; }
            if (isT(38.0)) {
                float ang = inc ? -(1.0 - easeF(p)) * 3.14159 : easeF(p) * 3.14159;
                vec2 q = c * vec2(aspect, 1.0);
                float cs = cos(ang); float sn = sin(ang);
                q = vec2(q.x * cs - q.y * sn, q.x * sn + q.y * cs);
                uv = q / vec2(aspect, 1.0) / (1.0 + k * 0.6) + 0.5;
            }
            if (isT(43.0)) { float d = length(c * vec2(aspect, 1.0)); uv += c / max(d, 0.001) * sin(d * 40.0 - p * 30.0) * 0.02 * k; }
            if (isT(45.0)) {
                vec2 q = c * vec2(aspect, 1.0); float d = length(q);
                float a = k * 4.0 * (1.0 - smoothstep(0.0, 0.7, d));
                float cs = cos(a); float sn = sin(a);
                q = vec2(q.x * cs - q.y * sn, q.x * sn + q.y * cs);
                uv = q / vec2(aspect, 1.0) + 0.5;
            }
            if (isT(41.0)) { float cells = mix(200.0, 12.0, k); vec2 cl = vec2(cells * aspect, cells); uv = (floor(uv * cl) + 0.5) / cl; }
            if (isT(40.0)) {
                float blk = rnd(vec2(floor(uv.y * 24.0), floor(p * 20.0)));
                if (blk > 1.0 - k * 0.6) uv.x += (rnd(vec2(blk, p)) - 0.5) * 0.25 * k;
            }
            vec3 col;
            if (isT(5.0) || isT(7.0) || isT(34.0) || isT(35.0) || isT(36.0) || isT(37.0) || isT(38.0) || isT(44.0)) {
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 12; i++) {
                    float f = float(i) / 11.0 - 0.5;
                    vec2 o;
                    if (isT(34.0) || isT(35.0)) o = vec2(f * 0.14 * k, 0.0);
                    else if (isT(5.0)) o = vec2(cos(f * 40.0), sin(f * 40.0)) * 0.014 * k * (0.3 + abs(f));
                    else if (isT(7.0)) o = vec2(cos(f * 40.0), sin(f * 40.0)) * 0.003 * k * (0.3 + abs(f));
                    else o = (uv - 0.5) * f * 0.2 * k * (isT(44.0) ? 2.0 : 1.0);
                    acc += mtex(uv + o).rgb;
                }
                col = acc / 12.0;
            } else if (isT(40.0) || isT(42.0)) {
                float amt = (isT(40.0) ? 0.03 : 0.025) * k;
                vec2 dir = isT(42.0) ? (uv - 0.5) * 2.0 : vec2(1.0, 0.0);
                col = vec3(mtex(uv + dir * amt).r, mtex(uv).g, mtex(uv - dir * amt).b);
            } else {
                col = mtex(uv).rgb;
            }
            float dip = inc ? clamp((1.0 - p) * 2.0, 0.0, 1.0) : clamp(p * 2.0, 0.0, 1.0);
            if (isT(2.0)) col *= 1.0 - dip;
            if (isT(3.0)) col = mix(col, vec3(1.0), dip);
            if (isT(4.0)) col += vec3(k * k * 1.2);
            if (isT(6.0)) {
                float x = uv0.x + uv0.y * 0.3 - mix(-0.3, 1.6, p);
                float band = exp(-(x * 3.0) * (x * 3.0));
                col += vec3(1.0, 0.55, 0.2) * band * 1.4 * k + vec3(1.0, 0.8, 0.6) * k * k * 0.5;
            }
            if (isT(44.0)) col += vec3(k * k * 0.8);
            if (isT(40.0)) col *= 1.0 - 0.25 * k * step(0.5, fract(uv0.y * uSize.y * 0.5));
            if (isT(21.0)) { float r = rnd(floor(uv0 * uSize / 3.0)) * 0.98 + 0.01; col += vec3(1.0, 0.9, 0.7) * (1.0 - smoothstep(0.0, 0.05, abs(r - p))) * 1.5; }
            if (isT(20.0)) {
                float v = mix(uv0.x, fbm(uv0 * vec2(aspect, 1.0) * 3.0), 0.6);
                col += vec3(0.25, 0.1, 0.35) * (1.0 - smoothstep(0.0, 0.08, abs(v - mix(-0.15, 1.15, p)))) * k;
            }
            float rv = reveal(uv0);
            float a = uMode > 1.5 ? 1.0 - rv : (uMode > 0.5 ? rv : 1.0);
            return vec4(clamp(col, 0.0, 1.0), a);
        }
    """

    /**
     * The preview shader for one transition. The type is built in as a constant, so the shader compiler
     * drops every other transition's code (one shader with all of them is too large for Android).
     */
    fun agsl(t: TransitionType): String = AGSL.replace("uniform float uType;", "const float uType = ${t.id};")

    /** A compiled preview shader for [t], or null if this phone can't build it (the preview then fades). */
    fun shaderFor(t: TransitionType): android.graphics.RuntimeShader? =
        if (android.os.Build.VERSION.SDK_INT < 33) null else try { android.graphics.RuntimeShader(agsl(t)) } catch (e: Throwable) {
            android.util.Log.w("TransitionPreview", "Shader for ${t.name} unavailable: ${e.message}"); null
        }

    private const val AGSL = """
        uniform shader content;
        uniform float2 uSize;
        uniform float uType;
        uniform float uP;
        uniform float uSide;
        uniform float uMode;
        vec4 tex(vec2 uv) { return vec4(content.eval(vec2(clamp(uv.x, 0.0, 1.0), 1.0 - clamp(uv.y, 0.0, 1.0)) * uSize)); }
    """ + CORE + """
        half4 main(float2 fc) {
            vec4 c = shade(vec2(fc.x / uSize.x, 1.0 - fc.y / uSize.y));
            return half4(c.rgb * c.a, c.a);
        }
    """

    const val GLSL_VERTEX = """
        attribute vec4 aFramePosition;
        varying vec2 vTexSamplingCoord;
        void main() {
            gl_Position = aFramePosition;
            vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
        }
    """

    const val GLSL_FRAGMENT = """
        precision highp float;
        uniform sampler2D uTexSampler;
        uniform vec2 uSize;
        uniform float uType;
        uniform float uP;
        uniform float uSide;
        uniform float uMode;
        varying vec2 vTexSamplingCoord;
        vec4 tex(vec2 uv) { return texture2D(uTexSampler, clamp(uv, 0.0, 1.0)); }
    """ + CORE + """
        void main() { gl_FragColor = shade(vTexSamplingCoord); }
    """
}
