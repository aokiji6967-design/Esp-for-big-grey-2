package dev.esp.config;

/** Global rendering controls shared by every category. */
public final class RenderSettings {
    private double range;
    private int maxTargets;
    private double lineThickness;
    private boolean occlusionCheck;
    private boolean interpolate;
    private int blockScanBudget;
    private double tracerAlpha;

    public RenderSettings() {
        this.range = 48.0;
        this.maxTargets = 192;
        this.lineThickness = 2.0;
        this.occlusionCheck = false;
        this.interpolate = true;
        this.blockScanBudget = 65536;
        this.tracerAlpha = 0.65;
    }

    /**
     * Configured distance, in blocks, at which a target is drawn. This is a lower bound only: the
     * effective range the scanners and renderer use is never shorter than the client's render
     * distance, so everything visible within loaded terrain is trackable.
     */
    public double range() {
        return range;
    }

    public void range(double value) {
        this.range = value;
    }

    /** Upper bound on how many targets are drawn in one frame, to bound frame cost. */
    public int maxTargets() {
        return maxTargets;
    }

    public void maxTargets(int value) {
        this.maxTargets = value;
    }

    /**
     * Width of outlines and tracers, in screen pixels. A pixel width keeps lines equally thin at
     * every distance; a world space width would turn nearby edges into wide bands.
     */
    public double lineThickness() {
        return lineThickness;
    }

    public void lineThickness(double value) {
        this.lineThickness = value;
    }

    /** When enabled, targets without line of sight are skipped (they no longer show through walls). */
    public boolean occlusionCheck() {
        return occlusionCheck;
    }

    public void occlusionCheck(boolean value) {
        this.occlusionCheck = value;
    }

    /** Smooths entity movement between ticks instead of snapping to the last tick position. */
    public boolean interpolate() {
        return interpolate;
    }

    public void interpolate(boolean value) {
        this.interpolate = value;
    }

    /** How many block positions the incremental block sweep examines per tick. */
    public int blockScanBudget() {
        return blockScanBudget;
    }

    public void blockScanBudget(int value) {
        this.blockScanBudget = value;
    }

    /** Opacity multiplier applied to tracer lines. */
    public double tracerAlpha() {
        return tracerAlpha;
    }

    public void tracerAlpha(double value) {
        this.tracerAlpha = value;
    }
}
