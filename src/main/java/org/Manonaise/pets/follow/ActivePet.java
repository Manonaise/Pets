package org.Manonaise.pets.follow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class ActivePet {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Pets plugin;
    private final Player owner;
    private final Pet pet;

    private Entity entity;
    private ArmorStand nameTag;
    private int task = -1;

    private long lootStartMs = System.currentTimeMillis();
    private boolean gaveUp = false;
    private boolean following = false;

    private Location lastLoc;
    private double walkAccum = 0.0;

    private int lastShownLevel = -1;

    // AuraSkills mining boost tracking
    private int lastGrindLevel = -1;
    private double lastAppliedAmount = Double.NaN;

    public ActivePet(Pets plugin, Player owner, Pet pet) {
        this.plugin = plugin;
        this.owner = owner;
        this.pet = pet;
        spawn();
    }

    private void spawn() {
        Location base = computeBaseSpawn(owner.getLocation());
        Location safe = findSafeSpawnNear(base);

        entity = pet.getType().spawn(safe, pet, owner);

        entity.getPersistentDataContainer().set(Pets.key("pet-owner"), PersistentDataType.STRING, owner.getUniqueId().toString());
        entity.getPersistentDataContainer().set(Pets.key("pet-id"), PersistentDataType.INTEGER, pet.getId());

        // naam op entity (extra zekerheid)
        try {
            entity.customName(petNameComponent());
            entity.setCustomNameVisible(true);
        } catch (Throwable ignored) {}

        spawnNameTagPassenger();

        lastLoc = entity.getLocation();
        walkAccum = 0;

        // direct mining boost
        updateMiningBoost(true);

        task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (entity == null || entity.isDead() || !owner.isOnline()) {
                remove();
                return;
            }

            if (pet.getLevel() != lastShownLevel) {
                updateNameTagText();
            }

            updateMiningBoost(false);

            if (isInWater(entity.getLocation())) {
                long now = System.currentTimeMillis();
                long cd = plugin.getConfig().getInt("wash.cooldown-seconds", 60) * 1000L;
                if (now - pet.getLastWashed() >= cd) {
                    pet.setLastWashed(now);
                    plugin.getPetManager().awardWashXp(pet);
                }
            }

            followTick();

            Location cur = entity.getLocation();
            double moved = cur.distance(lastLoc);
            if (moved > 0 && moved < 6.0) {
                walkAccum += moved;
                if (walkAccum >= 1.0) {
                    int blocks = (int) Math.floor(walkAccum);
                    walkAccum -= blocks;
                    plugin.getPetManager().addWalkProgressForPet(pet, blocks);
                }
            }
            lastLoc = cur;

        }, 10L, 10L);
    }

    private void spawnNameTagPassenger() {
        if (nameTag != null && !nameTag.isDead()) nameTag.remove();
        nameTag = null;

        Location at = entity.getLocation().clone();

        nameTag = (ArmorStand) at.getWorld().spawn(at, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            as.setSmall(true);
            as.setBasePlate(false);
            as.setInvulnerable(true);
            as.setPersistent(false);
            as.setCollidable(false);
            as.customName(petNameComponent());
            as.setCustomNameVisible(true);
        });

        try { entity.addPassenger(nameTag); } catch (Throwable ignored) {}

        lastShownLevel = pet.getLevel();
    }

    private void updateNameTagText() {
        try {
            entity.customName(petNameComponent());
            entity.setCustomNameVisible(true);
        } catch (Throwable ignored) {}

        if (nameTag == null || nameTag.isDead()) {
            spawnNameTagPassenger();
            return;
        }
        nameTag.customName(petNameComponent());
        nameTag.setCustomNameVisible(true);
        lastShownLevel = pet.getLevel();
    }

    private Component petNameComponent() {
        Component nm = LEGACY.deserialize(pet.getName());
        return nm.append(Component.text(" §7(Lv." + pet.getLevel() + ")"));
    }

    private void updateMiningBoost(boolean force) {
        int g = pet.getUpGrinden();

        double perLevel = plugin.getConfig().getDouble("grinden.auraskills-per-level", 0.01); // 1% per level
        double cap = plugin.getConfig().getDouble("grinden.auraskills-cap", 0.10);            // max 10%

        double amount = 0.0;
        if (g > 1) {
            amount = (g - 1) * perLevel;
            if (amount > cap) amount = cap;
        }

        if (!force && g == lastGrindLevel && Double.compare(amount, lastAppliedAmount) == 0) return;

        lastGrindLevel = g;
        lastAppliedAmount = amount;

        // ✅ alleen via AuraSkills
        var hook = plugin.getAuraSkillsHook();
        if (hook != null) {
            hook.setMiningSpeedBonus(owner, pet, amount);
        }

    }

    public int elapsedMinutesSinceLoot() {
        return (int) ((System.currentTimeMillis() - lootStartMs) / 60000L);
    }

    public void resetLootTimer() {
        lootStartMs = System.currentTimeMillis();
    }

    private Location computeBaseSpawn(Location playerLoc) {
        Location l = playerLoc.clone();
        Vector dir = l.getDirection().setY(0).normalize();
        l.add(dir.multiply(1.5));
        return l;
    }

    private Location findSafeSpawnNear(Location base) {
        Location start = base.clone();
        start.setY(base.getY());

        Location up = start.clone();
        for (int dy = 0; dy <= 8; dy++) {
            Location c = start.clone().add(0, dy, 0);
            if (isPassable(c.getBlock()) && isPassable(c.clone().add(0, 1, 0).getBlock())) {
                up = c;
                break;
            }
        }

        Location grounded = up.clone();
        for (int dy = 0; dy <= 8; dy++) {
            Block below = grounded.clone().add(0, -1, 0).getBlock();
            if (!isPassable(below)) return grounded;
            grounded.subtract(0, 1, 0);
        }
        return up;
    }

    private boolean isPassable(Block b) {
        Material t = b.getType();
        try { return b.isPassable(); }
        catch (Throwable ignored) { return !t.isSolid(); }
    }

    private void followTick() {
        if (entity instanceof Sittable s && s.isSitting()) {
            stopPathfindingIfMob();
            entity.setVelocity(new Vector());
            following = false;
            return;
        }

        Location pl = owner.getLocation();
        Location el = entity.getLocation();
        double d2 = pl.distanceSquared(el);

        final double keepDistance = 2.0;
        final int maxRange = plugin.getConfig().getInt("follow.max-range", 40);
        final int startRange = plugin.getConfig().getInt("follow.teleport-range", 15);

        final double keepSq = keepDistance * keepDistance;
        final double startSq = startRange * startRange;
        final double stopSq = maxRange * maxRange;

        if (d2 > stopSq) {
            if (!gaveUp) {
                gaveUp = true;
                owner.sendMessage("§7" + pet.getName() + " is te ver weg en geeft het op. Gebruik §b/pet whistle§7.");
            }
            stopPathfindingIfMob();
            entity.setVelocity(new Vector());
            following = false;
            return;
        }

        if (!following) {
            if (d2 <= startSq) following = true;
            else return;
        }

        if (d2 <= keepSq) {
            stopPathfindingIfMob();
            entity.setVelocity(new Vector());
            return;
        }

        gaveUp = false;

        double speed = Math.max(0.1, (1.0 + (pet.getUpSnelheid() - 1) * 0.10) * pet.penaltyMultiplier());

        if (entity instanceof Mob mob) {
            try {
                mob.getPathfinder().moveTo(owner, speed);
            } catch (Throwable ignored) {
                velocityFallback(pl, el);
            }
        } else {
            velocityFallback(pl, el);
        }
    }

    private void velocityFallback(Location pl, Location el) {
        Vector dir = pl.toVector().subtract(el.toVector())
                .normalize()
                .multiply(0.25 + (pet.getUpSnelheid() - 1) * 0.05)
                .multiply(pet.penaltyMultiplier());
        entity.setVelocity(dir);
        if (entity instanceof Creature c) c.setTarget(owner);
    }

    private void stopPathfindingIfMob() {
        if (entity instanceof Mob mob) {
            try { mob.getPathfinder().stopPathfinding(); } catch (Throwable ignored) {}
        }
    }

    private boolean isInWater(Location loc) {
        Block b = loc.getBlock();
        Material t = b.getType();
        return t == Material.WATER
                || (Tag.ICE.isTagged(t) && b.getRelative(0, -1, 0).getType() == Material.WATER)
                || t == Material.BUBBLE_COLUMN;
    }

    public void whistleSummon() {
        Location rnd = owner.getLocation().clone().add(-1 + Math.random() * 2, 0, -1 + Math.random() * 2);
        Location safe = findSafeSpawnNear(rnd);

        stopPathfindingIfMob();

        entity.getPersistentDataContainer().set(
                Pets.key("pet-whistle-allow-until"),
                PersistentDataType.LONG,
                System.currentTimeMillis() + 2000L
        );

        entity.teleport(safe);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity != null && nameTag != null && !nameTag.isDead()) {
                try {
                    if (!entity.getPassengers().contains(nameTag)) entity.addPassenger(nameTag);
                } catch (Throwable ignored) {}
            }
        });

        gaveUp = false;
        following = false;
        lastLoc = entity.getLocation();
        walkAccum = 0;
    }

    public void setSitting(boolean sit) {
        if (entity instanceof Sittable s) s.setSitting(sit);
    }

    public void remove() {
        if (task != -1) {
            Bukkit.getScheduler().cancelTask(task);
            task = -1;
        }

        // ✅ AuraSkills bonus weg
        try {
            var hook = plugin.getAuraSkillsHook();
            if (hook != null) hook.removeMiningSpeedBonus(owner, pet);
        } catch (Throwable ignored) {}


        if (nameTag != null) {
            try { nameTag.remove(); } catch (Throwable ignored) {}
            nameTag = null;
        }

        if (entity != null) {
            entity.remove();
            entity = null;
        }
    }

    public Entity getEntity() { return entity; }
}
