// SPDX-License-Identifier: Apache-2.0
package nl.thuis.flyover;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/** Runs the plugin against a fake host, without a device. Run with java -ea. */
public final class FlyoverTest {
    static final class Host implements PluginHost {
        final List<String> subscribed = new ArrayList<>();
        final Map<String, Map<String, Object>> ha = new HashMap<>();
        final List<String> haClosed = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        String overlay;
        OverlaySpec spec;
        int shows;
        String status;
        boolean statusError;
        String entityStatus = "available";

        public void subscribe(String event) { subscribed.add(event); }
        public void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) {
            commands.add(command);
            if (command.equals("getHaEntityState")) {
                Map<String, Object> data = new HashMap<>();
                data.put("status", entityStatus);
                callback.onResult(true, data, null);
            } else if (command.equals("isScreensaverActive")) {
                callback.onResult(true, Boolean.FALSE, null);
            } else {
                callback.onResult(false, null, "unknown");
            }
        }
        public void haSubscribe(String key, Map<String, Object> message, CommandCallback callback) {
            ha.put(key, message);
            callback.onResult(true, null, null);
        }
        public void haUnsubscribe(String key) { haClosed.add(key); ha.remove(key); }
        public void showOverlay(String key, OverlaySpec spec, OverlayFactory factory) { overlay = key; this.spec = spec; shows++; }
        public void hideOverlay(String key) { if (key.equals(overlay)) overlay = null; }
        public void status(String message, boolean error) { status = message; statusError = error; }
        public void showWindow(String title, String message, String buttonLabel) {}
        public void hideWindow() {}
        public void log(String message) { System.out.println("log: " + message); }
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static Map<String, Object> templateEvent(String result) {
        return map("type", "event", "time", 1, "event", map("result", result, "listeners", map()));
    }

    static String only(Map<String, ?> m) {
        assert m.size() == 1 : "expected one subscription, got " + m.keySet();
        return m.keySet().iterator().next();
    }

    public static void main(String[] args) throws Exception {
        // ---- parsing ----
        Sky.Reading r = Sky.parse("v1|d|3c1a2b,TP1352,A20N,92|3c1a2c,KLM83C,E295,271");
        assert r != null && !r.night && r.flights.size() == 2;
        assert r.flights.get(0).label().equals("TP1352 · A20N");
        assert r.flights.get(0).leftToRight() && !r.flights.get(1).leftToRight();
        assert Sky.parse("v1|n|").flights.isEmpty() && Sky.parse("v1|n|").night;
        assert Sky.parse("garbage") == null && Sky.parse(42) == null && Sky.parse(null) == null;
        assert Sky.parse("v1|d|,PHX,,").flights.get(0).label().equals("PHX");

        // ---- label styles ----
        Sky.Flight tp = Sky.parse("v1|d|a,TP1352,A20N,92,AMS,LIS,Amsterdam,Lisbon").flights.get(0);
        assert tp.label(Config.LABEL_FLIGHT).equals("TP1352 \u00B7 A20N");
        assert tp.label(Config.LABEL_CODES).equals("AMS \u2192 LIS");
        assert tp.label(Config.LABEL_CITIES).equals("Amsterdam \u2192 Lisbon");
        assert tp.label(Config.LABEL_FLIGHT_ROUTE).equals("TP1352 \u00B7 AMS \u2192 LIS");
        Sky.Flight ga = Sky.parse("v1|d|p,PHABC,C172,300,,,,").flights.get(0);
        assert ga.label(Config.LABEL_CODES).equals("PHABC \u00B7 C172") && ga.label(Config.LABEL_CITIES).equals("PHABC \u00B7 C172");
        Sky.Flight half = Sky.parse("v1|d|h,KL1,B738,10,,AMS,,").flights.get(0);
        assert half.label(Config.LABEL_CITIES).equals("\u2192 AMS") : half.label(Config.LABEL_CITIES);
        assert new Config(map("labelStyle", "Cities")).labelStyle == Config.LABEL_CITIES;
        assert new Config(map("labelStyle", "nonsense")).labelStyle == Config.LABEL_FLIGHT;
        assert new Config(map()).template().contains("airport_destination_city");

        // ---- aircraft shapes ----
        assert Shapes.forType("A20N", "Airplane") == Shapes.NARROW;
        assert Shapes.forType("BCS3", "Airplane") == Shapes.NARROW;
        assert Shapes.forType("E190", "Airplane") == Shapes.NARROW;
        assert Shapes.forType("B789", "Airplane") == Shapes.WIDE;
        assert Shapes.forType("A346", "Airplane") == Shapes.QUAD;
        assert Shapes.forType("B748", "Airplane") == Shapes.JUMBO && Shapes.forType("A388", null) == Shapes.JUMBO;
        assert Shapes.forType("CRJ9", "Airplane") == Shapes.REARJET && Shapes.forType("C56X", "") == Shapes.REARJET;
        assert Shapes.forType("AT76", "Airplane") == Shapes.TURBOPROP && Shapes.forType("DH8D", "") == Shapes.TURBOPROP;
        assert Shapes.forType("C172", "Airplane") == Shapes.LIGHT && Shapes.forType("BE35", "") == Shapes.LIGHT;
        assert Shapes.forType("C17", "Airplane") == Shapes.QUAD : "C-17, not a Cessna";
        assert Shapes.forType("EC35", "") == Shapes.HELICOPTER;
        assert Shapes.forType("XXXX", "Helicopter") == Shapes.HELICOPTER;
        assert Shapes.forType("", "") == Shapes.NARROW && Shapes.forType(null, null) == Shapes.NARROW;
        assert Shapes.forType(" a20n ", "airplane") == Shapes.NARROW;
        for (Shapes.Shape shape : Shapes.ALL) {
            assert shape.slowness <= Shapes.MAX_SLOWNESS;
            for (Shapes.Part part : shape.parts) {
                float[][] cmds = SvgPath.commands(part.d);
                assert cmds.length >= 3 && cmds[0][0] == 'M' : shape.name + ": " + part.d;
                for (float[] cmd : cmds) for (int i = 1; i < cmd.length; i++) {
                    float v = cmd[i];
                    boolean xy = cmd[0] == 'H' || (cmd[0] != 'V' && i % 2 == 1);
                    assert v >= -1 && v <= (xy ? Shapes.W + 1 : Shapes.H + 1) : shape.name + " outside its box: " + part.d;
                }
            }
        }
        Sky.Flight heli = Sky.parse("v1|d|h,PHPXE,EC35,300,,,,,Helicopter").flights.get(0);
        assert heli.category.equals("Helicopter") && heli.shape() == Shapes.HELICOPTER;
        assert Sky.parse("v1|d|a,TP1352,A20N,92,AMS,LIS,Amsterdam,Lisbon").flights.get(0).category.isEmpty();
        assert new Config(map()).template().contains("f.aircraft_category");
        assert new Config(map()).typeShapes && !new Config(map("typeShapes", false)).typeShapes;
        Config slow = new Config(map("crossing", 40));
        assert slow.crossingMs(Shapes.HELICOPTER) == 68_000 && slow.crossingMs(Shapes.NARROW) == 40_000;
        assert new Config(map("crossing", 40, "typeShapes", false)).crossingMs(Shapes.HELICOPTER) == 40_000;

        // ---- ISS ----
        Sky.Reading withIss = Sky.parse("v1|n|a,TP1352,A20N,92|@iss,42,10");
        assert withIss.flights.size() == 2;
        Sky.Flight iss = withIss.flights.get(1);
        assert iss.isIss() && iss.shape() == Shapes.ISS && iss.leftToRight();
        assert iss.label(Config.LABEL_CITIES).equals("ISS \u00B7 42\u00B0") : iss.label(Config.LABEL_CITIES);
        assert Sky.parse("v1|n|@iss,x,1").flights.isEmpty() : "a broken ISS entry is skipped";
        String tpl = new Config(map()).template();
        assert tpl.contains("state_attr('sensor.iss', 'lat')") && tpl.contains("state_attr('sensor.iss', 'latitude')");
        assert tpl.contains("el >= 10");
        assert !new Config(map("issEntity", "")).template().contains("@iss");
        assert new Config(map("issMinElevation", 30)).template().contains("el >= 30");
        assert !new Config(map()).sameQuery(new Config(map("issMinElevation", 30)));
        assert new Config(map()).fps == 60;
        List<Sky.Track> it = Sky.merge(new ArrayList<Sky.Track>(),
            Sky.parse("v1|d|a,A1,A320,90|b,B1,B738,270|@iss,30,7").flights, 1000, new Config(map()));
        assert it.size() == 3 : "the ISS does not take a plane's lane";
        for (Sky.Track tk : it) assert tk.flight.isIss() ? tk.lane == Sky.ISS_LANE : tk.lane >= 0;

        // ---- lifecycle ----
        Host host = new Host();
        FlyoverPlugin plugin = new FlyoverPlugin();
        plugin.start(host, map("flightsEntity", "sensor.flightradar24_current_in_area"));
        assert host.subscribed.contains("screensaver.state") && host.subscribed.contains("screensaver.appearance");
        String key = only(host.ha);
        Map<String, Object> msg = host.ha.get(key);
        assert "render_template".equals(msg.get("type"));
        String template = (String) msg.get("template");
        assert template.contains("state_attr('sensor.flightradar24_current_in_area', 'flights')") && template.contains("<= 15");
        assert host.overlay == null : "no overlay while the screensaver is off";

        String ev = "ks.ha.subscription." + key;
        plugin.onEvent(ev, templateEvent("v1|d|a,TP1352,A20N,92,AMS,LIS,Amsterdam,Lisbon|b,KL1834,E295,265"));
        assert host.status.contains("2 planes nearby: TP1352 · A20N, KL1834 · E295") : host.status;
        // The status line follows the Label setting, like the screensaver.
        plugin.configure(map("labelStyle", "Cities"));
        assert host.status.contains("2 planes nearby: Amsterdam → Lisbon, KL1834 · E295") : host.status;
        plugin.configure(map("labelStyle", "Airport codes"));
        assert host.status.contains("nearby: AMS → LIS, KL1834 · E295") : host.status;
        plugin.configure(map());

        // Screensaver comes up: one full-screen, top-layer, touch-through overlay.
        plugin.onEvent("ks.screensaver.state", map("active", true));
        assert "planes".equals(host.overlay) && host.shows == 1;
        assert host.spec.onTop() && !host.spec.touchable() && !host.spec.closeOnBack();
        assert host.spec.width() == OverlaySpec.FILL && host.spec.height() == OverlaySpec.FILL;

        // HA updates do not rebuild the overlay.
        plugin.onEvent(ev, templateEvent("v1|d|a,TP1352,A20N,92"));
        plugin.onEvent(ev, templateEvent("v1|n|"));
        assert host.shows == 1;
        assert host.status.startsWith("No planes nearby") : host.status;
        plugin.onEvent(ev, templateEvent("v1|n|@iss,35,7"));
        assert host.status.startsWith("No planes nearby \u00B7 ISS overhead at 35\u00B0") : host.status;
        plugin.onEvent(ev, templateEvent("v1|n|"));

        // Full-screen Now Playing hides it; shared layout keeps it.
        plugin.onEvent("ks.screensaver.appearance", map("view", "weather", "nowPlaying", true, "nowPlayingShared", false));
        assert host.overlay == null;
        plugin.onEvent("ks.screensaver.appearance", map("view", "weather", "nowPlaying", true, "nowPlayingShared", true));
        assert "planes".equals(host.overlay) && host.shows == 2;

        // Screensaver gone: overlay gone.
        plugin.onEvent("ks.screensaver.state", map("active", false));
        assert host.overlay == null;

        // View filter.
        plugin.configure(map("views", "clock"));
        plugin.onEvent("ks.screensaver.state", map("active", true));
        assert host.overlay == null : "weather view is not in the list";
        assert host.status.contains("not shown on this view") : host.status;
        plugin.configure(map("views", "Weather, clock"));
        assert "planes".equals(host.overlay);

        // Changing the query resubscribes under a fresh key; changing only looks does not.
        plugin.configure(map("maxDistance", 8, "views", "weather"));
        String key2 = only(host.ha);
        assert !key2.equals(key) && host.haClosed.contains(key);
        assert ((String) host.ha.get(key2).get("template")).contains("<= 8");
        plugin.configure(map("maxDistance", 8, "views", "weather", "size", 96));
        assert only(host.ha).equals(key2);
        // Events for the old key are ignored.
        plugin.onEvent(ev, templateEvent("v1|d|z,XX1,B738,10"));
        assert host.status.startsWith("No planes nearby") : host.status;

        // An unsafe entity id never reaches the template.
        plugin.configure(map("flightsEntity", "sensor.x') }}{{ states('y"));
        assert host.ha.isEmpty() && host.statusError;

        // Missing sensor is reported.
        Host h2 = new Host();
        h2.entityStatus = "missing";
        new FlyoverPlugin().start(h2, map());
        assert h2.statusError && h2.status.contains("does not exist") : h2.status;

        // Template errors are reported and cleared again.
        FlyoverPlugin p3 = new FlyoverPlugin();
        Host h3 = new Host();
        p3.start(h3, map());
        String ev3 = "ks.ha.subscription." + only(h3.ha);
        p3.onEvent(ev3, map("type", "event", "event", map("error", "UndefinedError")));
        assert h3.statusError;
        p3.onEvent(ev3, templateEvent("v1|d|a,TP1352,A20N,92"));
        assert !h3.statusError && h3.status.startsWith("1 plane nearby") : h3.status;

        // Demo starts the screensaver.
        p3.execute("demo", Collections.<String, Object>emptyMap());
        assert h3.commands.contains("startScreensaver");

        // ---- track merging ----
        Config c = new Config(map());
        long t0 = 1_000_000;
        List<Sky.Track> tr = Sky.merge(new ArrayList<Sky.Track>(), Sky.parse("v1|d|a,A1,A320,90|b,B1,B738,270").flights, t0, c);
        assert tr.size() == 2 && tr.get(0).lane != tr.get(1).lane;
        assert tr.get(1).firstSeen > t0 : "a second new plane is staggered";
        List<Sky.Track> tr2 = Sky.merge(tr, Sky.parse("v1|d|a,A1,A320,90").flights, t0 + 5000, c);
        Sky.Track a = tr2.get(0);
        assert a.firstSeen == t0 && a.goneAt == Long.MAX_VALUE : "kept its start time";
        assert tr2.size() == 2 && tr2.get(1).goneAt == t0 + 5000 : "b finishes its pass";
        List<Sky.Track> tr3 = Sky.merge(tr2, Sky.parse("v1|d|a,A1,A320,90").flights, t0 + 5000 + c.longestCycleMs() + 1, c);
        assert tr3.size() == 1 : "b dropped once even the slowest pass is over";

        System.out.println("FlyoverTest passed");
    }
}
