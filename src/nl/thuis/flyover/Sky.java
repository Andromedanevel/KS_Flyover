// SPDX-License-Identifier: Apache-2.0
package nl.thuis.flyover;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the overlay should draw, handed from the plugin worker to the main thread as one immutable snapshot.
 * Positions are derived from the wall clock, so a rebuilt view carries on exactly where the old one was.
 */
final class Sky {
    /** One plane as reported by HA. */
    static final class Flight {
        final String id, code, type, from, to, fromCity, toCity, category;
        final int heading;
        Flight(String id, String code, String type, int heading) {
            this(id, code, type, heading, "", "", "", "", "");
        }
        Flight(String id, String code, String type, int heading, String from, String to, String fromCity, String toCity) {
            this(id, code, type, heading, from, to, fromCity, toCity, "");
        }
        Flight(String id, String code, String type, int heading, String from, String to, String fromCity, String toCity,
               String category) {
            this.id = id; this.code = code; this.type = type; this.heading = heading;
            this.from = from; this.to = to; this.fromCity = fromCity; this.toCity = toCity; this.category = category;
        }
        /** The silhouette for this aircraft. */
        Shapes.Shape shape() { return Shapes.forType(type, category); }
        /** Eastbound planes (heading 0 to 179) fly left to right, the way they would on a north-up map. */
        boolean leftToRight() { int h = ((heading % 360) + 360) % 360; return h < 180; }
        String label() {
            if (code.isEmpty()) return type;
            if (type.isEmpty()) return code;
            return code + " · " + type;
        }
        /** The label in one of the Config.LABEL_* styles. Missing route details fall back to the flight number. */
        String label(int style) {
            String codes = route(from, to), cities = route(fromCity, toCity);
            switch (style) {
                case Config.LABEL_CODES: return codes.isEmpty() ? label() : codes;
                case Config.LABEL_CITIES: return !cities.isEmpty() ? cities : codes.isEmpty() ? label() : codes;
                case Config.LABEL_FLIGHT_ROUTE:
                    if (codes.isEmpty()) return label();
                    return code.isEmpty() ? codes : code + " · " + codes;
                default: return label();
            }
        }
        private static String route(String a, String b) {
            if (a.isEmpty() && b.isEmpty()) return "";
            if (a.isEmpty()) return "→ " + b;
            if (b.isEmpty()) return a + " →";
            return a + " → " + b;
        }
    }

    /** A plane on screen: when it was first seen, which lane it uses and, once it has left the area, when. */
    static final class Track {
        final Flight flight;
        final long firstSeen;
        final long goneAt;
        final int lane;
        Track(Flight flight, long firstSeen, long goneAt, int lane) {
            this.flight = flight; this.firstSeen = firstSeen; this.goneAt = goneAt; this.lane = lane;
        }
    }

    static final Sky EMPTY = new Sky(Collections.<Track>emptyList(), false, null);

    final List<Track> tracks;
    final boolean night;
    final Config config;

    Sky(List<Track> tracks, boolean night, Config config) {
        this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
        this.night = night;
        this.config = config;
    }

    /** The parsed template result, or null when it is not one. */
    static final class Reading {
        final boolean night;
        final List<Flight> flights;
        Reading(boolean night, List<Flight> flights) { this.night = night; this.flights = flights; }
    }

    static Reading parse(Object result) {
        if (!(result instanceof String)) return null;
        String[] parts = ((String) result).trim().split("\\|", -1);
        if (parts.length < 2 || !"v1".equals(parts[0])) return null;
        List<Flight> flights = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            String[] f = parts[i].split(",", -1);
            if (f.length < 4) continue;
            int heading;
            try { heading = Integer.parseInt(f[3].trim()); } catch (NumberFormatException e) { heading = 90; }
            String code = f[1].trim(), type = f[2].trim();
            String id = f[0].trim().isEmpty() ? code + "/" + type : f[0].trim();
            flights.add(new Flight(id, code, type, heading, field(f, 4), field(f, 5), field(f, 6), field(f, 7), field(f, 8)));
        }
        return new Reading("n".equals(parts[1]), flights);
    }

    private static String field(String[] f, int i) { return i < f.length ? f[i].trim() : ""; }

    /**
     * Folds a new reading into the current tracks. Planes keep the time they were first seen, so their animation does
     * not restart on every HA update. A plane that left the area finishes the pass it is on and is then dropped.
     */
    static List<Track> merge(List<Track> current, List<Flight> flights, long now, Config config) {
        Map<String, Track> previous = new LinkedHashMap<>();
        for (Track t : current) previous.put(t.flight.id, t);
        List<Track> next = new ArrayList<>();
        boolean[] lanes = new boolean[3];

        for (Flight f : flights) { // planes still around keep their lane and start time
            Track t = previous.get(f.id);
            if (t != null) {
                next.add(new Track(f, t.firstSeen, Long.MAX_VALUE, t.lane));
                lanes[t.lane] = true;
                previous.remove(f.id);
            }
        }
        long cycle = config.cycleMs();
        long longest = config.longestCycleMs();
        for (Track t : previous.values()) { // planes that left: let the running pass finish
            long gone = t.goneAt == Long.MAX_VALUE ? now : t.goneAt;
            if (now - gone < longest && !lanes[t.lane]) {
                next.add(new Track(t.flight, t.firstSeen, gone, t.lane));
                lanes[t.lane] = true;
            }
        }
        int added = 0;
        for (Flight f : flights) { // new planes take a free lane and enter now, staggered if several arrive together
            boolean known = false;
            for (Track t : next) if (t.flight.id.equals(f.id)) { known = true; break; }
            if (known) continue;
            int lane = 0;
            while (lane < 2 && lanes[lane]) lane++;
            if (lane >= 2) continue;
            lanes[lane] = true;
            next.add(new Track(f, now + added * (long) (cycle * 0.45), Long.MAX_VALUE, lane));
            added++;
        }
        return next;
    }
}
