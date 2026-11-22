// SPDX-License-Identifier: Apache-2.0
package nl.thuis.flyover;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Side-view silhouettes for the kinds of aircraft that fly over, chosen from the ICAO type designator
 * (A20N, B738, AT76, EC35, ...) and Flightradar24's aircraft category. Original drawings.
 *
 * Every shape is drawn in the same 120 x 44 box, nose to the right, as plain SVG path data (absolute M, L, H, V, C, Z
 * only), so the plugin and the preview render exactly the same thing. Parts are drawn in order.
 */
final class Shapes {
    static final float W = 120f, H = 44f;

    static final int BODY = 0, SHADE = 1, WINDOWS = 2, GLASS = 3;

    static final class Part {
        final int role;
        final String d;
        Part(int role, String d) { this.role = role; this.d = d; }
    }

    /** A spinning propeller or rotor, drawn live on every frame. */
    static final class Rotor {
        static final int PROP = 0;      // seen edge-on, upright (aeroplane propeller)
        static final int MAIN = 1;      // seen edge-on, flat (helicopter main rotor)
        static final int DISC = 2;      // seen face-on (helicopter tail rotor)
        final int kind;
        final float cx, cy, r, revsPerSecond;
        Rotor(int kind, float cx, float cy, float r, float revsPerSecond) {
            this.kind = kind; this.cx = cx; this.cy = cy; this.r = r; this.revsPerSecond = revsPerSecond;
        }
    }

    static final class Shape {
        final String name;
        final Part[] parts;
        final Rotor[] rotors;
        final float[] strobe, beacon, wingtip;  // light positions, or null
        final float trailY;                     // contrail height, NaN for none (props and helicopters fly low)
        final float scale;                      // size relative to an airliner
        final float slowness;                   // crossing time relative to an airliner
        Shape(String name, Part[] parts, Rotor[] rotors, float[] strobe, float[] beacon, float[] wingtip,
              float trailY, float scale, float slowness) {
            this.name = name; this.parts = parts; this.rotors = rotors; this.strobe = strobe; this.beacon = beacon;
            this.wingtip = wingtip; this.trailY = trailY; this.scale = scale; this.slowness = slowness;
        }
    }

    private static Part p(int role, String d) { return new Part(role, d); }
    private static float[] at(float x, float y) { return new float[] {x, y}; }
    private static final Rotor[] NONE = new Rotor[0];

    // ---------- Single-aisle jet: A320 family, 737, E-Jets, A220 ----------
    static final Shape NARROW = new Shape("narrow", new Part[] {
        p(BODY, "M8 21L2 4H9L24 19Z"),
        p(SHADE, "M10 24L4 31H10L19 25Z"),
        p(SHADE, "M44 26L66 42H74L60 26Z"),
        p(BODY, "M6 22C6 19.8 7.8 18 10 18H92C101 18 110 20.6 114 23.2C115.4 24.1 115.4 25.9 114 26.8"
            + "C110 29.4 101 31 92 31H14C9.6 31 6 28 6 25Z"),
        p(BODY, "M60 31H69C70.3 31 71 31.9 71 33.2C71 34.6 70.3 35.5 69 35.5H60C58.7 35.5 58 34.6 58 33.2"
            + "C58 31.9 58.7 31 60 31Z"),
        p(WINDOWS, "M26.8 20.3H81.2C81.6 20.3 82 20.7 82 21.1C82 21.5 81.6 21.9 81.2 21.9H26.8"
            + "C26.4 21.9 26 21.5 26 21.1C26 20.7 26.4 20.3 26.8 20.3Z"),
        p(GLASS, "M101 20.5C104 20.8 107 21.6 109.4 22.7L108.4 23.6C106 22.7 103.4 22.1 100.8 21.8Z"),
    }, NONE, at(3, 5), at(60, 17.4f), at(71, 42.5f), 22.5f, 1f, 1f);

    private static final String WIDE_BODY =
        "M4 22.5C4 19 6.5 16 10 16H90C101 16 111 19 115.5 22.5C117 23.8 117 26 115.5 27.3"
        + "C111 30.5 101 32 90 32H14C8 32 4 29 4 25.5Z";
    private static final String WIDE_WINDOWS =
        "M24.8 19.3H83.2C83.6 19.3 84 19.7 84 20.1C84 20.5 83.6 20.9 83.2 20.9H24.8"
        + "C24.4 20.9 24 20.5 24 20.1C24 19.7 24.4 19.3 24.8 19.3Z";
    private static final String WIDE_GLASS = "M102 19C106 19.5 110 20.7 112.8 22.2L111.7 23.1C109 21.8 105.5 20.8 101.8 20.4Z";
    private static final String WIDE_FIN = "M7 20L1 1H9L25 17Z";
    private static final String WIDE_TAILPLANE = "M9 24L2 32H9L19 25.5Z";
    private static final String WIDE_WING = "M42 27L64 43H73L58 27Z";
    private static final String INNER_POD =
        "M48 31.5H60C61.8 31.5 62.6 32.6 62.6 34.2C62.6 35.8 61.8 36.9 60 36.9H48C46.6 36.9 46 35.8 46 34.2"
        + "C46 32.6 46.6 31.5 48 31.5Z";
    private static final String OUTER_POD =
        "M60 36.5H71C72.6 36.5 73.3 37.5 73.3 38.9C73.3 40.3 72.6 41.3 71 41.3H60C58.8 41.3 58.3 40.3 58.3 38.9"
        + "C58.3 37.5 58.8 36.5 60 36.5Z";

    // ---------- Twin-aisle twin: A330, A350, 767, 777, 787 ----------
    static final Shape WIDE = new Shape("wide", new Part[] {
        p(BODY, WIDE_FIN), p(SHADE, WIDE_TAILPLANE), p(SHADE, WIDE_WING), p(BODY, WIDE_BODY),
        p(BODY, "M55 31.5H68C70 31.5 71 32.8 71 34.6C71 36.4 70 37.7 68 37.7H55C53.5 37.7 53 36.4 53 34.6"
            + "C53 32.8 53.5 31.5 55 31.5Z"),
        p(WINDOWS, WIDE_WINDOWS), p(GLASS, WIDE_GLASS),
    }, NONE, at(2.5f, 2.5f), at(60, 15.4f), at(69, 43), 24f, 1.3f, 1f);

    // ---------- Four-engine: A340, Il-96 ----------
    static final Shape QUAD = new Shape("quad", new Part[] {
        p(BODY, WIDE_FIN), p(SHADE, WIDE_TAILPLANE), p(SHADE, WIDE_WING), p(BODY, WIDE_BODY),
        p(BODY, INNER_POD), p(BODY, OUTER_POD), p(WINDOWS, WIDE_WINDOWS), p(GLASS, WIDE_GLASS),
    }, NONE, at(2.5f, 2.5f), at(60, 15.4f), at(69, 43), 24f, 1.35f, 1f);

    // ---------- Jumbo with upper deck: 747, A380 ----------
    static final Shape JUMBO = new Shape("jumbo", new Part[] {
        p(BODY, WIDE_FIN), p(SHADE, WIDE_TAILPLANE), p(SHADE, WIDE_WING), p(BODY, WIDE_BODY),
        p(BODY, "M66 16.5C74 12 88 11.3 98 13.4C104 14.7 108.6 16.9 111.5 19.4L95 16.4Z"),
        p(BODY, INNER_POD), p(BODY, OUTER_POD), p(WINDOWS, WIDE_WINDOWS),
        p(WINDOWS, "M78 14.6H94C94.4 14.6 94.7 14.9 94.7 15.3C94.7 15.7 94.4 16 94 16H78C77.6 16 77.3 15.7 77.3 15.3"
            + "C77.3 14.9 77.6 14.6 78 14.6Z"),
        p(GLASS, "M100 14.6C103.6 15.2 106.8 16.4 109.2 17.9L108.1 18.7C105.8 17.4 102.9 16.4 99.8 15.9Z"),
    }, NONE, at(2.5f, 2.5f), at(60, 15.4f), at(69, 43), 24f, 1.45f, 1f);

    // ---------- Rear engines and T-tail: CRJ, ERJ, business jets, MD-80, Fokker ----------
    static final Shape REARJET = new Shape("rearjet", new Part[] {
        p(BODY, "M10 21L5 5H12L25 19.5Z"),
        p(SHADE, "M1 6L4 4H19L22 6Z"),
        p(SHADE, "M48 26L62 40H69L58 26Z"),
        p(BODY, "M8 22.5C8 20.5 9.5 19 11.5 19H94C103 19 110 21.2 113.5 23.4C114.8 24.2 114.8 25.8 113.5 26.6"
            + "C110 28.6 103 29.5 94 29.5H15C11 29.5 8 27.2 8 25Z"),
        p(BODY, "M28 20.9H32L31 22.4H29Z"),
        p(BODY, "M24 15.5H36C38 15.5 39 16.6 39 18.2C39 19.8 38 20.9 36 20.9H24C22.6 20.9 22 19.8 22 18.2"
            + "C22 16.6 22.6 15.5 24 15.5Z"),
        p(WINDOWS, "M42.6 21.4H85.4C85.7 21.4 86 21.7 86 22.05C86 22.4 85.7 22.7 85.4 22.7H42.6"
            + "C42.3 22.7 42 22.4 42 22.05C42 21.7 42.3 21.4 42.6 21.4Z"),
        p(GLASS, "M101 21.3C104 21.6 107 22.3 109.5 23.4L108.6 24.2C106.2 23.4 103.6 22.8 100.8 22.6Z"),
    }, NONE, at(2, 5), at(60, 18.4f), at(66, 40), 18.2f, 0.8f, 1f);

    // ---------- Regional turboprop, high wing: ATR, Dash 8, Saab, Fokker 50 ----------
    static final Shape TURBOPROP = new Shape("turboprop", new Part[] {
        p(BODY, "M10 21L5 4H12L26 19.5Z"),
        p(SHADE, "M1 5L4 3H19L22 5Z"),
        p(BODY, "M8 23C8 20.8 9.8 19 12 19H92C101 19 108 21 111.5 23.5C113 24.6 113 26.4 111.5 27.5"
            + "C108 30 101 30.8 92 30.8H15C11 30.8 8 28.3 8 25.5Z"),
        p(BODY, "M44 19.6C48 17 52 16.2 56 16.2H74C78 16.2 80.5 17.6 82 19.6Z"),
        p(SHADE, "M64 15.2H82C84 15.2 85.2 16.4 85.2 18C85.2 19.6 84 20.8 82 20.8H66C64.6 20.8 64 19.7 64 18Z"),
        p(BODY, "M48 16.4L52 13.6H79L81 16.4Z"),
        p(WINDOWS, "M30.6 21.6H63.4C63.7 21.6 64 21.9 64 22.25C64 22.6 63.7 22.9 63.4 22.9H30.6"
            + "C30.3 22.9 30 22.6 30 22.25C30 21.9 30.3 21.6 30.6 21.6Z"),
        p(GLASS, "M100 21.4C103.5 21.8 107 22.7 109.6 24L108.6 24.8C106.2 23.7 103.4 23 100.6 22.6Z"),
    }, new Rotor[] {new Rotor(Rotor.PROP, 86.4f, 18f, 8.5f, 20f)},
        at(2, 4), at(40, 18.6f), at(80, 13.8f), Float.NaN, 0.85f, 1.25f);

    // ---------- Light single-engine aeroplane: Cessna, Piper, Cirrus, Diamond ----------
    static final Shape LIGHT = new Shape("light", new Part[] {
        p(BODY, "M10 23L6 9H12L24 21.5Z"),
        p(SHADE, "M8 24L3 27.5H11L19 25Z"),
        p(BODY, "M79 29.5L77 33L78.2 33.2L80.5 29.6Z"),
        p(BODY, "M100 29L101 32.5L102 32.4L101.2 29Z"),
        p(BODY, "M75.5 34.5C75.5 33.4 76.4 32.5 77.5 32.5C78.6 32.5 79.5 33.4 79.5 34.5C79.5 35.6 78.6 36.5 77.5 36.5"
            + "C76.4 36.5 75.5 35.6 75.5 34.5Z"),
        p(BODY, "M99.8 33.8C99.8 32.9 100.5 32.2 101.4 32.2C102.3 32.2 103 32.9 103 33.8C103 34.7 102.3 35.4 101.4 35.4"
            + "C100.5 35.4 99.8 34.7 99.8 33.8Z"),
        p(BODY, "M10 24C10 23 10.8 22.3 12 22.3L60 20H92C98 20 103 21.5 106 23.5C107 24.3 107 26 106 26.8"
            + "C103 28.6 98 29.8 92 29.8H72L14 27C11.5 26.9 10 26 10 25Z"),
        p(BODY, "M86 20L80 28.5L81.2 28.8L87.6 20Z"),
        p(SHADE, "M66 20L68 17.4H96L97 20Z"),
        p(GLASS, "M75 21.4H88L90.4 25.2H75Z"),
    }, new Rotor[] {new Rotor(Rotor.PROP, 107.6f, 25.1f, 6.5f, 30f)},
        at(7, 9.6f), at(40, 21.6f), at(95, 17.6f), Float.NaN, 0.55f, 1.5f);

    // ---------- Helicopter ----------
    static final Shape HELICOPTER = new Shape("helicopter", new Part[] {
        p(BODY, "M16 20.5L62 21.5V26.5L16 23.2Z"),
        p(BODY, "M12 12H18L21 23L14 24.5Z"),
        p(BODY, "M72 32.8L70 38.6H71.4L73.6 32.8Z"),
        p(BODY, "M92 32.8L93 38.6H94.4L93.6 32.8Z"),
        p(BODY, "M64 38.5H100C101.5 38.5 102.6 37.8 103.2 36.6L104 36.9C103.2 38.6 101.7 39.6 100 39.6H64Z"),
        p(BODY, "M64 18C72 13.5 86 13 96 16.5C103 19 107 23.5 107 27.5C107 31 103 33 96 33H70"
            + "C64 33 60 30 60 26C60 22.5 61 20 64 18Z"),
        p(SHADE, "M70 13.5C74 11.5 86 11.5 90 13.6L92 16H68Z"),
        p(BODY, "M79.5 8.5H81.5V12.2H79.5Z"),
        p(WINDOWS, "M74 17.5H86V23.5H74Z"),
        p(GLASS, "M90 16.8C97 18.4 102.5 21.8 104.5 25.6H92Z"),
    }, new Rotor[] {new Rotor(Rotor.MAIN, 80.5f, 8.5f, 46f, 2.2f), new Rotor(Rotor.DISC, 14.5f, 16.5f, 5.5f, 3f)},
        at(13, 12.5f), at(80.5f, 13f), null, Float.NaN, 0.62f, 1.7f);

    static final Shape[] ALL = {NARROW, WIDE, QUAD, JUMBO, REARJET, TURBOPROP, LIGHT, HELICOPTER};

    /** The slowest shape's crossing time relative to an airliner. */
    static final float MAX_SLOWNESS = 1.7f;

    // ---------- Type designator to shape ----------

    private static Set<String> set(String... codes) { return new HashSet<>(Arrays.asList(codes)); }

    private static final Set<String> HELI = set("EC20", "EC25", "EC30", "EC35", "EC45", "EC55", "EC75", "EC65",
        "H160", "H175", "AS50", "AS55", "AS65", "AS32", "AS3B", "B06", "B06T", "B407", "B412", "B427", "B429",
        "B430", "B505", "B212", "R22", "R44", "R66", "A109", "A119", "A139", "A149", "A169", "A189", "AW09",
        "S76", "S92", "S61", "NH90", "EH10", "MI8", "MI17", "H60", "UH1", "UH60", "BK17", "CH47", "H47", "H64",
        "LYNX", "WILD", "G2CA", "MD52", "MD60", "MD90H", "H269", "H500", "EN28", "EN48", "GAZL", "ALO2", "ALO3",
        "PUMA", "SUCO", "TIGR", "K126", "KMAX");
    private static final Set<String> JUMBO_TYPES = set("A388", "B741", "B742", "B743", "B744", "B748", "B74S",
        "B74R", "BLCF");
    private static final Set<String> QUAD_TYPES = set("A342", "A343", "A345", "A346", "IL96", "IL86", "A124",
        "A225", "C17", "IL76");
    private static final Set<String> WIDE_TYPES = set("A306", "A30B", "A310", "A332", "A333", "A337", "A338",
        "A339", "A359", "A35K", "B762", "B763", "B764", "B772", "B773", "B77L", "B77W", "B778", "B779", "B788",
        "B789", "B78X", "MD11", "DC10", "L101", "A3ST", "K35R", "KC10", "C5", "C5M");
    private static final Set<String> REAR_TYPES = set("CRJ1", "CRJ2", "CRJ7", "CRJ9", "CRJX", "E135", "E145",
        "E35L", "E45X", "F70", "F100", "F28", "B712", "DC93", "DC95", "MD81", "MD82", "MD83", "MD87", "MD88",
        "MD90", "CL30", "CL35", "CL60", "GLF2", "GLF3", "GLF4", "GLF5", "GLF6", "GL5T", "GL6T", "GL7T", "GLEX",
        "GA5C", "GA6C", "GA7C", "E50P", "E55P", "E545", "E550", "LJ31", "LJ35", "LJ40", "LJ45", "LJ60", "LJ75",
        "FA10", "FA20", "FA50", "FA7X", "FA8X", "FA6X", "F900", "F2TH", "H25B", "H25C", "PC24", "HDJT", "EA50",
        "SF50", "C25A", "C25B", "C25C", "C25M", "C500", "C501", "C510", "C525", "C526", "C550", "C551", "C55B",
        "C560", "C56X", "C650", "C680", "C68A", "C700", "C750", "BE40", "PRM1", "G150", "G280", "ASTR", "GALX",
        "J328", "YK42", "T154", "IL62", "VC10", "B727", "B721", "B722", "TU34");
    private static final Set<String> TURBOPROP_TYPES = set("AT43", "AT44", "AT45", "AT46", "AT72", "AT73", "AT75",
        "AT76", "ATP", "DH8A", "DH8B", "DH8C", "DH8D", "DHC6", "DHC7", "SF34", "SB20", "F27", "F50", "D328",
        "B190", "BE20", "BE30", "BE99", "BE9L", "B350", "JS31", "JS32", "JS41", "E110", "E120", "SW3",
        "SW4", "L410", "C212", "CN35", "C295", "AN26", "AN24", "AN28", "AN32", "C130", "C30J", "A400", "C160",
        "P3", "P180", "PAY2", "PAY3", "MU2", "SH33", "SH36", "BN2T", "Y12", "MA60");
    private static final Set<String> LIGHT_TYPES = set("C150", "C152", "C162", "C170", "C172", "C175", "C177",
        "C77R", "C180", "C182", "C185", "C195", "C206", "C207", "C208", "C210", "C337", "P210", "P28A", "P28B",
        "P28R", "P28T", "P32R", "P32T", "PA11", "PA12", "PA18", "PA20", "PA22", "PA24", "PA25", "PA28", "PA30",
        "PA31", "PA32", "PA34", "PA38", "PA44", "PA46", "P46T", "SR20", "SR22", "S22T", "DA20", "DA40", "DA42",
        "DA50", "DA62", "DV20", "BE23", "BE33", "BE35", "BE36", "BE55", "BE58", "BE76", "M20P", "M20T", "M20J", "TB9",
        "TB10", "TB20", "TB21", "TBM7", "TBM8", "TBM9", "PC6T", "PC7", "PC9", "PC12", "PC21", "AA1", "AA5", "DR40",
        "DR10", "DR22", "DR30", "RV4", "RV6", "RV7", "RV8", "RV9", "RV10", "RV12", "RV14", "ULAC", "GLID", "AT3",
        "AT5", "C42", "CTSW", "SIRA", "WT9", "TOBA", "AQUI", "J3", "CUB", "CH60", "EV97", "BL8", "GA8", "M7",
        "PIVI", "FDCT", "VL3", "EXPL", "G115", "SF25", "TAMP", "SUPE", "ROB1", "SPIT", "P51", "HUSK", "DHC1",
        "DHC2", "DHC3", "YK52", "Z42", "Z43", "Z242", "DIMO", "EGLT", "G109", "ASK21", "DUOD", "ARCP");

    /** The silhouette for an ICAO type designator and Flightradar24 category ("Airplane", "Helicopter", ...). */
    static Shape forType(String typeCode, String category) {
        String code = typeCode == null ? "" : typeCode.trim().toUpperCase(Locale.ROOT);
        String cat = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        if (cat.contains("heli") || cat.contains("gyro") || HELI.contains(code)) return HELICOPTER;
        if (JUMBO_TYPES.contains(code)) return JUMBO;
        if (QUAD_TYPES.contains(code)) return QUAD;
        if (WIDE_TYPES.contains(code)) return WIDE;
        if (REAR_TYPES.contains(code)) return REARJET;
        if (TURBOPROP_TYPES.contains(code)) return TURBOPROP;
        if (LIGHT_TYPES.contains(code) || cat.contains("glider") || cat.contains("light")) return LIGHT;
        return NARROW;   // every other airliner, and anything unknown
    }

    private Shapes() {}
}
