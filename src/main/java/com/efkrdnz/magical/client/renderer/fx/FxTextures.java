package com.efkrdnz.magical.client.renderer.fx;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.StampId;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Procedurally generated textures registered at client setup:
 * <ul>
 *   <li>{@link #NOISE}: 256x256 tileable atlas, R value noise, G fbm, B worley F1, A blue-ish noise.</li>
 *   <li>{@link #EMBLEMS}: 1024x1024 SDF atlas of 16x16 cells (64px); cells 0..31 are stamps, 32+ emblems.</li>
 * </ul>
 * Generated in Java so the build has no external tooling dependency; the stroke tables below are
 * the single source of every sigil identity mark.
 */
public final class FxTextures {
    public static final ResourceLocation NOISE = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "effect/noise");
    public static final ResourceLocation EMBLEMS = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "effect/sigil_emblems");
    private static final int NOISE_SIZE = 256;
    private static final int CELL = 64;
    private static final int CELLS = 16;
    private static boolean registered;

    private FxTextures() {}

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getTextureManager().register(NOISE, new DynamicTexture(buildNoise()));
        minecraft.getTextureManager().register(EMBLEMS, new DynamicTexture(buildEmblems()));
        MagicalMod.LOGGER.info("Magical FX textures generated");
    }

    // ------------------------------------------------------------------ noise

    private static NativeImage buildNoise() {
        NativeImage image = new NativeImage(NOISE_SIZE, NOISE_SIZE, false);
        int period = 8;
        for (int y = 0; y < NOISE_SIZE; y++) {
            for (int x = 0; x < NOISE_SIZE; x++) {
                float fx = x / (float) NOISE_SIZE;
                float fy = y / (float) NOISE_SIZE;
                float value = valueNoise(fx * period, fy * period, period, 11);
                float fbm = 0.0F;
                float amp = 0.5F;
                int p = period;
                for (int i = 0; i < 4; i++) {
                    fbm += valueNoise(fx * p, fy * p, p, 31 + i * 7) * amp;
                    p *= 2;
                    amp *= 0.5F;
                }
                float worley = worleyF1(fx, fy, 6, 97);
                float blue = valueNoise(fx * 64, fy * 64, 64, 53) * 0.5F + hash(x * 7 + y * 131 + 5) * 0.5F;
                image.setPixel(x, y, pack(value, fbm, worley, blue));
            }
        }
        return image;
    }

    private static int pack(float r, float g, float b, float a) {
        int ir = Mth.clamp(Math.round(r * 255.0F), 0, 255);
        int ig = Mth.clamp(Math.round(g * 255.0F), 0, 255);
        int ib = Mth.clamp(Math.round(b * 255.0F), 0, 255);
        int ia = Mth.clamp(Math.round(a * 255.0F), 0, 255);
        return (ia << 24) | (ib << 16) | (ig << 8) | ir; // NativeImage ABGR
    }

    private static float hash(int n) {
        n = (n ^ 61) ^ (n >>> 16);
        n *= 9;
        n = n ^ (n >>> 4);
        n *= 0x27d4eb2d;
        n = n ^ (n >>> 15);
        return (n & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    private static float lattice(int x, int y, int period, int seed) {
        int px = Math.floorMod(x, period);
        int py = Math.floorMod(y, period);
        return hash(px * 1973 + py * 9277 + seed * 26699);
    }

    private static float valueNoise(float x, float y, int period, int seed) {
        int xi = Mth.floor(x);
        int yi = Mth.floor(y);
        float fx = x - xi;
        float fy = y - yi;
        float ux = fx * fx * (3.0F - 2.0F * fx);
        float uy = fy * fy * (3.0F - 2.0F * fy);
        float a = lattice(xi, yi, period, seed);
        float b = lattice(xi + 1, yi, period, seed);
        float c = lattice(xi, yi + 1, period, seed);
        float d = lattice(xi + 1, yi + 1, period, seed);
        return Mth.lerp(uy, Mth.lerp(ux, a, b), Mth.lerp(ux, c, d));
    }

    private static float worleyF1(float x, float y, int cells, int seed) {
        float px = x * cells;
        float py = y * cells;
        int cx = Mth.floor(px);
        int cy = Mth.floor(py);
        float best = 10.0F;
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                int gx = cx + ox;
                int gy = cy + oy;
                float jx = lattice(gx, gy, cells, seed);
                float jy = lattice(gx, gy, cells, seed + 1);
                float dx = gx + jx - px;
                float dy = gy + jy - py;
                best = Math.min(best, dx * dx + dy * dy);
            }
        }
        return Mth.clamp(Mth.sqrt(best), 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------ emblems

    private static NativeImage buildEmblems() {
        NativeImage image = new NativeImage(CELL * CELLS, CELL * CELLS, false);
        for (int i = 0; i < CELLS * CELLS; i++) {
            image.fillRect((i % CELLS) * CELL, (i / CELLS) * CELL, CELL, CELL, 0xFF000000);
        }
        for (StampId stamp : StampId.values()) {
            rasterize(image, stamp.atlasCell(), stampStrokes(stamp), 0.11F);
        }
        for (EmblemId emblem : EmblemId.values()) {
            rasterize(image, emblem.atlasCell(), emblemStrokes(emblem), 0.08F);
        }
        return image;
    }

    /** Signed distance of a stroke list; 0.5 at the ink edge, brighter inside. */
    private static void rasterize(NativeImage image, int cell, String strokes, float halfWidth) {
        List<float[]> prims = parse(strokes);
        int ox = (cell % CELLS) * CELL;
        int oy = (cell / CELLS) * CELL;
        float spread = 0.5F;
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                // cell content occupies +-0.42 of the cell (the shader samples q*0.42)
                // shader samples v = cy + 0.5 + q.y * 0.42, so larger image rows are q.y = +1 ("up")
                float px = ((x + 0.5F) / CELL - 0.5F) / 0.42F;
                float py = ((y + 0.5F) / CELL - 0.5F) / 0.42F;
                float d = 10.0F;
                for (float[] p : prims) {
                    d = Math.min(d, distance(p, px, py, halfWidth));
                }
                float sdf = Mth.clamp(0.5F - d / spread, 0.0F, 1.0F);
                int v = Mth.clamp(Math.round(sdf * 255.0F), 0, 255);
                image.setPixel(ox + x, oy + y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
    }

    private static float distance(float[] p, float x, float y, float hw) {
        int type = (int) p[0];
        switch (type) {
            case 0: { // circle outline cx cy r
                return Math.abs(Mth.sqrt((x - p[1]) * (x - p[1]) + (y - p[2]) * (y - p[2])) - p[3]) - hw;
            }
            case 1: { // segment x1 y1 x2 y2
                return segment(x, y, p[1], p[2], p[3], p[4]) - hw;
            }
            case 2: { // polygon outline n r rot
                int n = (int) p[1];
                float best = 10.0F;
                for (int i = 0; i < n; i++) {
                    float a0 = (i / (float) n) * Mth.TWO_PI + p[3] * Mth.DEG_TO_RAD + Mth.HALF_PI;
                    float a1 = ((i + 1) / (float) n) * Mth.TWO_PI + p[3] * Mth.DEG_TO_RAD + Mth.HALF_PI;
                    best = Math.min(best, segment(x, y, Mth.cos(a0) * p[2], Mth.sin(a0) * p[2], Mth.cos(a1) * p[2], Mth.sin(a1) * p[2]));
                }
                return best - hw;
            }
            case 3: { // arc cx cy r a0 a1 (degrees, ccw)
                float dx = x - p[1];
                float dy = y - p[2];
                float ang = (float) Math.toDegrees(Math.atan2(dy, dx));
                float a0 = p[4];
                float a1 = p[5];
                float rel = Mth.positiveModulo(ang - a0, 360.0F);
                float span = Mth.positiveModulo(a1 - a0, 360.0F);
                if (span == 0.0F) {
                    span = 360.0F;
                }
                if (rel <= span) {
                    return Math.abs(Mth.sqrt(dx * dx + dy * dy) - p[3]) - hw;
                }
                float ex = p[1] + Mth.cos(a0 * Mth.DEG_TO_RAD) * p[3];
                float ey = p[2] + Mth.sin(a0 * Mth.DEG_TO_RAD) * p[3];
                float fx = p[1] + Mth.cos(a1 * Mth.DEG_TO_RAD) * p[3];
                float fy = p[2] + Mth.sin(a1 * Mth.DEG_TO_RAD) * p[3];
                return Math.min(Mth.sqrt((x - ex) * (x - ex) + (y - ey) * (y - ey)), Mth.sqrt((x - fx) * (x - fx) + (y - fy) * (y - fy))) - hw;
            }
            case 4: { // filled disc cx cy r
                return Mth.sqrt((x - p[1]) * (x - p[1]) + (y - p[2]) * (y - p[2])) - p[3];
            }
            case 5: { // star outline n ro ri rot
                int n = (int) p[1];
                float best = 10.0F;
                for (int i = 0; i < n; i++) {
                    float a0 = (i / (float) n) * Mth.TWO_PI + p[4] * Mth.DEG_TO_RAD + Mth.HALF_PI;
                    float a1 = ((i + 0.5F) / n) * Mth.TWO_PI + p[4] * Mth.DEG_TO_RAD + Mth.HALF_PI;
                    float a2 = ((i + 1) / (float) n) * Mth.TWO_PI + p[4] * Mth.DEG_TO_RAD + Mth.HALF_PI;
                    best = Math.min(best, segment(x, y, Mth.cos(a0) * p[2], Mth.sin(a0) * p[2], Mth.cos(a1) * p[3], Mth.sin(a1) * p[3]));
                    best = Math.min(best, segment(x, y, Mth.cos(a1) * p[3], Mth.sin(a1) * p[3], Mth.cos(a2) * p[2], Mth.sin(a2) * p[2]));
                }
                return best - hw;
            }
            case 6: { // rectangle outline cx cy hw hh
                float dx = Math.abs(x - p[1]) - p[3];
                float dy = Math.abs(y - p[2]) - p[4];
                float outside = Mth.sqrt(Math.max(dx, 0.0F) * Math.max(dx, 0.0F) + Math.max(dy, 0.0F) * Math.max(dy, 0.0F));
                float inside = Math.min(Math.max(dx, dy), 0.0F);
                return Math.abs(outside + inside) - hw;
            }
            default:
                return 10.0F;
        }
    }

    private static float segment(float x, float y, float ax, float ay, float bx, float by) {
        float pax = x - ax, pay = y - ay, bax = bx - ax, bay = by - ay;
        float h = Mth.clamp((pax * bax + pay * bay) / Math.max(bax * bax + bay * bay, 1.0E-6F), 0.0F, 1.0F);
        float dx = pax - bax * h, dy = pay - bay * h;
        return Mth.sqrt(dx * dx + dy * dy);
    }

    /**
     * Mini stroke language, ';' separated: C cx,cy,r circle | L x1,y1,x2,y2 line | P n,r,rot polygon
     * | A cx,cy,r,a0,a1 arc | D cx,cy,r filled disc | S n,ro,ri,rot star | R cx,cy,hw,hh rectangle.
     */
    private static List<float[]> parse(String strokes) {
        List<float[]> out = new ArrayList<>();
        for (String part : strokes.split(";")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            char op = s.charAt(0);
            String[] nums = s.substring(1).trim().split(",");
            float[] v = new float[nums.length + 1];
            v[0] = switch (op) {
                case 'C' -> 0;
                case 'L' -> 1;
                case 'P' -> 2;
                case 'A' -> 3;
                case 'D' -> 4;
                case 'S' -> 5;
                case 'R' -> 6;
                default -> 99;
            };
            for (int i = 0; i < nums.length; i++) {
                v[i + 1] = Float.parseFloat(nums[i].trim());
            }
            out.add(v);
        }
        return out;
    }

    private static String stampStrokes(StampId stamp) {
        return switch (stamp) {
            case NEEDLE -> "L0,-0.9,0,0.9;L-0.25,0.55,0,0.9;L0.25,0.55,0,0.9";
            case FOOTPRINT -> "C0,-0.25,0.42;D-0.35,0.55,0.16;D0,0.68,0.16;D0.35,0.55,0.16";
            case KITE -> "L0,0.9,0.55,0;L0.55,0,0,-0.9;L0,-0.9,-0.55,0;L-0.55,0,0,0.9;L0,0.9,0,-0.9";
            case TEARDROP -> "A0,-0.2,0.5,200,340;L-0.47,-0.03,0,0.9;L0.47,-0.03,0,0.9";
            case THORN -> "L-0.7,-0.6,0.7,0.6;L-0.7,-0.6,-0.1,-0.9;L0.1,0.2,0.75,-0.2;L-0.4,0.3,-0.05,0.85";
            case SNOWFLAKE -> "L0,-0.9,0,0.9;L-0.78,-0.45,0.78,0.45;L-0.78,0.45,0.78,-0.45;L-0.3,0.6,0,0.9;L0.3,0.6,0,0.9;L-0.3,-0.6,0,-0.9;L0.3,-0.6,0,-0.9";
            case LEAF -> "A-0.45,0,0.9,-50,50;A0.45,0,0.9,130,230;L0,-0.7,0,0.7";
            case FEATHER -> "L0,-0.9,0,0.9;L0,0.6,-0.45,0.2;L0,0.3,-0.5,-0.1;L0,0,-0.45,-0.4;L0,0.6,0.45,0.2;L0,0.3,0.5,-0.1;L0,0,0.45,-0.4";
            case HEX -> "P6,0.8,0";
            case CROSS -> "L-0.8,0,0.8,0;L0,-0.8,0,0.8";
            case EYE -> "A0,-0.5,1.0,40,140;A0,0.5,1.0,220,320;C0,0,0.28;D0,0,0.12";
            case HOURGLASS -> "L-0.6,-0.8,0.6,-0.8;L-0.6,0.8,0.6,0.8;L-0.6,-0.8,0.6,0.8;L0.6,-0.8,-0.6,0.8";
            case FEATHER_ARC -> "A0,-0.3,1.0,30,150;L0,0.7,-0.3,0.3;L0,0.7,0.3,0.3;L-0.4,0.55,-0.6,0.2;L0.4,0.55,0.6,0.2";
            case KEY -> "C0,0.5,0.35;L0,0.15,0,-0.9;L0,-0.9,0.4,-0.9;L0,-0.55,0.3,-0.55";
            case BONE -> "L-0.6,-0.6,0.6,0.6;C-0.7,-0.7,0.2;C0.7,0.7,0.2;C-0.85,-0.5,0.16;C0.5,0.85,0.16";
            case GEAR -> "C0,0,0.55;C0,0,0.22;L0,0.55,0,0.9;L0,-0.55,0,-0.9;L0.55,0,0.9,0;L-0.55,0,-0.9,0;L0.39,0.39,0.64,0.64;L-0.39,-0.39,-0.64,-0.64;L0.39,-0.39,0.64,-0.64;L-0.39,0.39,-0.64,0.64";
            case ARROW -> "L0,-0.9,0,0.9;L0,0.9,-0.5,0.4;L0,0.9,0.5,0.4";
            case DOT -> "D0,0,0.35";
            case BAR -> "L-0.8,0,0.8,0";
            case CHEVRON -> "L-0.7,-0.4,0,0.5;L0,0.5,0.7,-0.4";
            case WAVE -> "A-0.5,0,0.4,180,360;A0.5,0,0.4,0,180";
            case FLAME -> "A0,-0.3,0.55,180,360;L-0.55,-0.3,0,0.9;L0.55,-0.3,0.15,0.55;L0.15,0.55,0,0.9";
            case DROP -> "A0,-0.25,0.5,200,340;L-0.47,-0.08,0,0.85;L0.47,-0.08,0,0.85";
            case STAR4 -> "S4,0.9,0.3,0";
            case RING -> "C0,0,0.7";
            case TRIANGLE -> "P3,0.85,0";
            case SQUARE -> "P4,0.8,45";
            case DIAMOND -> "P4,0.85,0";
            case CRESCENT -> "A0,0,0.8,60,300;A0.35,0,0.65,80,280";
            case SPIRAL -> "A0,0,0.25,0,180;A0,0,0.5,180,360;A0,0,0.75,0,180";
            case LINK -> "C-0.35,0,0.4;C0.35,0,0.4";
            case TOOTH -> "L-0.5,-0.7,0,0.8;L0,0.8,0.5,-0.7;L-0.5,-0.7,0.5,-0.7";
        };
    }

    private static String emblemStrokes(EmblemId emblem) {
        return switch (emblem) {
            case BLANK -> "C0,0,0.3";
            case EYE -> "A0,-0.55,1.05,42,138;A0,0.55,1.05,222,318;C0,0,0.32;D0,0,0.14";
            case FLAME -> "A0,-0.35,0.55,180,360;L-0.55,-0.35,0,0.95;L0.55,-0.35,0.2,0.5;L0.2,0.5,0,0.95;L0,-0.2,0.15,0.3";
            case DROP -> "A0,-0.3,0.5,200,340;L-0.47,-0.13,0,0.9;L0.47,-0.13,0,0.9;C0,-0.25,0.15";
            case SNOWFLAKE -> "L0,-0.95,0,0.95;L-0.82,-0.47,0.82,0.47;L-0.82,0.47,0.82,-0.47;L-0.3,0.65,0,0.95;L0.3,0.65,0,0.95;L-0.3,-0.65,0,-0.95;L0.3,-0.65,0,-0.95;C0,0,0.2";
            case HOURGLASS -> "L-0.6,-0.85,0.6,-0.85;L-0.6,0.85,0.6,0.85;L-0.6,-0.85,0.6,0.85;L0.6,-0.85,-0.6,0.85;D0,-0.55,0.15";
            case KEY -> "C0,0.55,0.35;C0,0.55,0.12;L0,0.2,0,-0.9;L0,-0.9,0.45,-0.9;L0,-0.5,0.3,-0.5";
            case SKULL -> "A0,0.15,0.7,0,360;C-0.28,0.2,0.18;C0.28,0.2,0.18;L-0.3,-0.55,-0.3,-0.9;L0,-0.55,0,-0.9;L0.3,-0.55,0.3,-0.9;L-0.45,-0.55,0.45,-0.55";
            case SUN -> "C0,0,0.4;L0,0.55,0,0.9;L0,-0.55,0,-0.9;L0.55,0,0.9,0;L-0.55,0,-0.9,0;L0.4,0.4,0.65,0.65;L-0.4,-0.4,-0.65,-0.65;L0.4,-0.4,0.65,-0.65;L-0.4,0.4,-0.65,0.65";
            case MOON -> "A0,0,0.85,55,305;A0.35,0,0.7,75,285";
            case FEATHER -> "L-0.3,-0.9,0.3,0.9;L0,0,-0.55,0.1;L0.1,0.3,-0.4,0.5;L-0.1,-0.3,-0.6,-0.3;L0,0,0.55,-0.1;L0.1,0.3,0.6,0.3;L-0.1,-0.3,0.4,-0.5";
            case ANCHOR -> "C0,0.65,0.2;L0,0.45,0,-0.85;A0,-0.2,0.65,200,340;L-0.5,0,0.5,0";
            case SPIRAL -> "A0,0,0.2,0,180;A0,0,0.4,180,360;A0,0,0.6,0,180;A0,0,0.8,180,360";
            case TREE -> "L0,-0.9,0,0.3;L0,0.3,-0.6,0.8;L0,0.3,0.6,0.8;L0,0,-0.5,0.45;L0,0,0.5,0.45;L0,-0.3,-0.4,0.1;L0,-0.3,0.4,0.1";
            case HAND -> "R0,-0.35,0.45,0.45;L-0.35,0.1,-0.35,0.8;L-0.12,0.1,-0.12,0.9;L0.12,0.1,0.12,0.9;L0.35,0.1,0.35,0.75;L-0.45,-0.2,-0.85,0.3";
            case CROWN -> "L-0.8,-0.6,0.8,-0.6;L-0.8,-0.6,-0.8,0.3;L0.8,-0.6,0.8,0.3;L-0.8,0.3,-0.4,-0.1;L-0.4,-0.1,0,0.7;L0,0.7,0.4,-0.1;L0.4,-0.1,0.8,0.3;D0,0.75,0.1";
            case WING -> "A-0.4,-0.6,1.2,20,100;L-0.4,0.6,0.6,0.3;L-0.4,0.6,0.7,-0.1;L-0.4,0.6,0.5,-0.5;L-0.4,0.6,0.1,-0.7";
            case THORN_CROWN -> "C0,0,0.6;L0.6,0,0.95,0.2;L0.42,0.42,0.55,0.8;L0,0.6,-0.2,0.95;L-0.42,0.42,-0.8,0.55;L-0.6,0,-0.95,-0.2;L-0.42,-0.42,-0.55,-0.8;L0,-0.6,0.2,-0.95;L0.42,-0.42,0.8,-0.55";
            case INFINITY -> "C-0.4,0,0.38;C0.4,0,0.38";
            case GEAR -> "C0,0,0.55;C0,0,0.22;L0,0.55,0,0.9;L0,-0.55,0,-0.9;L0.55,0,0.9,0;L-0.55,0,-0.9,0;L0.39,0.39,0.64,0.64;L-0.39,-0.39,-0.64,-0.64;L0.39,-0.39,0.64,-0.64;L-0.39,0.39,-0.64,0.64";
            case ARROW -> "L-0.7,-0.7,0.7,0.7;L0.7,0.7,0.15,0.7;L0.7,0.7,0.7,0.15;L-0.7,-0.7,-0.2,-0.2";
            case LOCK -> "R0,-0.3,0.55,0.45;A0,0.15,0.4,0,180;L-0.4,0.15,-0.4,0;L0.4,0.15,0.4,0;D0,-0.3,0.12";
            case CHAIN -> "R-0.45,0,0.28,0.18;R0.45,0,0.28,0.18;R0,0,0.28,0.18";
            case HEART -> "A-0.4,0.3,0.4,0,180;A0.4,0.3,0.4,0,180;L-0.8,0.3,0,-0.85;L0.8,0.3,0,-0.85";
            case BELL -> "A0,0.1,0.55,0,180;L-0.55,0.1,-0.7,-0.5;L0.55,0.1,0.7,-0.5;L-0.7,-0.5,0.7,-0.5;D0,-0.7,0.14;L0,0.65,0,0.9";
            case MASK -> "A0,0.1,0.8,0,180;L-0.8,0.1,-0.5,-0.7;L0.8,0.1,0.5,-0.7;L-0.5,-0.7,0.5,-0.7;L-0.5,0.15,-0.15,0.15;L0.15,0.15,0.5,0.15";
            case LEAF -> "A-0.5,0,0.95,-45,45;A0.5,0,0.95,135,225;L0,-0.65,0,0.65;L0,-0.65,0,-0.95";
            case BONE -> "L-0.55,-0.55,0.55,0.55;C-0.68,-0.68,0.22;C0.68,0.68,0.22;C-0.85,-0.45,0.18;C-0.45,-0.85,0.18;C0.45,0.85,0.18;C0.85,0.45,0.18";
            case STAR6 -> "S6,0.9,0.4,0";
            case WAVE -> "A-0.55,0.25,0.35,180,360;A0.15,0.25,0.35,0,180;A-0.55,-0.35,0.35,180,360;A0.15,-0.35,0.35,0,180";
            case MOUNTAIN -> "L-0.9,-0.7,-0.2,0.6;L-0.2,0.6,0.15,0;L0.15,0,0.5,0.75;L0.5,0.75,0.9,-0.7;L-0.9,-0.7,0.9,-0.7";
            case COMET -> "D0.45,0.45,0.28;L0.2,0.2,-0.9,-0.9;L0.4,0.1,-0.7,-0.75;L0.1,0.4,-0.75,-0.7";
            case SEED -> "A0,0,0.6,110,430;L0.35,0.5,0.6,0.85;D0,0,0.15";
            case LANTERN -> "R0,-0.15,0.4,0.45;L-0.55,0.3,0.55,0.3;L-0.55,-0.6,0.55,-0.6;A0,0.45,0.3,0,180;L0,0.75,0,0.95;D0,-0.15,0.14";
            case NEEDLE -> "L0,-0.95,0,0.95;L-0.2,0.6,0,0.95;L0.2,0.6,0,0.95;C0,-0.7,0.12";
            case MIRROR -> "R0,0,0.5,0.75;L-0.5,-0.75,0.5,0.75;L-0.3,0.55,-0.1,0.75";
            case TOOTH -> "L-0.6,0.7,0.6,0.7;L-0.6,0.7,-0.35,-0.85;L0.6,0.7,0.35,-0.85;L-0.35,-0.85,0,-0.4;L0,-0.4,0.35,-0.85";
            case SHELL -> "A0,-0.3,0.9,20,160;L-0.85,0,0,-0.75;L0.85,0,0,-0.75;L-0.5,0.55,0,-0.75;L0.5,0.55,0,-0.75;L0,0.6,0,-0.75";
            case KNOT -> "C-0.3,0.25,0.4;C0.3,0.25,0.4;C0,-0.3,0.4";
            case COMPASS -> "C0,0,0.85;S4,0.75,0.2,0;S4,0.45,0.12,45";
            case VOID_RING -> "C0,0,0.8;C0,0,0.55;D0,0,0.35";
            // DARK. A straw doll with something pushed through its chest, a body sitting over its
            // own shadow, four strokes and a slash, and a thread cut clean in the middle.
            case EFFIGY_DOLL -> "C0,0.6,0.22;L0,0.38,0,-0.5;L-0.55,0.12,0.55,0.12;L0,-0.5,-0.38,-0.9;L0,-0.5,0.38,-0.9;D0,0.05,0.1";
            case CAST_SHADOW -> "C0,0.38,0.42;A0,-0.5,0.78,195,345;D0,-0.5,0.14;L-0.62,-0.28,0.62,-0.28";
            case TALLY -> "L-0.62,-0.62,-0.62,0.62;L-0.3,-0.62,-0.3,0.62;L0.02,-0.62,0.02,0.62;L0.34,-0.62,0.34,0.62;L-0.8,-0.45,0.55,0.5";
            case CUT_THREAD -> "L-0.9,0.5,-0.18,0.08;L0.18,-0.08,0.9,-0.5;L-0.18,0.08,-0.42,-0.24;L0.18,-0.08,0.42,0.26;D0,0,0.08";
            // LIGHT. A cup held up, with something rising out of it.
            case CHALICE -> "A0,0.25,0.55,180,360;L-0.55,0.25,0.55,0.25;L0,-0.3,0,-0.72;L-0.38,-0.8,0.38,-0.8;D0,0.62,0.12";
            case SPLATTER -> "D0,0,0.42;D0.62,0.3,0.14;D-0.55,0.42,0.12;D0.35,-0.62,0.13;D-0.68,-0.25,0.1;L0.42,0.12,0.8,0.3;L-0.4,-0.2,-0.75,-0.42";
            case CHIMNEY -> "L-0.4,-0.9,-0.25,0.1;L0.4,-0.9,0.25,0.1;L-0.4,-0.9,0.4,-0.9;A-0.15,0.4,0.25,0,270;A0.25,0.7,0.2,270,540;A-0.05,0.9,0.12,180,450";
            case CRACKED_STONE -> "C0,0,0.85;L0,0,0.55,0.65;L0,0,-0.7,0.4;L0,0,0.2,-0.8;L0.3,0.35,0.35,0.7";
            case CRUCIBLE -> "L-0.8,0.1,-0.5,-0.8;L0.8,0.1,0.5,-0.8;L-0.5,-0.8,0.5,-0.8;L-0.9,0.1,0.9,0.1;A-0.35,0.45,0.25,180,360;A0,0.55,0.3,180,360;A0.35,0.45,0.25,180,360";
            case HORSESHOE -> "A0,0,0.7,200,340;L-0.66,0.24,-0.66,-0.6;L0.66,0.24,0.66,-0.6;L-0.85,-0.6,-0.47,-0.6;L0.47,-0.6,0.85,-0.6";
            case CHIASMA -> "A-0.45,0.4,0.45,0,180;A-0.45,-0.4,0.45,180,360;A0.45,0.4,0.45,0,180;A0.45,-0.4,0.45,180,360;L-0.9,-0.4,0.9,0.4;D-0.9,0.4,0.1;D0.9,-0.4,0.1";
            case PINCER -> "A-0.35,0,0.65,300,420;A0.35,0,0.65,120,240;D0,0,0.1;L-0.9,-0.6,-0.6,-0.85;L0.9,-0.6,0.6,-0.85";
            case CALIPER -> "L-0.7,-0.8,-0.7,0.8;L-0.7,0.8,-0.4,0.8;L-0.7,-0.8,-0.4,-0.8;L0.7,-0.8,0.7,0.8;L0.7,0.8,0.4,0.8;L0.7,-0.8,0.4,-0.8;L-0.3,0,0.3,0;L-0.3,-0.35,0.3,-0.35;L-0.3,0.35,0.3,0.35";
            case ARMATURE -> "C0,0,0.2;L0,0.2,0,0.85;L0.17,-0.1,0.75,-0.45;L-0.17,-0.1,-0.75,-0.45;C0,0.85,0.12;C0.75,-0.45,0.12;C-0.75,-0.45,0.12";
            case HINGE -> "L0,-0.9,0,0.9;A0,0,0.75,90,270;D0.4,0,0.28;A0,0,0.75,270,450";
            case SCATTER -> "D0,0,0.18;D0.65,0.2,0.1;D-0.6,0.35,0.1;D0.2,-0.7,0.1;D-0.3,-0.65,0.1;D0.55,-0.4,0.08;D-0.7,-0.15,0.08;D0.1,0.75,0.08";
            case LEVIATHAN -> "A0,0,0.75,40,400;L-0.9,-0.1,0.9,-0.1;D0.55,0.5,0.12";
            case BEACON -> "L0,-0.9,0,0.3;L-0.3,-0.9,0.3,-0.9;D0,0.45,0.18;L0.2,0.55,0.85,0.85;L0.25,0.4,0.9,0.4;L0.2,0.25,0.85,-0.05";
            case SCRIPTURE -> "L-0.7,0.6,0.7,0.6;L-0.7,0.2,0.7,0.2;L-0.7,-0.2,0.7,-0.2;L-0.7,-0.6,0.2,-0.6";
            case GULLET -> "P8,0.85,22.5;C0,0,0.45;L0,0.45,0,0.85;L0,-0.45,0,-0.85;L0.45,0,0.85,0;L-0.45,0,-0.85,0";
            case MOTH -> "L0,-0.6,0,0.6;A-0.4,0.15,0.45,0,180;A0.4,0.15,0.45,0,180;A-0.35,-0.35,0.35,180,360;A0.35,-0.35,0.35,180,360;L0,0.6,-0.25,0.9;L0,0.6,0.25,0.9";
            case CUIRASS -> "L-0.7,0.6,0.7,0.6;L-0.7,0.6,-0.6,-0.5;L0.7,0.6,0.6,-0.5;A0,-0.5,0.6,180,360;L0,0.6,0.15,-0.2;L0.15,-0.2,-0.1,-0.75";
            case HAMMER -> "L0,-0.9,0,0.2;R0,0.5,0.7,0.3;L-0.7,0.2,-0.7,0.8";
            case RAM -> "L0,0.9,-0.75,-0.6;L0,0.9,0.75,-0.6;L-0.75,-0.6,0.75,-0.6;L0,0.3,0,-0.6";
            case VICE -> "L-0.8,0.5,-0.2,0.5;L-0.8,-0.5,-0.2,-0.5;L0.8,0.5,0.2,0.5;L0.8,-0.5,0.2,-0.5;L-0.2,0.5,-0.2,-0.5;L0.2,0.5,0.2,-0.5;L-0.8,0.5,-0.8,-0.5;L0.8,0.5,0.8,-0.5";
            case WAR_HORN -> "A0.1,-0.2,0.8,100,220;L-0.15,0.6,0.7,0.8;L-0.4,0.3,0.7,0.8;C0.75,0.8,0.15";
            case KEYSTONE -> "L-0.6,0.7,0.6,0.7;L-0.6,0.7,-0.35,-0.7;L0.6,0.7,0.35,-0.7;L-0.35,-0.7,0.35,-0.7;L-0.9,-0.75,-0.35,-0.7;L0.9,-0.75,0.35,-0.7";
            case QUIVER -> "R0.1,-0.1,0.3,0.7;L-0.15,0.6,-0.4,0.95;L0.1,0.6,0,0.95;L0.35,0.6,0.5,0.95";
            case WOLF -> "L-0.8,-0.2,-0.2,0.3;L-0.2,0.3,0.1,0.75;L0.1,0.75,0.35,0.3;L0.35,0.3,0.85,-0.3;L0.85,-0.3,0.2,-0.5;L0.2,-0.5,-0.8,-0.2;D0.4,0.1,0.08";
            case RETICLE -> "C0,0,0.6;L0,0.6,0,0.95;L0,-0.6,0,-0.95;L0.6,0,0.95,0;L-0.6,0,-0.95,0;D0,0,0.1";
            case OPEN_HAND -> "A0,-0.2,0.5,180,360;L-0.5,-0.2,-0.55,0.5;L-0.2,-0.2,-0.2,0.85;L0.15,-0.2,0.15,0.9;L0.5,-0.2,0.5,0.6;L-0.5,-0.2,-0.9,0.1";
            case HARE -> "C0,-0.25,0.45;L-0.25,0.15,-0.4,0.9;L-0.05,0.2,-0.15,0.9;L0.05,0.2,0.25,0.9;L0.25,0.15,0.5,0.85;D0.15,-0.2,0.07";
            case LEECH -> "A0,0,0.7,20,200;A0,0,0.4,200,380;L-0.65,0.25,-0.9,0.7;L0.65,-0.25,0.9,-0.7";
            case CLOSED_EYE -> "A0,0.5,1.0,225,315;L-0.4,-0.3,-0.55,-0.6;L0,-0.35,0,-0.7;L0.4,-0.3,0.55,-0.6;D0,0.05,0.08";
            case PLAGUE -> "C0,0,0.35;C0.55,0.35,0.22;C-0.5,0.45,0.2;C0.1,-0.6,0.22;C-0.5,-0.4,0.18;L0.3,0.2,0.4,0.28;L-0.3,0.2,-0.38,0.3;L0.05,-0.35,0.08,-0.42";
            case ALEMBIC -> "L-0.25,0.9,-0.25,0.2;L0.25,0.9,0.25,0.2;A0,-0.35,0.6,200,340;L-0.56,-0.15,-0.25,0.2;L0.56,-0.15,0.25,0.2;L-0.25,0.9,0.25,0.9;C0,-0.4,0.12";
            case GEYSER -> "L-0.7,-0.8,0.7,-0.8;L-0.4,-0.8,-0.3,-0.3;L0.4,-0.8,0.3,-0.3;L0,-0.3,0,0.4;L-0.15,-0.2,-0.4,0.3;L0.15,-0.2,0.4,0.3;A0,0.6,0.3,0,180;A-0.35,0.45,0.2,0,180;A0.35,0.45,0.2,0,180";
            case WELL -> "C0,-0.3,0.7;C0,-0.3,0.4;L-0.55,-0.3,-0.55,0.6;L0.55,-0.3,0.55,0.6;A0,0.6,0.55,0,180";
            case ORRERY -> "C0,0,0.85;C0,0,0.5;D0,0,0.12;D0.5,0,0.1;D-0.6,0.6,0.1;L0,0,0.5,0;L0,0,-0.6,0.6";
            case WHEEL -> "C0,0,0.85;C0,0,0.2;L0,0.2,0,0.85;L0,-0.2,0,-0.85;L0.2,0,0.85,0;L-0.2,0,-0.85,0;L0.14,0.14,0.6,0.6;L-0.14,-0.14,-0.6,-0.6;L0.14,-0.14,0.6,-0.6;L-0.14,0.14,-0.6,0.6";
            case ECLIPSE -> "C0,0,0.85;D0,0,0.6;L0.6,0.6,0.9,0.9;L-0.6,0.6,-0.9,0.9";
            case JAWS -> "L-0.9,0.2,0.9,0.2;L-0.9,-0.2,0.9,-0.2;L-0.6,0.2,-0.5,0.6;L-0.2,0.2,-0.1,0.6;L0.2,0.2,0.3,0.6;L0.6,0.2,0.7,0.6;L-0.6,-0.2,-0.5,-0.6;L-0.2,-0.2,-0.1,-0.6;L0.2,-0.2,0.3,-0.6;L0.6,-0.2,0.7,-0.6";
        };
    }
}
