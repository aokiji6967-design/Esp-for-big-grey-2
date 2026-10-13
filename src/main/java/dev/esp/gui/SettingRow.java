package dev.esp.gui;

import dev.esp.config.EspColor;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * One row in the settings panel.
 *
 * <p>Rows describe themselves - what they read, what they write and what text to show - so the panel
 * only has to know how to draw and hit test each {@link Kind}, and adding a setting is a one line
 * change where the settings are declared.
 */
public final class SettingRow {
    public enum Kind {
        HEADER,
        TOGGLE,
        SLIDER,
        COLOR,
        CYCLE,
        BUTTON,
        TEXT,
        FRIENDS
    }

    private final Kind kind;
    private final String label;

    private BooleanSupplier booleanGetter;
    private Consumer<Boolean> booleanSetter;
    private DoubleSupplier doubleGetter;
    private DoubleConsumer doubleSetter;
    private Supplier<String> valueText;
    private Runnable action;
    private Supplier<EspColor> colorGetter;
    private Consumer<EspColor> colorSetter;

    private double min;
    private double max;

    private SettingRow(Kind kind, String label) {
        this.kind = kind;
        this.label = label;
    }

    public static SettingRow header(String label) {
        return new SettingRow(Kind.HEADER, label);
    }

    public static SettingRow toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        SettingRow row = new SettingRow(Kind.TOGGLE, label);
        row.booleanGetter = getter;
        row.booleanSetter = setter;

        return row;
    }

    public static SettingRow slider(
        String label, double min, double max,
        DoubleSupplier getter, DoubleConsumer setter, Supplier<String> valueText
    ) {
        SettingRow row = new SettingRow(Kind.SLIDER, label);
        row.min = min;
        row.max = max;
        row.doubleGetter = getter;
        row.doubleSetter = setter;
        row.valueText = valueText;

        return row;
    }

    public static SettingRow color(String label, Supplier<EspColor> getter, Consumer<EspColor> setter) {
        SettingRow row = new SettingRow(Kind.COLOR, label);
        row.colorGetter = getter;
        row.colorSetter = setter;

        return row;
    }

    public static SettingRow cycle(String label, Supplier<String> valueText, Runnable action) {
        SettingRow row = new SettingRow(Kind.CYCLE, label);
        row.valueText = valueText;
        row.action = action;

        return row;
    }

    public static SettingRow button(String label, Runnable action) {
        SettingRow row = new SettingRow(Kind.BUTTON, label);
        row.action = action;

        return row;
    }

    public static SettingRow text(String label) {
        return new SettingRow(Kind.TEXT, label);
    }

    public static SettingRow friends(String label) {
        return new SettingRow(Kind.FRIENDS, label);
    }

    public Kind kind() {
        return kind;
    }

    public String label() {
        return label;
    }

    public boolean value() {
        return booleanGetter != null && booleanGetter.getAsBoolean();
    }

    public void set(boolean value) {
        if (booleanSetter != null) booleanSetter.accept(value);
    }

    public double doubleValue() {
        return doubleGetter == null ? 0.0 : doubleGetter.getAsDouble();
    }

    public void setDouble(double value) {
        if (doubleSetter != null) doubleSetter.accept(value);
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public String valueText() {
        return valueText == null ? "" : valueText.get();
    }

    public void run() {
        if (action != null) action.run();
    }

    public EspColor color() {
        return colorGetter == null ? EspColor.WHITE : colorGetter.get();
    }

    public void color(EspColor color) {
        if (colorSetter != null) colorSetter.accept(color);
    }
}
