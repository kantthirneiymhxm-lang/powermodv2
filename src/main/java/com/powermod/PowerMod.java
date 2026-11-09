package com.powermod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ToolMaterials;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PowerMod implements ModInitializer {
    public static final String MOD_ID = "powermod";
    public static final Identifier MANA_SYNC = new Identifier(MOD_ID, "mana_sync");
    public static final Identifier FX_FLASH = new Identifier(MOD_ID, "fx_flash");
    public static final Identifier CAST = new Identifier(MOD_ID, "cast");
    public static final float MAX_MANA = 100f;
    public static final float REGEN_PER_TICK = 0.1f;

    private static final Map<UUID, Float> MANA = new HashMap<>();
    private static final Map<UUID, Integer> SELECTED = new HashMap<>();
    private static int tick = 0;

    public static final Item POWER_SWORD = new PowerSwordItem(ToolMaterials.NETHERITE, 4, -2.2f, new FabricItemSettings());

    public static float getMana(ServerPlayerEntity p) {
        return MANA.getOrDefault(p.getUuid(), MAX_MANA);
    }

    public static void setMana(ServerPlayerEntity p, float value) {
        MANA.put(p.getUuid(), Math.max(0f, Math.min(MAX_MANA, value)));
    }

    public static int getSelected(ServerPlayerEntity p) {
        return SELECTED.getOrDefault(p.getUuid(), 0);
    }

    public static void setSelected(ServerPlayerEntity p, int s) {
        SELECTED.put(p.getUuid(), ((s % 4) + 4) % 4);
    }

    public static void syncMana(ServerPlayerEntity p) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(getMana(p));
        buf.writeInt(getSelected(p));
        ServerPlayNetworking.send(p, MANA_SYNC, buf);
    }

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "power_sword"), POWER_SWORD);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> e.add(POWER_SWORD));

        // keybind casting from the client
        ServerPlayNetworking.registerGlobalReceiver(CAST, (server, player, handler, buf, responder) -> {
            int id = buf.readInt();
            server.execute(() -> {
                if (player.getMainHandStack().isOf(POWER_SWORD)) {
                    Skills.cast(player, id);
                }
            });
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                float m = getMana(p);
                if (m < MAX_MANA) setMana(p, m + REGEN_PER_TICK);
                if (tick % 4 == 0) syncMana(p);
            }
        });
    }
}
