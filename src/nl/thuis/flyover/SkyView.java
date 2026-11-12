// SPDX-License-Identifier: Apache-2.0
package nl.thuis.flyover;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A transparent full-screen view that flies the planes across whatever KS shows under it. It only redraws while a
 * plane is actually on screen; between passes it sleeps until the next one is due.
 */
final class SkyView extends View {
    // Plane drawn in a 120 x 44 box, nose to the right. An original side-view silhouette.
    private static final float W = 120f, H = 44f;

    private final AtomicReference<Sky> sky;
    private final float density;
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path fuselage = new Path(), fin = new Path(), tailplane = new Path(), wing = new Path(), cockpit = new Path();
    private final RectF engine = new RectF(58, 31, 71, 35.5f), windows = new RectF(26, 20.3f, 82, 21.9f);
    private final Runnable tick = new Runnable() { @Override public void run() { invalidate(); } };
    private LinearGradient trailShader;
    private float trailShaderLen = -1;
    private boolean trailShaderNight;

    SkyView(Context context, AtomicReference<Sky> sky, Typeface typeface) {
        super(context);
        this.sky = sky;
        this.density = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        detail.setStyle(Paint.Style.FILL);
        light.setStyle(Paint.Style.FILL);
        text.setTypeface(typeface);
        text.setLetterSpacing(0.12f);
        buildShape();
    }

    /** New data from the plugin: redraw now instead of waiting for the next scheduled frame. */
    void wake() {
        removeCallbacks(tick);
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        removeCallbacks(tick); // one pending frame at most
        Sky s = sky.get();
        if (s == null || s.config == null || s.tracks.isEmpty() || getWidth() == 0) return;
        Config c = s.config;
        long now = System.currentTimeMillis();
        long cycle = c.cycleMs();
        long crossing = c.crossingSeconds * 1000L;
        long nextWake = Long.MAX_VALUE;
        boolean animating = false;

        float planeW = c.sizeDp * density;
        float scale = planeW / W;
        float planeH = H * scale;
        float trailLen = c.showTrail ? planeW * 2.2f : 0;
        text.setTextSize(Math.max(11f * density, planeH * 0.34f));
        float gap = planeW * 0.14f;

        for (Sky.Track t : s.tracks) {
            long elapsed = now - t.firstSeen;
            if (elapsed < 0) { nextWake = Math.min(nextWake, -elapsed); continue; }
            long passStart = t.firstSeen + (elapsed / cycle) * cycle;
            long inPass = now - passStart;
            if (passStart > t.goneAt) continue;                 // left the area before this pass began
            if (inPass >= crossing) {                           // between passes
                long nextPass = passStart + cycle;
                if (nextPass <= t.goneAt) nextWake = Math.min(nextWake, nextPass - now);
                continue;
            }
            animating = true;
            float p = inPass / (float) crossing;
            String label = c.showLabel ? t.flight.label().toUpperCase() : "";
            float labelW = label.isEmpty() ? 0 : text.measureText(label) + gap;
            boolean ltr = t.flight.leftToRight();

            // Travel from fully off one edge (trail included) to fully off the other (label included).
            float start = -(planeW + trailLen), end = getWidth() + labelW;
            float x = start + (end - start) * p;              // left edge of the plane, flying right
            if (!ltr) x = getWidth() - x - planeW;            // mirror for westbound planes
            float laneY = getHeight() * c.heightPercent / 100f + t.lane * planeH * 2.2f;
            float y = laneY - p * getHeight() * 0.04f;          // a slight climb across the screen

            drawPlane(canvas, x, y, scale, ltr, trailLen, s.night, c.nightLights, now);
            if (!label.isEmpty()) {
                float baseline = y + planeH * 0.58f;
                text.setColor(s.night ? Color.argb(190, 225, 232, 245) : Color.argb(225, 255, 255, 255));
                text.setShadowLayer(3f * density, 0, density, Color.argb(s.night ? 140 : 90, 0, 20, 50));
                if (ltr) canvas.drawText(label, x + planeW + gap, baseline, text);
                else canvas.drawText(label, x - labelW, baseline, text);
            }
        }

        if (animating) {
            if (c.fps >= 60) postInvalidateOnAnimation();
            else postDelayed(tick, 1000L / c.fps);
        } else if (nextWake != Long.MAX_VALUE) {
            postDelayed(tick, Math.max(16, Math.min(nextWake, 60_000)));
        }
    }

    private void drawPlane(Canvas canvas, float x, float y, float scale, boolean ltr, float trailLen,
                           boolean night, boolean navLights, long now) {
        canvas.save();
        canvas.translate(x, y);
        if (!ltr) { // mirror around the plane's own centre
            canvas.translate(W * scale, 0);
            canvas.scale(-1, 1);
        }
        if (trailLen > 0) {
            if (trailShader == null || trailShaderLen != trailLen || trailShaderNight != night) {
                int alpha = night ? 70 : 150;
                trailShader = new LinearGradient(-trailLen, 0, 0, 0, Color.argb(0, 255, 255, 255),
                    Color.argb(alpha, 255, 255, 255), Shader.TileMode.CLAMP);
                trailShaderLen = trailLen;
                trailShaderNight = night;
            }
            trail.setShader(trailShader);
            float ty = 22.5f * scale, th = Math.max(1.2f * density, 1.1f * scale);
            canvas.drawRect(-trailLen, ty - th / 2, 8 * scale, ty + th / 2, trail);
        }
        canvas.scale(scale, scale);

        int bodyColor = night ? Color.argb(235, 150, 162, 182) : Color.WHITE;
        body.setColor(bodyColor);
        body.setShadowLayer(night ? 0 : 1.2f, 0, 0.6f, Color.argb(50, 0, 0, 0));
        canvas.drawPath(fin, body);
        body.clearShadowLayer();
        body.setColor(Color.argb(night ? 200 : 220, Color.red(bodyColor), Color.green(bodyColor), Color.blue(bodyColor)));
        canvas.drawPath(tailplane, body);
        canvas.drawPath(wing, body);
        body.setColor(bodyColor);
        canvas.drawPath(fuselage, body);
        canvas.drawRoundRect(engine, 2.2f, 2.2f, body);

        detail.setColor(night ? Color.argb(150, 255, 214, 140) : Color.argb(150, 127, 151, 179));
        canvas.drawRoundRect(windows, 0.8f, 0.8f, detail);   // lit cabin windows at night
        detail.setColor(Color.argb(150, 127, 151, 179));
        canvas.drawPath(cockpit, detail);

        if (night && navLights) {
            long ms = now % 1200;
            if (ms < 120) dot(canvas, 3f, 5f, 3.2f, Color.WHITE);                       // tail strobe
            if (ms >= 600 && ms < 760) dot(canvas, 60f, 17.4f, 2.6f, Color.rgb(255, 60, 50)); // beacon
            // Wingtip: the side we look at is red on a westbound plane and green on an eastbound one.
            dot(canvas, 71f, 42.5f, 1.8f, ltr ? Color.rgb(70, 230, 120) : Color.rgb(255, 70, 60));
        }
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
