package dev.esp.render;

/**
 * The width to draw camera facing ribbons with, expressed in screen pixels.
 *
 * <p>Outlines used to be given a width in world units, which looks wrong as soon as you move: an
 * edge two blocks away becomes a wide band while the same edge across the map stays a hairline. A
 * pixel width is what a thickness setting is expected to mean, so the configured value stays the
 * same on screen at any distance and only the ribbon's world size scales with it.
 *
 * <p>The conversion is exact for a ribbon that faces the camera and is offset perpendicular to the
 * view ray. For a vertical projection with scale {@code m11}, a world offset of {@code w} at
 * distance {@code d} covers {@code height * m11 * w / (2 * d)} pixels, so
 * {@code w = pixels * 2 * d / (height * m11)} for the full width, and half of that for the offset.
 *
 * @param pixels                the configured line width, in pixels
 * @param worldPerPixelPerBlock world units per pixel, per block of distance: {@code 1 / (height * m11)}
 */
public record LineStyle(double pixels, double worldPerPixelPerBlock) {
    /** Fallback vertical scale for a viewport the game has not sized yet. */
    private static final double FALLBACK = 1.0 / 1080.0;

    /**
     * @param pixels      the configured line width, in pixels
     * @param projectionM11 the vertical scale of the frame's projection matrix
     * @param framebufferHeight the height of the frame in pixels
     */
    public static LineStyle of(double pixels, float projectionM11, int framebufferHeight) {
        boolean usable = projectionM11 > 1.0E-4f && framebufferHeight > 0 && Double.isFinite(projectionM11);
        double scale = usable ? 1.0 / (framebufferHeight * projectionM11) : FALLBACK;

        return new LineStyle(pixels, scale);
    }

    /** @return the ribbon's half width, in world units, for an edge {@code distance} blocks away */
    public double halfWidth(double distance) {
        return pixels * distance * worldPerPixelPerBlock;
    }

    /** @return the same width in pixels, never narrower than a single pixel */
    public LineStyle atLeastOnePixel() {
        return pixels >= 1.0 ? this : new LineStyle(1.0, worldPerPixelPerBlock);
    }
}
