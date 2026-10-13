package dev.esp.config;

/**
 * An immutable ARGB colour with 8 bit channels, used for every configurable colour in the mod.
 *
 * <p>Stored as a packed {@code int} in the config file so a colour round-trips exactly.
 */
public final class EspColor {
    public static final EspColor WHITE = new EspColor(255, 255, 255, 255);

    public final int r;
    public final int g;
    public final int b;
    public final int a;

    public EspColor(int r, int g, int b, int a) {
        this.r = clamp(r);
        this.g = clamp(g);
        this.b = clamp(b);
        this.a = clamp(a);
    }

    public EspColor(int r, int g, int b) {
        this(r, g, b, 255);
    }

    public static EspColor ofArgb(int argb) {
        return new EspColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    /**
     * Parses {@code #RGB}, {@code #RRGGBB} and {@code #RRGGBBAA} (the {@code #} is optional).
     *
     * <p>The short form expands each digit by repetition, the way CSS does, and the 8 digit form
     * keeps its alpha. Both match {@link #toHex()} exactly, so a colour survives a round trip
     * through the GUI's hex field.
     *
     * @return the parsed colour, or {@code null} when the input is not a valid hex colour
     */
    public static EspColor parseHex(String input) {
        if (input == null) return null;

        String hex = input.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.isEmpty() || !hex.matches("[0-9a-fA-F]+")) return null;

        try {
            long value = Long.parseLong(hex, 16);

            return switch (hex.length()) {
                case 3 -> {
                    int r = (int) ((value >> 8) & 0xF);
                    int g = (int) ((value >> 4) & 0xF);
                    int b = (int) (value & 0xF);
                    yield new EspColor(r * 17, g * 17, b * 17, 255);
                }
                case 6 -> new EspColor((int) (value >> 16) & 0xFF, (int) (value >> 8) & 0xFF, (int) value & 0xFF, 255);
                case 8 -> new EspColor(
                    (int) (value >> 24) & 0xFF,
                    (int) (value >> 16) & 0xFF,
                    (int) (value >> 8) & 0xFF,
                    (int) value & 0xFF
                );
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int toArgb() {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** @return the colour as {@code RRGGBBAA}, the format accepted by {@link #parseHex(String)} */
    public String toHex() {
        return String.format("#%02X%02X%02X%02X", r, g, b, a);
    }

    public EspColor withAlpha(int alpha) {
        return new EspColor(r, g, b, alpha);
    }

    public EspColor mulAlpha(double factor) {
        return withAlpha((int) Math.round(a * factor));
    }

    /** @return this colour at {@code progress} (0..1) towards {@code other} */
    public EspColor lerp(EspColor other, double progress) {
        double t = clamp01(progress);

        return new EspColor(
            (int) Math.round(r + (other.r - r) * t),
            (int) Math.round(g + (other.g - g) * t),
            (int) Math.round(b + (other.b - b) * t),
            (int) Math.round(a + (other.a - a) * t)
        );
    }

    /** @return a fully saturated colour cycling over {@code hue} (0..1) */
    public static EspColor fromHue(double hue, double saturation, double value, int alpha) {
        double h = hue - Math.floor(hue);
        double s = clamp01(saturation);
        double v = clamp01(value);

        int i = (int) (h * 6);
        double f = h * 6 - i;
        double p = v * (1 - s);
        double q = v * (1 - f * s);
        double t = v * (1 - (1 - f) * s);

        double r;
        double g;
        double b;

        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }

        return new EspColor((int) Math.round(r * 255), (int) Math.round(g * 255), (int) Math.round(b * 255), alpha);
    }

    /** @return the hue (0..1) of this colour */
    public double hue() {
        double rn = r / 255.0;
        double gn = g / 255.0;
        double bn = b / 255.0;

        double max = Math.max(rn, Math.max(gn, bn));
        double min = Math.min(rn, Math.min(gn, bn));
        double delta = max - min;

        if (delta < 1.0E-6) return 0.0;

        double hue;
        if (max == rn) hue = (gn - bn) / delta + (gn < bn ? 6 : 0);
        else if (max == gn) hue = (bn - rn) / delta + 2;
        else hue = (rn - gn) / delta + 4;

        return hue / 6.0;
    }

    /** @return this colour rotated to the given hue, keeping saturation, value and alpha */
    public EspColor withHue(double hue) {
        double max = Math.max(r, Math.max(g, b)) / 255.0;
        double min = Math.min(r, Math.min(g, b)) / 255.0;
        double saturation = max < 1.0E-6 ? 0 : (max - min) / max;

        return fromHue(hue, saturation, max, a);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EspColor other)) return false;

        return toArgb() == other.toArgb();
    }

    @Override
    public int hashCode() {
        return toArgb();
    }

    @Override
    public String toString() {
        return toHex();
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    private static double clamp01(double value) {
        return value < 0 ? 0 : Math.min(value, 1);
    }
}
