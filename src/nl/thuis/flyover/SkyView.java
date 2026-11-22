// SPDX-License-Identifier: Apache-2.0
package nl.thuis.flyover;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.Display;
import android.view.View;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A transparent full-screen view that flies the planes across whatever KS shows under it.
 *
 * Smoothness: each plane (with its contrail and label) is drawn once into a bitmap, so a frame only moves an image
 * plus a few live strokes for blinking lights and spinning propellers or rotors. Frames follow the display's vsync
 * through Choreographer, and positions come from the vsync time, so every frame advances the plane by the same
 * distance. A lower frame rate skips whole vsyncs evenly. Between passes nothing is drawn at all.
 */
final class SkyView extends View {
    private static final float W = Shapes.W, H = Shapes.H;

    /** A pre-drawn plane: the bitmap plus where the plane itself sits inside it. */
    private static final class Sprite {
        final Bitmap bitmap;
        final float planeLeft, planeTop;
        Sprite(Bitmap bitmap, float planeLeft, float planeTop) {
            this.bitmap = bitmap; this.planeLeft = planeLeft; this.planeTop = planeTop;
        }
    }

    private final AtomicReference<Sky> sky;
    private final float density;
    private final Typeface typeface;
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rotorDisc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Map<String, Path[]> paths = new HashMap<>();
    private final Map<String, Sprite> sprites = new HashMap<>();

    private final Runnable tick = new Runnable() { @Override public void run() { invalidate(); } };
    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            frameScheduled = false;
            vsyncNanos = frameTimeNanos;
            if (++vsyncCount >= stride) {
                vsyncCount = 0;
                invalidate();               // drawn in this same frame, after the animation callbacks
            } else {
                scheduleFrame();
            }
        }
    };
    private boolean frameScheduled;
    private long vsyncNanos;
    private int vsyncCount;
    private int stride = 2;
    /** Wall clock minus the monotonic clock, so vsync times map onto the plugin's wall-clock timeline. */
    private long wallOffsetMs = System.currentTimeMillis() - System.nanoTime() / 1_000_000L;

    SkyView(Context context, AtomicReference<Sky> sky, Typeface typeface) {
        super(context);
        this.sky = sky;
        this.typeface = typeface;
        this.density = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        light.setStyle(Paint.Style.FILL);
        rotorDisc.setStyle(Paint.Style.FILL);
        blade.setStyle(Paint.Style.STROKE);
        blade.setStrokeCap(Paint.Cap.ROUND);
    }

    /** New data from the plugin: redraw now instead of waiting for the next scheduled frame. */
    void wake() {
        removeCallbacks(tick);
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        Choreographer.getInstance().removeFrameCallback(frame);
        frameScheduled = false;
        for (Sprite s : sprites.values()) s.bitmap.recycle();
        sprites.clear();
        super.onDetachedFromWindow();
    }

    private void scheduleFrame() {
        if (frameScheduled) return;
        frameScheduled = true;
        Choreographer.getInstance().postFrameCallback(frame);
    }

    private int strideFor(int fps) {
        float hz = 60f;
        Display display = getDisplay();
        if (display != null && display.getRefreshRate() >= 20f) hz = display.getRefreshRate();
        return Math.max(1, Math.round(hz / Math.max(1, fps)));
    }

    @Override protected void onDraw(Canvas canvas) {
        removeCallbacks(tick); // one pending wake-up at most
        Sky s = sky.get();
        if (s == null || s.config == null || s.tracks.isEmpty() || getWidth() == 0) {
            trimSprites(null);
            return;
        }
        Config c = s.config;
        // Time of the vsync this frame is for, on the plugin's wall-clock timeline.
        long nowNanos = frameScheduled || vsyncNanos == 0 ? System.nanoTime() : vsyncNanos;
        double now = nowNanos / 1_000_000.0 + wallOffsetMs;
        long nextWake = Long.MAX_VALUE;
        boolean animating = false;
        Set<String> used = new HashSet<>();

        float baseW = c.sizeDp * density;              // an airliner's length on screen
        float baseH = baseW * H / W;

        for (Sky.Track t : s.tracks) {
            Shapes.Shape shape = c.typeShapes ? t.flight.shape() : Shapes.NARROW;
            long crossing = c.crossingMs(shape);
            long cycle = crossing + c.pauseSeconds * 1000L;
            double elapsed = now - t.firstSeen;
            if (elapsed < 0) { nextWake = Math.min(nextWake, (long) -elapsed); continue; }
            long passStart = t.firstSeen + ((long) elapsed / cycle) * cycle;
            double inPass = now - passStart;
            if (passStart > t.goneAt) continue;                 // left the area before this pass began
            if (inPass >= crossing) {                           // between passes
                long nextPass = passStart + cycle;
                if (nextPass <= t.goneAt) nextWake = Math.min(nextWake, (long) (nextPass - now));
                continue;
            }
            animating = true;
            boolean ltr = t.flight.leftToRight();
            String label = c.showLabel ? t.flight.label(c.labelStyle).toUpperCase() : "";
            float scale = baseW * (c.typeShapes ? shape.scale : 1f) / W;
            Sprite sp = sprite(label, ltr, s.night, c, shape, scale, baseH);
            used.add(key(label, ltr, s.night, c, shape, scale));

            float p = (float) (inPass / crossing);
            // Travel from fully off one edge to fully off the other, sprite included.
            float travel = getWidth() + sp.bitmap.getWidth();
            float left = ltr ? -sp.bitmap.getWidth() + travel * p : getWidth() - travel * p;
            float laneTop = getHeight() * c.heightPercent / 100f + t.lane * baseH * 2.4f;
            float centre = laneTop + baseH / 2f - p * getHeight() * 0.04f;   // a slight climb across the screen
            float top = centre - H * scale / 2f;

            canvas.drawBitmap(sp.bitmap, left, top - sp.planeTop, blit);
            drawLive(canvas, shape, left + sp.planeLeft, top, scale, ltr, s.night, c.nightLights, now);
        }
        trimSprites(used);

        if (animating) {
            stride = strideFor(c.fps);
            scheduleFrame();
        } else {
            vsyncNanos = 0;
            wallOffsetMs = System.currentTimeMillis() - System.nanoTime() / 1_000_000L; // re-anchor while idle
            if (nextWake != Long.MAX_VALUE) postDelayed(tick, Math.max(16, Math.min(nextWake, 60_000)));
        }
    }

    /* ---------- Sprites ---------- */

    private static String key(String label, boolean ltr, boolean night, Config c, Shapes.Shape shape, float scale) {
        return shape.name + '|' + label + '|' + ltr + '|' + night + '|' + c.showTrail + '|' + scale;
    }

    private void trimSprites(Set<String> used) {
        Iterator<Map.Entry<String, Sprite>> it = sprites.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Sprite> e = it.next();
            if (used == null || !used.contains(e.getKey())) { e.getValue().bitmap.recycle(); it.remove(); }
        }
    }

    private Path[] pathsFor(Shapes.Shape shape) {
        Path[] p = paths.get(shape.name);
        if (p == null) {
            p = new Path[shape.parts.length];
            for (int i = 0; i < p.length; i++) p[i] = SvgPath.parse(shape.parts[i].d);
            paths.put(shape.name, p);
        }
        return p;
    }

    private Sprite sprite(String label, boolean ltr, boolean night, Config c, Shapes.Shape shape, float scale, float baseH) {
        String k = key(label, ltr, night, c, shape, scale);
        Sprite cached = sprites.get(k);
        if (cached != null) return cached;

        float planeW = W * scale, planeH = H * scale;
        float trailLen = c.showTrail && !Float.isNaN(shape.trailY) ? planeW * 2.2f : 0;
        float gap = planeW * 0.14f;
        float pad = 6f * density;                     // room for shadows

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTypeface(typeface);
        text.setLetterSpacing(0.12f);
        text.setTextSize(Math.max(11f * density, baseH * 0.34f));   // same label size for every type
        text.setColor(night ? Color.argb(190, 225, 232, 245) : Color.argb(225, 255, 255, 255));
        text.setShadowLayer(3f * density, 0, density, Color.argb(night ? 140 : 90, 0, 20, 50));
        float labelW = label.isEmpty() ? 0 : text.measureText(label);
        Paint.FontMetrics fm = text.getFontMetrics();

        int width = (int) Math.ceil(trailLen + planeW + (label.isEmpty() ? 0 : gap + labelW) + 2 * pad);
        float textH = fm.descent - fm.ascent;
        int height = (int) Math.ceil(Math.max(planeH, textH) + 2 * pad);
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        // Layout along the direction of flight: contrail, plane, then the label ahead of the nose.
        float planeLeft = ltr ? pad + trailLen : width - pad - trailLen - planeW;
        float planeTop = (height - planeH) / 2f;
        drawPlane(cv, shape, planeLeft, planeTop, scale, ltr, trailLen, night);
        if (!label.isEmpty()) {
            float baseline = height / 2f - (fm.ascent + fm.descent) / 2f;   // label centred on the plane
            float x = ltr ? planeLeft + planeW + gap : planeLeft - gap - labelW;
            cv.drawText(label, x, baseline, text);
        }
        Sprite sp = new Sprite(bmp, planeLeft, planeTop);
        sprites.put(k, sp);
        return sp;
    }

    private void drawPlane(Canvas canvas, Shapes.Shape shape, float x, float y, float scale, boolean ltr,
                           float trailLen, boolean night) {
        canvas.save();
        canvas.translate(x, y);
        if (!ltr) { // mirror around the plane's own centre
            canvas.translate(W * scale, 0);
            canvas.scale(-1, 1);
        }
        if (trailLen > 0) {
            Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
            trail.setShader(new LinearGradient(-trailLen, 0, 0, 0, Color.argb(0, 255, 255, 255),
                Color.argb(night ? 70 : 150, 255, 255, 255), Shader.TileMode.CLAMP));
            float ty = shape.trailY * scale, th = Math.max(1.2f * density, 1.1f * scale);
            canvas.drawRect(-trailLen, ty - th / 2, 8 * scale, ty + th / 2, trail);
        }
        canvas.scale(scale, scale);

        int body = night ? Color.argb(235, 150, 162, 182) : Color.WHITE;
        int shade = Color.argb(night ? 200 : 220, Color.red(body), Color.green(body), Color.blue(body));
        int windows = night ? Color.argb(150, 255, 214, 140) : Color.argb(150, 127, 151, 179);
        int glass = Color.argb(150, 127, 151, 179);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path[] p = pathsFor(shape);
        for (int i = 0; i < p.length; i++) {
            int role = shape.parts[i].role;
            paint.setColor(role == Shapes.BODY ? body : role == Shapes.SHADE ? shade : role == Shapes.WINDOWS ? windows : glass);
            if (!night && i == 0) paint.setShadowLayer(1.2f, 0, 0.6f, Color.argb(50, 0, 0, 0));
            else paint.clearShadowLayer();
            canvas.drawPath(p[i], paint);
        }
        canvas.restore();
    }

    /** Lights and rotors stay live: a few strokes per frame, in the plane's own coordinates. */
    private void drawLive(Canvas canvas, Shapes.Shape shape, float planeLeft, float top, float scale, boolean ltr,
                          boolean night, boolean navLights, double now) {
        boolean lights = night && navLights;
        if (!lights && shape.rotors.length == 0) return;
        canvas.save();
        canvas.translate(planeLeft, top);
        if (!ltr) { canvas.translate(W * scale, 0); canvas.scale(-1, 1); }
        canvas.scale(scale, scale);

        int tone = night ? Color.rgb(170, 182, 200) : Color.WHITE;
        double seconds = now / 1000.0;
        for (Shapes.Rotor r : shape.rotors) {
            double turn = (seconds * r.revsPerSecond) % 1.0;
            float cos = (float) Math.abs(Math.cos(turn * 2 * Math.PI));
            rotorDisc.setColor(Color.argb(r.kind == Shapes.Rotor.DISC ? 55 : 70, Color.red(tone), Color.green(tone), Color.blue(tone)));
            blade.setColor(Color.argb(night ? 150 : 200, Color.red(tone), Color.green(tone), Color.blue(tone)));
            switch (r.kind) {
                case Shapes.Rotor.PROP:      // edge-on, upright: a thin blurred disc and a flickering blade
                    oval.set(r.cx - 0.9f, r.cy - r.r, r.cx + 0.9f, r.cy + r.r);
                    canvas.drawOval(oval, rotorDisc);
                    blade.setStrokeWidth(1.1f);
                    canvas.drawLine(r.cx, r.cy - r.r * cos, r.cx, r.cy + r.r * cos, blade);
                    break;
                case Shapes.Rotor.MAIN:      // edge-on, flat: the blades sweep towards us and away
                    oval.set(r.cx - r.r, r.cy - 0.8f, r.cx + r.r, r.cy + 0.8f);
                    canvas.drawOval(oval, rotorDisc);
                    blade.setStrokeWidth(1.3f);
                    canvas.drawLine(r.cx - r.r * cos, r.cy, r.cx + r.r * cos, r.cy, blade);
                    break;
                default: {                   // face-on: a disc with a turning blade
                    canvas.drawCircle(r.cx, r.cy, r.r, rotorDisc);
                    float a = (float) (turn * 2 * Math.PI);
                    float dx = (float) Math.cos(a) * r.r, dy = (float) Math.sin(a) * r.r;
                    blade.setStrokeWidth(0.9f);
                    canvas.drawLine(r.cx - dx, r.cy - dy, r.cx + dx, r.cy + dy, blade);
                }
            }
        }

        if (lights) {
            long ms = ((long) now) % 1200;
            if (shape.strobe != null && ms < 120) dot(canvas, shape.strobe, 3.2f, Color.WHITE);
            if (shape.beacon != null && ms >= 600 && ms < 760) dot(canvas, shape.beacon, 2.6f, Color.rgb(255, 60, 50));
            // Wingtip: the side we look at is green on an eastbound plane and red on a westbound one.
            if (shape.wingtip != null) dot(canvas, shape.wingtip, 1.8f, ltr ? Color.rgb(70, 230, 120) : Color.rgb(255, 70, 60));
        }
        canvas.restore();
    }

    private void dot(Canvas canvas, float[] at, float r, int color) {
        light.setColor(Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawCircle(at[0], at[1], r * 2.2f, light);
        light.setColor(color);
        canvas.drawCircle(at[0], at[1], r, light);
    }
}
