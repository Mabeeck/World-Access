package mabeeck.worldaccess.mixin.client;

import mabeeck.worldaccess.WorldAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import org.lwjgl.glfw.GLFW;

import javax.swing.text.JTextComponent;

public class KeyListener {
    /*public static KeyBinding getKey = new KeyBinding("key.world-access.get", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.category.world-access");
    public static void registerKeyInputs() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (getKey.wasPressed()) {
                CustomPayload packet = CustomPayload;
                ClientPlayNetworking.send(packet);
            }
        });
    }*/
}
