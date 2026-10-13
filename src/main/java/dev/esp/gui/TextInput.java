package dev.esp.gui;

import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A tiny single line text field.
 *
 * <p>Deliberately not a vanilla widget: the search box, the hex field and the friend name box all
 * need the same four lines of behaviour, and owning them keeps focus handling explicit and
 * consistent across the screen.
 */
public final class TextInput {
    private final Consumer<String> onChange;
    private Predicate<String> filter = value -> true;

    private String text = "";
    private boolean focused;
    private int maxLength = 64;

    public TextInput(Consumer<String> onChange) {
        this.onChange = onChange;
    }

    public TextInput filter(Predicate<String> filter) {
        this.filter = filter;
        return this;
    }

    public TextInput maxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        return this;
    }

    public String text() {
        return text;
    }

    public void text(String value) {
        String next = value == null ? "" : value;
        if (next.length() > maxLength) next = next.substring(0, maxLength);
        if (next.equals(text)) return;

        text = next;
        onChange.accept(text);
    }

    public boolean focused() {
        return focused;
    }

    public void focus(boolean value) {
        focused = value;
    }

    /** @return true when the input consumed the character */
    public boolean charTyped(CharInput input) {
        if (!focused) return true;

        int codePoint = input.codepoint();
        if (codePoint < 32 || codePoint == 127) return true;
        if (text.length() >= maxLength) return true;

        String next = text + input.asString();
        if (!filter.test(next)) return true;

        text = next;
        onChange.accept(text);

        return true;
    }

    public boolean keyPressed(KeyInput input) {
        if (!focused) return false;

        if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (!text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
                onChange.accept(text);
            }

            return true;
        }

        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            focus(false);
            return true;
        }

        return true;
    }
}
