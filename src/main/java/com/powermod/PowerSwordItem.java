package com.powermod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class PowerSwordItem extends SwordItem {
    public PowerSwordItem(ToolMaterial material, int damage, float speed, Settings settings) {
        super(material, damage, speed, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);

        ServerPlayerEntity player = (ServerPlayerEntity) user;
        if (player.isSneaking()) {
            // sneak + right click = switch skill
            PowerMod.setSelected(player, PowerMod.getSelected(player) + 1);
            int s = PowerMod.getSelected(player);
            player.sendMessage(Text.literal("Skill " + (s + 1) + ": " + Skills.NAMES[s]), true);
            PowerMod.syncMana(player);
        } else {
            Skills.cast(player, PowerMod.getSelected(player));
        }
        return TypedActionResult.success(stack);
    }
}
