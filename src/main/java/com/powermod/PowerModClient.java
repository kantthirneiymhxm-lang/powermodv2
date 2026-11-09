package com.powermod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

public class PowerModClient implements ClientModInitializer {
    private static float mana = PowerMod.MAX_MANA;
    private static float shown = PowerMod.MAX_MANA;
    private static int selected = 0;
    private static int flash = 0;
    private static int flashMax = 24;

    private static final KeyBinding[] KEYS = new KeyBinding[4];
    private static final int[] KEY_CODES = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V};

    @Override
    public void onInitializeClient() {
        for (int i = 0; i < 4; i++) {
            KEYS[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.powermod.skill" + (i + 1), InputUtil.Type.KEYSYM, KEY_CODES[i], "key.category.powermod"));
        }

        ClientPlayNetworking.registerGlobalReceiver(PowerMod.MANA_SYNC, (client, handler, buf, sender) -> {
            float m = buf.readFloat();
            int s = buf.readInt();
            client.execute(() -> {
                mana = m;
                selected = s;
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(PowerMod.FX_FLASH, (client, handler, buf, sender) -> {
            int dur = buf.readInt();
            client.execute(() -> {
                flash = dur;
                flashMax = Math.max(1, dur);
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (flash > 0) flash--;
            shown += (mana - shown) * 0.15f;
            for (int i = 0; i < 4; i++) {
                while (KEYS[i].wasPressed()) {
                    if (c.player != null) {
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeInt(i);
                        ClientPlayNetworking.send(PowerMod.CAST, buf);
                    }
                }
            }
        });

        HudRenderCallback.EVENT.register(PowerModClient::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();

        // red screen overlay
        if (flash > 0) {
            float t = flash / (float) flashMax;
            int a = (int) (t * 0.5f * 255);
            ctx.fill(0, 0, w, h, (a << 24) | 0x00B00000);
        }

        int x = 10, y = h - 22, bw = 100, bh = 8;

        // skill slots
        for (int i = 0; i < 4; i++) {
            int bx = x + i * 26;
            int by = y - 34;
            boolean sel = i == selected;
            boolean afford = mana >= Skills.COST[i];
            ctx.fill(bx - 1, by - 1, bx + 23, by + 17, sel ? 0xFFFFC832 : 0xFF000000);
            ctx.fill(bx, by, bx + 22, by + 16, afford ? 0xFF4A0A0A : 0xFF1A1A1A);
            String label = (i + 1) + ":" + (int) Skills.COST[i];
            ctx.drawTextWithShadow(mc.textRenderer, label, bx + 2, by + 4, afford ? 0xFFFFFF : 0x777777);
        }
        ctx.drawTextWithShadow(mc.textRenderer, Skills.NAMES[selected], x + 4 * 26 + 2, y - 30, 0xFFC832);

        // power bar
        ctx.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xFF000000);
        ctx.fill(x, y, x + bw, y + bh, 0xFF2A0505);
        int fill = (int) (bw * Math.max(0f, Math.min(1f, shown / PowerMod.MAX_MANA)));
        boolean low = shown < 25f && (mc.player.age / 5) % 2 == 0;
        ctx.fill(x, y, x + fill, y + bh, low ? 0xFFFF6644 : 0xFFD01515);
        ctx.fill(x, y, x + fill, y + 2, 0x66FFFFFF);
        ctx.drawTextWithShadow(mc.textRenderer, "POWER " + (int) shown, x, y - 11, 0xFFFFFF);
    }
}
