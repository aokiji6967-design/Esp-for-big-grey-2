package dev.esp;

import dev.esp.gui.EspScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Client entrypoint declared in {@code fabric.mod.json}. */
public final class EspMod implements ClientModInitializer {
    public static final String MOD_ID = "esp";
    public static final String KEY_OPEN = "key.esp.open";

    @Override
    public void onInitializeClient() {
        EspClient client = EspClient.create();

        KeyBinding openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_OPEN,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            while (openKey.wasPressed()) {
                minecraft.setScreen(new EspScreen(client));
            }

            client.tick(minecraft);
        });
    }
}
