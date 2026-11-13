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
 * Smoothness: each plane (with its contrail and label) is drawn once into a bitmap, so a frame only moves an image.
 * Frames follow the display's vsync through Choreographer, and positions come from the vsync time rather than the
 * moment the frame happens to be drawn, so every frame advances the plane by the same distance. A lower frame rate
 * skips whole vsyncs evenly instead of drifting against them. Between passes nothing is drawn at all.
 */
final class SkyView extends View {
    // Plane drawn in a 120 x 44 box, nose to the right. An original side-view silhouette.
    private static final float W = 120f, H = 44f;

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
    private final Path fuselage = new Path(), fin = new Path(), tailplane = new Path(), wing = new Path(), cockpit = new Path();
    private final RectF engine = new RectF(58, 31, 71, 35.5f), windows = new RectF(26, 20.3f, 82, 21.9f);
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
        buildShape();
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
        long cycle = c.cycleMs();
        long crossing = c.crossingSeconds * 1000L;
        long nextWake = Long.MAX_VALUE;
        boolean animating = false;
        Set<String> used = new HashSet<>();

        float planeW = c.sizeDp * density;
        float scale = planeW / W;
        float planeH = H * scale;

        for (Sky.Track t : s.tracks) {
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
            Sprite sp = sprite(label, ltr, s.night, c, scale);
            used.add(key(label, ltr, s.night, c, scale));

            float p = (float) (inPass / crossing);
            // Travel from fully off one edge to fully off the other, sprite included.
            float travel = getWidth() + sp.bitmap.getWidth();
            float left = ltr ? -sp.bitmap.getWidth() + travel * p : getWidth() - travel * p;
            float laneY = getHeight() * c.heightPercent / 100f + t.lane * planeH * 2.2f;
            float top = laneY - p * getHeight() * 0.04f;          // a slight climb across the screen

            canvas.drawBitmap(sp.bitmap, left, top - sp.planeTop, blit);
            if (s.night && c.nightLights) drawLights(canvas, left + sp.planeLeft, top, scale, ltr, (long) now);
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

    private static String key(String label, boolean ltr, boolean night, Config c, float scale) {
        return label + '|' + ltr + '|' + night + '|' + c.showTrail + '|' + scale;
    }

    private void trimSprites(Set<String> used) {
        Iterator<Map.Entry<String, Sprite>> it = sprites.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Sprite> e = it.next();
            if (used == null || !used.contains(e.getKey())) { e.getValue().bitmap.recycle(); it.remove(); }
        }
    }

    private Sprite sprite(String label, boolean ltr, boolean night, Config c, float scale) {
        String k = key(label, ltr, night, c, scale);
        Sprite cached = sprites.get(k);
        if (cached != null) return cached;

        float planeW = W * scale, planeH = H * scale;
        float trailLen = c.showTrail ? planeW * 2.2f : 0;
        float gap = planeW * 0.14f;
        float pad = 6f * density;                     // room for shadows

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTypeface(typeface);
        text.setLetterSpacing(0.12f);
        text.setTextSize(Math.max(11f * density, planeH * 0.34f));
        text.setColor(night ? Color.argb(190, 225, 232, 245) : Color.argb(225, 255, 255, 255));
        text.setShadowLayer(3f * density, 0, density, Color.argb(night ? 140 : 90, 0, 20, 50));
        float labelW = label.isEmpty() ? 0 : text.measureText(label);

        int width = (int) Math.ceil(trailLen + planeW + (label.isEmpty() ? 0 : gap + labelW) + 2 * pad);
        int height = (int) Math.ceil(planeH + 2 * pad);
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        // Layout along the direction of flight: contrail, plane, then the label ahead of the nose.
        float planeLeft = ltr ? pad + trailLen : width - pad - trailLen - planeW;
        float planeTop = pad;
        drawPlane(cv, planeLeft, planeTop, scale, ltr, trailLen, night);
        if (!label.isEmpty()) {
            float baseline = planeTop + planeH * 0.58f;
            float x = ltr ? planeLeft + planeW + gap : planeLeft - gap - labelW;
            cv.drawText(label, x, baseline, text);
        }
        Sprite sp = new Sprite(bmp, planeLeft, planeTop);
        sprites.put(k, sp);
        return sp;
    }

    private void drawPlane(Canvas canvas, float x, float y, float scale, boolean ltr, float trailLen, boolean night) {
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
            float ty = 22.5f * scale, th = Math.max(1.2f * density, 1.1f * scale);
            canvas.drawRect(-trailLen, ty - th / 2, 8 * scale, ty + th / 2, trail);
        }
        canvas.scale(scale, scale);

        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        int bodyColor = night ? Color.argb(235, 150, 162, 182) : Color.WHITE;
        body.setColor(bodyColor);
        if (!night) body.setShadowLayer(1.2f, 0, 0.6f, Color.argb(50, 0, 0, 0));
        canvas.drawPath(fin, body);
        body.clearShadowLayer();
        body.setColor(Color.argb(night ? 200 : 220, Color.red(bodyColor), Color.green(bodyColor), Color.blue(bodyColor)));
        canvas.drawPath(tailplane, body);
        canvas.drawPath(wing, body);
        body.setColor(bodyColor);
        canvas.drawPath(fuselage, body);
        canvas.drawRoundRect(engine, 2.2f, 2.2f, body);

        Paint detail = new Paint(Paint.ANTI_ALIAS_FLAG);
        detail.setColor(night ? Color.argb(150, 255, 214, 140) : Color.argb(150, 127, 151, 179));
        canvas.drawRoundRect(windows, 0.8f, 0.8f, detail);   // lit cabin windows at night
        detail.setColor(Color.argb(150, 127, 151, 179));
        canvas.drawPath(cockpit, detail);
        canvas.restore();
    }

    /** Blinking lights stay live: a few circles per frame, in the plane's own coordinates. */
    private void drawLights(Canvas canvas, float planeLeft, float top, float scale, boolean ltr, long now) {
        canvas.save();
        canvas.translate(planeLeft, top);
        if (!ltr) { canvas.translate(W * scale, 0); canvas.scale(-1, 1); }
        canvas.scale(scale, scale);
        long ms = now % 1200;
        if (ms < 120) dot(canvas, 3f, 5f, 3.2f, Color.WHITE);                              // tail strobe
        if (ms >= 600 && ms < 760) dot(canvas, 60f, 17.4f, 2.6f, Color.rgb(255, 60, 50));  // beacon
        // Wingtip: the side we look at is green on an eastbound plane and red on a westbound one.
        dot(canvas, 71f, 42.5f, 1.8f, ltr ? Color.rgb(70, 230, 120) : Color.rgb(255, 70, 60));
        canvas.restore();
    }

    private void dot(Canvas canvas, float cx, float cy, float r, int color) {
        light.setColor(Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawCircle(cx, cy, r * 2.2f, light);
        light.setColor(color);
        canvas.drawCircle(cx, cy, r, light);
    }

    private void buildShape() {
        fuselage.moveTo(6, 22);
        fuselage.cubicTo(6, 19.8f, 7.8f, 18, 10, 18);
        fuselage.lineTo(92, 18);
        fuselage.cubicTo(101, 18, 110, 20.6f, 114, 23.2f);
        fuselage.cubicTo(115.4f, 24.1f, 115.4f, 25.9f, 114, 26.8f);
        fuselage.cubicTo(110, 29.4f, 101, 31, 92, 31);
        fuselage.lineTo(14, 31);
        fuselage.cubicTo(9.6f, 31, 6, 28, 6, 25);
        fuselage.close();

        fin.moveTo(8, 21); fin.lineTo(2, 4); fin.lineTo(9, 4); fin.lineTo(24, 19); fin.close();
        tailplane.moveTo(10, 24); tailplane.lineTo(4, 31); tailplane.lineTo(10, 31); tailplane.lineTo(19, 25); tailplane.close();
        wing.moveTo(44, 26); wing.lineTo(66, 42); wing.lineTo(74, 42); wing.lineTo(60, 26); wing.close();

        cockpit.moveTo(101, 20.5f);
        cockpit.cubicTo(104, 20.8f, 107, 21.6f, 109.4f, 22.7f);
        cockpit.lineTo(108.4f, 23.6f);
        cockpit.cubicTo(106, 22.7f, 103.4f, 22.1f, 100.8f, 21.8f);
        cockpit.close();
    }
}
