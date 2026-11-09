package com.powermod;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Skills {
    public static final String[] NAMES = {"Blood Wave", "Dash Slash", "Meteor Rain", "Crimson World"};
    public static final float[] COST = {20f, 25f, 40f, 70f};
    private static final int[] COOLDOWN = {10, 30, 80, 240};
    private static final int[] FLASH = {24, 12, 40, 80};

    private static final DustParticleEffect BLACK = new DustParticleEffect(new Vector3f(0.04f, 0.04f, 0.04f), 1.6f);
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.05f, 0.05f), 1.8f);
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0f, 0.65f, 0.1f), 0.8f);

    private static final Map<UUID, long[]> LAST = new HashMap<>();

    public static void cast(ServerPlayerEntity p, int id) {
        if (id < 0 || id > 3) return;
        ServerWorld sw = (ServerWorld) p.getWorld();
        long now = sw.getTime();
        long[] last = LAST.computeIfAbsent(p.getUuid(), k -> {
            long[] a = new long[4];
            Arrays.fill(a, -1000L);
            return a;
        });
        if (now - last[id] < COOLDOWN[id]) return;

        float mana = PowerMod.getMana(p);
        if (mana < COST[id]) {
            p.sendMessage(Text.literal("Not enough power!"), true);
            return;
        }
        PowerMod.setMana(p, mana - COST[id]);
        last[id] = now;

        switch (id) {
            case 0 -> bloodWave(p, sw);
            case 1 -> dashSlash(p, sw);
            case 2 -> meteorRain(p, sw);
            default -> crimsonWorld(p, sw);
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(FLASH[id]);
        ServerPlayNetworking.send(p, PowerMod.FX_FLASH, buf);
        PowerMod.syncMana(p);
    }

    // ---------- helpers ----------

    private static void burst(ServerWorld sw, Vec3d pos, double spread) {
        sw.spawnParticles(RED, pos.x, pos.y, pos.z, 8, spread, spread, spread, 0.01);
        sw.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 5, spread * 0.8, spread * 0.8, spread * 0.8, 0.06);
        sw.spawnParticles(GOLD, pos.x, pos.y, pos.z, 10, spread * 1.2, spread * 1.2, spread * 1.2, 0.15);
        sw.spawnParticles(BLACK, pos.x, pos.y, pos.z, 6, spread * 1.4, spread * 1.4, spread * 1.4, 0.08);
    }

    private static void collect(ServerWorld sw, ServerPlayerEntity p, Vec3d pos, double r, Set<LivingEntity> out) {
        out.addAll(sw.getEntitiesByClass(LivingEntity.class, new Box(pos, pos).expand(r), e -> e != p && e.isAlive()));
    }

    private static void hurt(ServerWorld sw, ServerPlayerEntity p, Set<LivingEntity> targets, float dmg, int fireSec) {
        for (LivingEntity e : targets) {
            e.damage(sw.getDamageSources().playerAttack(p), dmg);
            if (fireSec > 0) e.setOnFireFor(fireSec);
        }
    }

    // ---------- skill 1: Blood Wave ----------
    private static void bloodWave(ServerPlayerEntity p, ServerWorld sw) {
        Vec3d look = p.getRotationVec(1f).normalize();
        Vec3d start = p.getEyePos();
        Set<LivingEntity> hit = new HashSet<>();

        for (int a = 0; a < 360; a += 15) {
            double rad = Math.toRadians(a);
            sw.spawnParticles(RED, p.getX() + Math.cos(rad) * 2.2, p.getY() + 0.1, p.getZ() + Math.sin(rad) * 2.2, 1, 0, 0.05, 0, 0.02);
        }
        for (int i = 1; i <= 14; i++) {
            Vec3d pos = start.add(look.multiply(i * 1.2));
            burst(sw, pos, 0.5);
            collect(sw, p, pos, 1.6, hit);
        }
        hurt(sw, p, hit, 10f, 3);
        for (LivingEntity e : hit) e.takeKnockback(0.8, -look.x, -look.z);

        sw.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 0.8f, 1.3f);
    }

    // ---------- skill 2: Dash Slash ----------
    private static void dashSlash(ServerPlayerEntity p, ServerWorld sw) {
        Vec3d dir = p.getRotationVec(1f).normalize();
        Vec3d origin = p.getPos().add(0, 1, 0);
        Set<LivingEntity> hit = new HashSet<>();

        for (int i = 0; i <= 8; i++) {
            Vec3d pos = origin.add(dir.multiply(i));
            burst(sw, pos, 0.35);
            collect(sw, p, pos, 1.5, hit);
        }
        hurt(sw, p, hit, 12f, 3);

        p.setVelocity(dir.x * 2.4, Math.max(dir.y * 2.4, 0.1), dir.z * 2.4);
        p.velocityModified = true;
        p.fallDistance = 0f;
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));

        sw.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 0.7f);
    }

    // ---------- skill 3: Meteor Rain ----------
    private static void meteorRain(ServerPlayerEntity p, ServerWorld sw) {
        Random r = p.getRandom();
        Vec3d c = p.getPos();
        Set<LivingEntity> hit = new HashSet<>();

        for (int i = 0; i < 24; i++) {
            double x = c.x + (r.nextDouble() * 2 - 1) * 8;
            double z = c.z + (r.nextDouble() * 2 - 1) * 8;
            double y = c.y;
            for (int h = 0; h <= 10; h++) {
                sw.spawnParticles(ParticleTypes.FLAME, x, y + h, z, 1, 0.1, 0, 0.1, 0.01);
                if (h % 2 == 0) sw.spawnParticles(BLACK, x, y + h, z, 1, 0.15, 0, 0.15, 0.0);
            }
            Vec3d impact = new Vec3d(x, y + 0.5, z);
            sw.spawnParticles(ParticleTypes.EXPLOSION, x, y + 0.5, z, 1, 0, 0, 0, 0);
            sw.spawnParticles(RED, x, y + 0.5, z, 20, 0.8, 0.6, 0.8, 0.02);
            sw.spawnParticles(GOLD, x, y + 0.5, z, 25, 0.5, 0.5, 0.5, 0.25);
            sw.spawnParticles(BLACK, x, y + 0.5, z, 10, 0.8, 0.5, 0.8, 0.1);
            sw.spawnParticles(ParticleTypes.LAVA, x, y + 0.5, z, 5, 0.5, 0.3, 0.5, 0.0);
            collect(sw, p, impact, 2.0, hit);
        }
        hurt(sw, p, hit, 14f, 4);

        sw.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    // ---------- skill 4: Crimson World (ultimate) ----------
    private static void crimsonWorld(ServerPlayerEntity p, ServerWorld sw) {
        Vec3d c = p.getPos();
        Set<LivingEntity> hit = new HashSet<>();

        for (int ring = 2; ring <= 10; ring += 2) {
            int step = ring <= 4 ? 20 : 12;
            for (int a = 0; a < 360; a += step) {
                double rad = Math.toRadians(a);
                double x = c.x + Math.cos(rad) * ring;
                double z = c.z + Math.sin(rad) * ring;
                sw.spawnParticles(RED, x, c.y + 0.15, z, 1, 0, 0.05, 0, 0.02);
                if (a % (step * 2) == 0) sw.spawnParticles(BLACK, x, c.y + 0.3, z, 2, 0.2, 0.2, 0.2, 0.05);
            }
        }
        for (int a = 0; a < 360; a += 45) {
            double rad = Math.toRadians(a);
            double x = c.x + Math.cos(rad) * 10;
            double z = c.z + Math.sin(rad) * 10;
            for (int h = 0; h <= 8; h++) {
                sw.spawnParticles(GOLD, x, c.y + h, z, 3, 0.2, 0.2, 0.2, 0.15);
                sw.spawnParticles(ParticleTypes.FLAME, x, c.y + h, z, 1, 0.2, 0.1, 0.2, 0.02);
            }
        }
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(RED, c.x, c.y + 1, c.z, 80, 3, 1.5, 3, 0.05);
        sw.spawnParticles(BLACK, c.x, c.y + 1, c.z, 50, 4, 2, 4, 0.1);

        collect(sw, p, c.add(0, 1, 0), 10.0, hit);
        hurt(sw, p, hit, 24f, 5);
        for (LivingEntity e : hit) {
            e.setVelocity(e.getVelocity().add(0, 0.9, 0));
            e.velocityModified = true;
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 3));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0));
        }

        sw.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 0.5f);
        sw.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }
}
