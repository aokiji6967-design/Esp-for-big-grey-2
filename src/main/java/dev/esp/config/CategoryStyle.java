package dev.esp.config;

/** Per-category visual configuration: colours, gradients, chroma and shape mode. */
public final class CategoryStyle {
    private EspColor outlineColor;
    private EspColor fillColor;
    private BoxMode boxMode;
    private ShapeMode shapeMode;
    private boolean tracers;
    private boolean gradient;
    private EspColor gradientColor;
    private boolean chroma;
    private double chromaSpeed;
    private boolean healthColor;

    public CategoryStyle(EspColor outlineColor, EspColor fillColor, BoxMode boxMode, ShapeMode shapeMode) {
        this.outlineColor = outlineColor;
        this.fillColor = fillColor;
        this.boxMode = boxMode;
        this.shapeMode = shapeMode;
        this.tracers = false;
        this.gradient = false;
        this.gradientColor = outlineColor;
        this.chroma = false;
        this.chromaSpeed = 0.35;
        this.healthColor = false;
    }

    public EspColor outlineColor() {
        return outlineColor;
    }

    public void outlineColor(EspColor color) {
        this.outlineColor = color;
    }

    public EspColor fillColor() {
        return fillColor;
    }

    public void fillColor(EspColor color) {
        this.fillColor = color;
    }

    public BoxMode boxMode() {
        return boxMode;
    }

    public void boxMode(BoxMode mode) {
        this.boxMode = mode;
    }

    public ShapeMode shapeMode() {
        return shapeMode;
    }

    public void shapeMode(ShapeMode mode) {
        this.shapeMode = mode;
    }

    public boolean tracers() {
        return tracers;
    }

    public void tracers(boolean value) {
        this.tracers = value;
    }

    public boolean gradient() {
        return gradient;
    }

    public void gradient(boolean value) {
        this.gradient = value;
    }

    public EspColor gradientColor() {
        return gradientColor;
    }

    public void gradientColor(EspColor color) {
        this.gradientColor = color;
    }

    public boolean chroma() {
        return chroma;
    }

    public void chroma(boolean value) {
        this.chroma = value;
    }

    public double chromaSpeed() {
        return chromaSpeed;
    }

    public void chromaSpeed(double value) {
        this.chromaSpeed = value;
    }

    public boolean healthColor() {
        return healthColor;
    }

    public void healthColor(boolean value) {
        this.healthColor = value;
    }
}
