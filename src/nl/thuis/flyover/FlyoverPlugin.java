// SPDX-License-Identifier: Apache-2.0
package nl.thuis.flyover;

import android.content.Context;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Flyover: planes that are really overhead, flying across Kiosk Satellite's own screensaver.
 *
 * Home Assistant (Flightradar24 integration) renders a small template with the nearest flights; KS streams it here.
 * While the screensaver shows, one transparent, touch-through overlay on the top layer draws the planes over it.
 * The screensaver itself (weather, clock, photos) stays entirely KS's own.
 */
public final class FlyoverPlugin implements KioskPlugin {
    static final String OVERLAY = "planes";
    private static final long DEMO_MS = 120_000;

    private final AtomicReference<Sky> sky = new AtomicReference<>(Sky.EMPTY);
    private volatile WeakReference<SkyView> view = new WeakReference<>(null);

    private PluginHost host;
    private Config config;
    private String subscriptionKey;
    private int generation;
    private List<Sky.Track> tracks = new ArrayList<>();
    private List<Sky.Flight> lastFlights = Collections.emptyList();
    private boolean night;
    private boolean screensaverActive;
    private String screensaverView;
    private boolean nowPlayingFull;
    private boolean overlayShown;
    private String haProblem;

    @Override public void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        config = new Config(settings);
        safe(() -> host.subscribe("screensaver.state"));
        safe(() -> host.subscribe("screensaver.appearance"));
        safe(() -> host.executeCommand("isScreensaverActive", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            if (ok && data instanceof Boolean) { screensaverActive = (Boolean) data; refreshOverlay(); }
        }));
        safe(() -> host.executeCommand("getScreensaverAppearance", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            if (ok && data instanceof Map) { applyAppearance((Map<?, ?>) data); refreshOverlay(); }
        }));
        subscribeFlights();
        report();
    }

    @Override public void configure(Map<String, Object> settings) {
        Config next = new Config(settings);
        boolean resubscribe = !next.sameQuery(config);
        config = next;
        if (resubscribe) subscribeFlights();
        publish();
        refreshOverlay();
        report();
    }

    @Override public void execute(String command, Map<String, Object> arguments) {
        if ("demo".equals(command)) startDemo();
    }

    @Override public void onEvent(String event, Map<String, Object> payload) {
        if (event.equals("ks.screensaver.state")) {
            screensaverActive = Boolean.TRUE.equals(payload.get("active"));
            refreshOverlay();
        } else if (event.equals("ks.screensaver.appearance")) {
            applyAppearance(payload);
            refreshOverlay();
        } else if (subscriptionKey != null && event.equals("ks.ha.subscription." + subscriptionKey)) {
            onTemplate(payload);
        } else if (event.equals("overlay.closed") && OVERLAY.equals(payload.get("key"))) {
            overlayShown = false;
        }
    }

    @Override public void stop() {
        // KS revokes the host and removes the overlay before stop.
        host = null;
        view = new WeakReference<>(null);
    }

    /* ---------- Home Assistant ---------- */

    private void subscribeFlights() {
        if (host == null) return;
        if (subscriptionKey != null) {
            String old = subscriptionKey;
            safe(() -> host.haUnsubscribe(old));
            subscriptionKey = null;
        }
        if (config.flightsEntity.isEmpty()) {
            haProblem = "Choose the Flightradar24 sensor in the settings.";
            report();
            return;
        }
        String key = "flights_" + (++generation);
        Map<String, Object> message = new HashMap<>();
        message.put("type", "render_template");
        message.put("template", config.template());
        message.put("report_errors", true);
        subscriptionKey = key;
        safe(() -> host.haSubscribe(key, message, (ok, data, error) -> {
            if (!key.equals(subscriptionKey)) return;
            haProblem = ok ? null : "Home Assistant refused the flights template: " + error;
            report();
        }));
        String entity = config.flightsEntity;
        Map<String, Object> read = new HashMap<>();
        read.put("entityId", entity);
        safe(() -> host.executeCommand("getHaEntityState", read, (ok, data, error) -> {
            if (!ok || !(data instanceof Map) || !entity.equals(config.flightsEntity)) return;
            Object status = ((Map<?, ?>) data).get("status");
            if ("missing".equals(status)) {
                haProblem = entity + " does not exist. Install the Flightradar24 integration or pick its \"Current in area\" sensor.";
                report();
            }
        }));
    }

    private void onTemplate(Map<String, Object> payload) {
        Object type = payload.get("type");
        if ("ended".equals(type)) { subscriptionKey = null; subscribeFlights(); return; }
        if (!"event".equals(type)) return;
        Object event = payload.get("event");
        if (!(event instanceof Map)) return;
        Map<?, ?> e = (Map<?, ?>) event;
        if (e.get("error") != null) {
            haProblem = "Template error: " + e.get("error");
            report();
            return;
        }
        Sky.Reading reading = Sky.parse(e.get("result"));
        if (reading == null) return;
        if (haProblem != null && haProblem.startsWith("Template error")) haProblem = null;
        night = reading.night;
        lastFlights = reading.flights;
        tracks = Sky.merge(tracks, reading.flights, System.currentTimeMillis(), config);
        publish();
        report();
    }

    /* ---------- Overlay ---------- */

    private void applyAppearance(Map<?, ?> a) {
        Object v = a.get("view");
        screensaverView = v instanceof String ? ((String) v).toLowerCase() : null;
        boolean playing = Boolean.TRUE.equals(a.get("nowPlaying"));
        boolean shared = Boolean.TRUE.equals(a.get("nowPlayingShared"));
        nowPlayingFull = playing && !shared;
    }

    private boolean viewAllowed() {
        if (config.views.isEmpty()) return true;
        return screensaverView != null && config.views.contains(screensaverView);
    }

    /** Keep one overlay for the whole screensaver session; it idles when there is nothing to fly. */
    private void refreshOverlay() {
        if (host == null) return;
        boolean want = screensaverActive && viewAllowed() && !nowPlayingFull;
        if (want && !overlayShown) {
            OverlaySpec spec = OverlaySpec.fullScreen().onTop(true).touchable(false).closeOnBack(false);
            try { spec = spec.enter(OverlaySpec.FADE).exit(OverlaySpec.FADE).transitionDuration(400); } catch (LinkageError older) {}
            try {
                host.showOverlay(OVERLAY, spec, new Factory());
                overlayShown = true;
            } catch (UnsupportedOperationException older) {
                haProblem = "This Kiosk Satellite version has no native overlays. Update the app.";
            } catch (IllegalStateException | IllegalArgumentException busy) {
                host.log("showOverlay: " + busy.getMessage());
            }
        } else if (!want && overlayShown) {
            safe(() -> host.hideOverlay(OVERLAY));
            overlayShown = false;
        }
        report();
    }

    private void publish() {
        sky.set(new Sky(tracks, night, config));
        SkyView v = view.get();
        if (v != null) v.post(v::wake);
    }

    private final class Factory implements OverlayFactory {
        @Override public View create(Context context, KsTheme theme) {
            SkyView v = new SkyView(context, sky, theme.typeface(500));
            view = new WeakReference<>(v);
            return v;
        }

        @Override public void onDestroy(View v) {
            if (view.get() == v) view = new WeakReference<>(null);
        }
    }

    /* ---------- Demo ---------- */

    private void startDemo() {
        long now = System.currentTimeMillis();
        List<Sky.Track> next = new ArrayList<>();
        next.add(new Sky.Track(new Sky.Flight("demo1", "TP1352", "A20N", 95, "AMS", "LIS", "Amsterdam", "Lisbon"), now, now + DEMO_MS, 0));
        if (config.maxPlanes > 1) {
            next.add(new Sky.Track(new Sky.Flight("demo2", "KL1834", "E295", 265, "BER", "AMS", "Berlin", "Amsterdam"), now + config.cycleMs() * 45 / 100, now + DEMO_MS, 1));
        }
        tracks = next;
        publish();
        // Show it straight away: start the screensaver (needs host.control) so the planes have something to fly over.
        safe(() -> host.executeCommand("startScreensaver", Collections.<String, Object>emptyMap(), (ok, data, error) -> {}));
        if (host != null) host.status("Demo: two minutes of planes over the screensaver.", false);
    }

    /* ---------- Status ---------- */

    private void report() {
        if (host == null) return;
        String text;
        boolean error = haProblem != null;
        if (error) {
            text = haProblem;
        } else {
            int n = lastFlights.size();
            StringBuilder b = new StringBuilder();
            b.append(n == 0 ? "No planes nearby" : n == 1 ? "1 plane nearby: " : n + " planes nearby: ");
            for (int i = 0; i < n; i++) b.append(i == 0 ? "" : ", ").append(lastFlights.get(i).label(Config.LABEL_FLIGHT_ROUTE));
            b.append(" · Screensaver ").append(screensaverActive ? "on" : "off");
            if (screensaverView != null) b.append(" (view: ").append(screensaverView).append(')');
            if (screensaverActive && !viewAllowed()) b.append(" · not shown on this view");
            text = b.toString();
        }
        final String message = text.length() > 1000 ? text.substring(0, 1000) : text;
        safe(() -> host.status(message, error));
    }

    private void safe(Runnable call) {
        if (host == null) return;
        try { call.run(); } catch (RuntimeException | LinkageError e) {
            PluginHost h = host;
            if (h != null) try { h.log(e.getClass().getSimpleName() + ": " + e.getMessage()); } catch (RuntimeException ignored) {}
        }
    }
}
