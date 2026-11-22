// SPDX-License-Identifier: Apache-2.0
package nl.thuis.flyover;

import android.graphics.Path;

/** Turns the absolute SVG path data in {@link Shapes} (M, L, H, V, C, Z) into an Android Path. */
final class SvgPath {
    /** The commands with their coordinates, as parsed. Kept free of Android so tests can check every shape. */
    static float[][] commands(String d) {
        java.util.List<float[]> out = new java.util.ArrayList<>();
        int i = 0, n = d.length();
        char cmd = 0;
        while (i < n) {
            char ch = d.charAt(i);
            if (Character.isWhitespace(ch) || ch == ',') { i++; continue; }
            if (Character.isLetter(ch)) {
                cmd = ch;
                i++;
                if (cmd == 'Z') out.add(new float[] {'Z'});
                continue;
            }
            int args;
            switch (cmd) {
                case 'M': case 'L': args = 2; break;
                case 'H': case 'V': args = 1; break;
                case 'C': args = 6; break;
                default: throw new IllegalArgumentException("Unsupported path command '" + cmd + "' in " + d);
            }
            float[] c = new float[args + 1];
            c[0] = cmd;
            for (int a = 1; a <= args; a++) {
                while (i < n && (Character.isWhitespace(d.charAt(i)) || d.charAt(i) == ',')) i++;
                int start = i;
                if (i < n && (d.charAt(i) == '-' || d.charAt(i) == '+')) i++;
                while (i < n && (Character.isDigit(d.charAt(i)) || d.charAt(i) == '.')) i++;
                if (start == i) throw new IllegalArgumentException("Missing number in " + d);
                c[a] = Float.parseFloat(d.substring(start, i));
            }
            out.add(c);
            if (cmd == 'M') cmd = 'L';   // further pairs after M are lines
        }
        return out.toArray(new float[0][]);
    }

    static Path parse(String d) {
        Path path = new Path();
        float x = 0, y = 0;
        for (float[] c : commands(d)) {
            switch ((char) c[0]) {
                case 'M': path.moveTo(x = c[1], y = c[2]); break;
                case 'L': path.lineTo(x = c[1], y = c[2]); break;
                case 'H': path.lineTo(x = c[1], y); break;
                case 'V': path.lineTo(x, y = c[1]); break;
                case 'C': path.cubicTo(c[1], c[2], c[3], c[4], x = c[5], y = c[6]); break;
                case 'Z': path.close(); break;
                default: break;
            }
        }
        return path;
    }

    private SvgPath() {}
}
