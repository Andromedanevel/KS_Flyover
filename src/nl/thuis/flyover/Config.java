// SPDX-License-Identifier: Apache-2.0
package nl.thuis.flyover;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** The plugin's settings, validated once per configure. Immutable. */
final class Config {
    private static final Pattern ENTITY_ID = Pattern.compile("[a-z0-9_]+\\.[a-z0-9_]+");

    final String flightsEntity;
    final int maxDistanceKm;
    final int maxPlanes;
    final int heightPercent;
    final int sizeDp;
    final int crossingSeconds;
    final int pauseSeconds;
    final boolean showLabel;
    final boolean showTrail;
    final boolean nightLights;
    final int fps;
    final List<String> views;

    Config(Map<String, Object> s) {
        String entity = string(s, "flightsEntity", "sensor.flightradar24_current_in_area").trim();
        flightsEntity = ENTITY_ID.matcher(entity).matches() ? entity : "";
        maxDistanceKm = number(s, "maxDistance", 15, 1, 100);
        maxPlanes = number(s, "maxPlanes", 2, 1, 2);
        heightPercent = number(s, "height", 28, 5, 85);
        sizeDp = number(s, "size", 64, 24, 160);
        crossingSeconds = number(s, "crossing", 40, 10, 180);
        pauseSeconds = number(s, "pause", 15, 0, 300);
        showLabel = bool(s, "showLabel", true);
        showTrail = bool(s, "showTrail", true);
        nightLights = bool(s, "nightLights", true);
        fps = number(s, "fps", 30, 10, 60);
        List<String> list = new ArrayList<>();
        for (String part : string(s, "views", "").split(",")) {
            String v = part.trim().toLowerCase(Locale.ROOT);
            if (!v.isEmpty()) list.add(v);
        }
        views = Collections.unmodifiableList(list);
    }

    /** One full cycle of a plane: across the screen, then the pause before it comes round again. */
    long cycleMs() { return (crossingSeconds + pauseSeconds) * 1000L; }

    /** Whether the HA template needs to be subscribed again for this change. */
    boolean sameQuery(Config other) {
        return other != null && flightsEntity.equals(other.flightsEntity)
            && maxDistanceKm == other.maxDistanceKm && maxPlanes == other.maxPlanes;
    }

    /**
     * The template Home Assistant renders and streams back. It does the filtering on the HA side, so the plugin never
     * has to receive the full Flightradar24 attribute (which can be larger than the 30 KB KS passes on). Result:
     * v1|d or n|id,flight,type,heading|id,flight,type,heading
     */
    String template() {
        return "{%- set ns = namespace(out=[]) -%}"
            + "{%- set fl = state_attr('" + flightsEntity + "', 'flights') -%}"
            + "{%- if fl is iterable and fl is not string -%}"
            + "{%- for f in fl | selectattr('distance', 'number') | sort(attribute='distance') -%}"
            + "{%- if ns.out | length < " + maxPlanes + " and f.distance <= " + maxDistanceKm
            + " and (f.on_ground | default(0) | int(0)) == 0 -%}"
            + "{%- set ns.out = ns.out + [[(f.id or '') | string, f.flight_number or f.callsign or '',"
            + " f.aircraft_code or '', (f.heading or 0) | int(0)] | join(',')] -%}"
            + "{%- endif -%}{%- endfor -%}{%- endif -%}"
            + "v1|{{ 'n' if is_state('sun.sun', 'below_horizon') else 'd' }}|{{ ns.out | join('|') }}";
    }

    private static String string(Map<String, Object> s, String key, String fallback) {
        Object v = s == null ? null : s.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    private static int number(Map<String, Object> s, String key, int fallback, int min, int max) {
        Object v = s == null ? null : s.get(key);
        int n = v instanceof Number ? (int) Math.round(((Number) v).doubleValue()) : fallback;
        return Math.max(min, Math.min(max, n));
    }

    private static boolean bool(Map<String, Object> s, String key, boolean fallback) {
        Object v = s == null ? null : s.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }
}
