package org.Manonaise.pets.follow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.Manonaise.pets.util.AIGoalUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class ActivePet {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Pets plugin;
    private final Player owner;
    private final Pet pet;

    private Entity entity;
    private ArmorStand nameTag;

    private int nameTask = -1;
    private int mainTask = -1;

    private long lootStartMs = System.currentTimeMillis();

    private boolean gaveUp = false;
    private boolean following = false;
    private boolean manualSitting = false;

    private int lastShownLevel = -1;
    private String lastShownName = null;

    private int lastGrindLevel = -1;
    private double lastAppliedMiningSpeed = Double.NaN;

    private final Location tagLocCache = new Location(null, 0, 0, 0);

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

        if (entity instanceof Mob mob) {
            AIGoalUtils.stripWanderingGoals(mob);
        }

        entity.getPersistentDataContainer().set(
                Pets.key("pet-owner"),
                PersistentDataType.STRING,
                owner.getUniqueId().toString()
        );

        entity.getPersistentDataContainer().set(
                Pets.key("pet-id"),
                PersistentDataType.INTEGER,
                pet.getId()
        );

        applyNoDamageFlags(entity);

        try {
            entity.customName(Component.empty());
            entity.setCustomNameVisible(false);
        } catch (Throwable ignored) {
        }

        ensureNameTag(true);
        updateMiningSpeedBoost(true);

        nameTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (entity == null || !entity.isValid() || entity.isDead() || !owner.isOnline()) {
                remove();
                return;
            }

            applyNoDamageFlags(entity);
            ensureNameTag(false);

            if (nameTag != null && nameTag.isValid() && !nameTag.isDead()) {
                nameTag.teleport(computeNameTagLocation(entity));
            }

        }, 1L, 1L);

        mainTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (entity == null || !entity.isValid() || entity.isDead() || !owner.isOnline()) {
                remove();
                return;
            }

            if (pet.getLevel() != lastShownLevel || !safeEq(lastShownName, pet.getName())) {
                updateNameTagText();
            }

            updateMiningSpeedBoost(false);

            if (isInWater(entity.getLocation())) {
                long now = System.currentTimeMillis();
                long cooldown = plugin.getConfig().getInt("wash.cooldown-seconds", 60) * 1000L;

                if (now - pet.getLastWashed() >= cooldown) {
                    pet.setLastWashed(now);
                    plugin.getPetManager().awardWashXp(pet);
                }
            }

            followTick();

        }, 10L, 10L);
    }

    private void updateMiningSpeedBoost(boolean force) {
        int grindLevel = pet.getUpGrinden();

        double perLevel = plugin.getConfig().getDouble("grinden.mining-speed-per-level", 0.005);
        double cap = plugin.getConfig().getDouble("grinden.mining-speed-cap", 0.05);

        double amount = 0.0;

        if (grindLevel > 1) {
            amount = (grindLevel - 1) * perLevel;

            if (amount > cap) {
                amount = cap;
            }
        }

        if (!force && grindLevel == lastGrindLevel && Double.compare(amount, lastAppliedMiningSpeed) == 0) {
            return;
        }

        lastGrindLevel = grindLevel;
        lastAppliedMiningSpeed = amount;

        try {
            var hook = plugin.getAuraSkillsHook();

            if (hook != null) {
                hook.setMiningSpeedBonus(owner, pet, amount);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Kon mining speed boost niet toepassen: " + t.getMessage());
        }
    }

    private void applyNoDamageFlags(Entity e) {
        try {
            e.setInvulnerable(true);
        } catch (Throwable ignored) {
        }

        try {
            e.setSilent(true);
        } catch (Throwable ignored) {
        }

        if (e instanceof LivingEntity le) {
            try {
                le.setCanPickupItems(false);
            } catch (Throwable ignored) {
            }

            try {
                le.setRemoveWhenFarAway(false);
            } catch (Throwable ignored) {
            }

            try {
                le.setFireTicks(0);
            } catch (Throwable ignored) {
            }
        }

        if (e instanceof Damageable d) {
            try {
                d.setHealth(d.getMaxHealth());
            } catch (Throwable ignored) {
            }
        }

        if (e instanceof Ageable a) {
            try {
                if (pet.isBaby()) {
                    a.setBaby();
                } else {
                    a.setAdult();
                }
            } catch (Throwable ignored) {
            }
        }

        if (nameTag != null && nameTag.isValid() && !nameTag.isDead()) {
            try {
                nameTag.setInvulnerable(true);
            } catch (Throwable ignored) {
            }
        }
    }

    private void ensureNameTag(boolean forceText) {
        if (entity == null) return;

        if (nameTag == null || !nameTag.isValid() || nameTag.isDead()) {
            spawnNameTag();
            forceText = true;
        }

        if (forceText) {
            updateNameTagText();
        }
    }

    private void spawnNameTag() {
        try {
            Location at = computeNameTagLocation(entity);

            nameTag = entity.getWorld().spawn(at, ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setMarker(true);
                as.setGravity(false);
                as.setSmall(true);
                as.setArms(false);
                as.setBasePlate(false);
                as.setInvulnerable(true);
                as.setPersistent(false);
                as.setCollidable(false);

                as.customName(petNameComponent());
                as.setCustomNameVisible(true);
            });

        } catch (Throwable t) {
            nameTag = null;
        }
    }

    private void updateNameTagText() {
        if (nameTag == null || nameTag.isDead() || !nameTag.isValid()) {
            ensureNameTag(true);
            return;
        }

        nameTag.customName(petNameComponent());
        nameTag.setCustomNameVisible(true);

        lastShownLevel = pet.getLevel();
        lastShownName = pet.getName();
    }

    private Component petNameComponent() {
        Component nm = LEGACY.deserialize(pet.getName());
        return nm.append(Component.text(" §7(Lv." + pet.getLevel() + ")"));
    }

    private Location computeNameTagLocation(Entity e) {
        Location l = e.getLocation();

        double topY;

        try {
            topY = e.getBoundingBox().getMaxY();
        } catch (Throwable t) {
            topY = l.getY() + Math.max(1.0, e.getHeight());
        }

        double extra = plugin.getConfig().getDouble("nametag.extra-y", 0.08);

        tagLocCache.setWorld(l.getWorld());
        tagLocCache.setX(l.getX());
        tagLocCache.setY(topY + extra);
        tagLocCache.setZ(l.getZ());
        tagLocCache.setYaw(0f);
        tagLocCache.setPitch(0f);

        return tagLocCache;
    }

    private boolean safeEq(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        return a.equals(b);
    }

    public int elapsedMinutesSinceLoot() {
        return (int) ((System.currentTimeMillis() - lootStartMs) / 60000L);
    }

    public void resetLootTimer() {
        lootStartMs = System.currentTimeMillis();
    }

    private Location computeBaseSpawn(Location playerLoc) {
        Location l = playerLoc.clone();

        Vector dir = l.getDirection().setY(0);

        if (dir.lengthSquared() == 0) {
            dir = new Vector(1, 0, 0);
        } else {
            dir.normalize();
        }

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

            if (!isPassable(below)) {
                return grounded;
            }

            grounded.subtract(0, 1, 0);
        }

        return up;
    }

    private boolean isPassable(Block b) {
        Material t = b.getType();

        try {
            return b.isPassable();
        } catch (Throwable ignored) {
            return !t.isSolid();
        }
    }

    private void followTick() {
        if (manualSitting || isVanillaSitting()) {
            stopPathfindingIfMob();

            if (entity != null) {
                entity.setVelocity(new Vector(0, 0, 0));
            }

            following = false;
            gaveUp = false;

            return;
        }

        Location pl = owner.getLocation();
        Location el = entity.getLocation();

        double distanceSquared = pl.distanceSquared(el);

        final double keepDistance = 2.0;
        final int maxRange = plugin.getConfig().getInt("follow.max-range", 40);
        final int startRange = plugin.getConfig().getInt("follow.teleport-range", 15);

        final double keepSquared = keepDistance * keepDistance;
        final double startSquared = startRange * startRange;
        final double stopSquared = maxRange * maxRange;

        if (distanceSquared > stopSquared) {
            if (!gaveUp) {
                gaveUp = true;
                owner.sendMessage("§7" + pet.getName() + " is te ver weg en geeft het op. Gebruik §b/pet whistle§7.");
            }

            stopPathfindingIfMob();

            entity.setVelocity(new Vector(0, 0, 0));
            following = false;

            return;
        }

        if (!following) {
            if (distanceSquared <= startSquared) {
                following = true;
            } else {
                return;
            }
        }

        if (distanceSquared <= keepSquared) {
            stopPathfindingIfMob();
            entity.setVelocity(new Vector(0, 0, 0));
            return;
        }

        gaveUp = false;

        double speed = Math.max(
                0.1,
                (1.0 + (pet.getUpSnelheid() - 1) * 0.10) * pet.penaltyMultiplier()
        );

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

    private boolean isVanillaSitting() {
        if (entity instanceof Sittable s) {
            try {
                return s.isSitting();
            } catch (Throwable ignored) {
                return false;
            }
        }

        return false;
    }

    private void velocityFallback(Location playerLocation, Location entityLocation) {
        if (manualSitting) {
            entity.setVelocity(new Vector(0, 0, 0));
            return;
        }

        Vector dir = playerLocation.toVector()
                .subtract(entityLocation.toVector())
                .normalize()
                .multiply(0.25 + (pet.getUpSnelheid() - 1) * 0.05)
                .multiply(pet.penaltyMultiplier());

        entity.setVelocity(dir);

        if (entity instanceof Creature c) {
            c.setTarget(owner);
        }
    }

    private void stopPathfindingIfMob() {
        if (entity instanceof Mob mob) {
            try {
                mob.getPathfinder().stopPathfinding();
            } catch (Throwable ignored) {
            }

            try {
                mob.setTarget(null);
            } catch (Throwable ignored) {
            }
        }

        if (entity instanceof Creature c) {
            try {
                c.setTarget(null);
            } catch (Throwable ignored) {
            }
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
        if (entity == null || !entity.isValid() || entity.isDead()) return;

        Location rnd = owner.getLocation().clone().add(
                -1 + Math.random() * 2,
                0,
                -1 + Math.random() * 2
        );

        Location safe = findSafeSpawnNear(rnd);

        stopPathfindingIfMob();

        entity.getPersistentDataContainer().set(
                Pets.key("pet-whistle-allow-until"),
                PersistentDataType.LONG,
                System.currentTimeMillis() + 2000L
        );

        entity.teleport(safe);

        ensureNameTag(false);

        if (nameTag != null && nameTag.isValid() && !nameTag.isDead()) {
            nameTag.teleport(computeNameTagLocation(entity));
        }

        gaveUp = false;
        following = false;
    }

    public void setSitting(boolean sit) {
        this.manualSitting = sit;

        if (entity instanceof Sittable s) {
            try {
                s.setSitting(sit);
            } catch (Throwable ignored) {
            }
        }

        if (sit) {
            stopPathfindingIfMob();

            if (entity != null) {
                try {
                    entity.setVelocity(new Vector(0, 0, 0));
                } catch (Throwable ignored) {
                }
            }

            following = false;
            gaveUp = false;
        }
    }

    public boolean isSitting() {
        return manualSitting || isVanillaSitting();
    }

    public void remove() {
        if (nameTask != -1) {
            Bukkit.getScheduler().cancelTask(nameTask);
            nameTask = -1;
        }

        if (mainTask != -1) {
            Bukkit.getScheduler().cancelTask(mainTask);
            mainTask = -1;
        }

        try {
            var hook = plugin.getAuraSkillsHook();

            if (hook != null) {
                hook.removeMiningSpeedBonus(owner, pet);
            }
        } catch (Throwable ignored) {
        }

        if (nameTag != null) {
            try {
                nameTag.remove();
            } catch (Throwable ignored) {
            }

            nameTag = null;
        }

        if (entity != null) {
            try {
                entity.remove();
            } catch (Throwable ignored) {
            }

            entity = null;
        }
    }

    public Entity getEntity() {
        return entity;
    }
}