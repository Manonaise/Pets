package org.Manonaise.pets.data;

import org.Manonaise.pets.Pets;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.*;

import java.util.Locale;

public enum PetType {
    WOLF, CAT, RABBIT, FOX, PARROT;

    public Entity spawn(Location loc, Pet pet, Player owner){
        Entity e = null;

        // 1) Probeer eerst MythicMobs model
        e = trySpawnMythicMob(loc, pet, owner);

        // 2) fallback vanilla
        if (e == null) {
            switch (this){
                case WOLF -> {
                    Wolf w = loc.getWorld().spawn(loc, Wolf.class, wolf -> {
                        wolf.setTamed(true);
                        if (owner != null) wolf.setOwner(owner);
                        wolf.setSitting(false);
                    });
                    e = w;
                }
                case CAT -> {
                    Cat c = loc.getWorld().spawn(loc, Cat.class, cat -> {
                        cat.setTamed(true);
                        if (owner != null) cat.setOwner(owner);
                        String var = pet.getVariant();
                        if (var != null) {
                            try { cat.setCatType(Cat.Type.valueOf(var.toUpperCase(Locale.ROOT))); }
                            catch (IllegalArgumentException ignored) {}
                        }
                        cat.setSitting(false);
                    });
                    e = c;
                }
                case RABBIT -> e = loc.getWorld().spawn(loc, Rabbit.class);
                case FOX -> {
                    Fox fx = loc.getWorld().spawn(loc, Fox.class);
                    Player p = owner != null ? owner : Bukkit.getPlayer(pet.getOwner());
                    if (p != null) {
                        try { fx.setFirstTrustedPlayer(p); } catch (Throwable ignored) {}
                    }
                    e = fx;
                }
                case PARROT -> {
                    Parrot pa = loc.getWorld().spawn(loc, Parrot.class, parrot -> {
                        parrot.setTamed(true);
                        if (owner != null) parrot.setOwner(owner);
                    });
                    e = pa;
                }
                default -> e = loc.getWorld().spawn(loc, ArmorStand.class);
            }
        }

        // 3) algemene flags
        if(e instanceof Ageable a){
            if(pet.isBaby()) a.setBaby(); else a.setAdult();
        }
        e.setInvulnerable(true);
        e.setPersistent(false);
        e.setCustomNameVisible(true);
        return e;
    }

    private Entity trySpawnMythicMob(Location loc, Pet pet, Player owner){
        String id = pet.getMythicMobId();
        if (id == null || id.isBlank()) return null;

        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicInst = mythicBukkitClass.getMethod("inst").invoke(null);
            if (mythicInst == null) return null;

            Object mobManager = mythicBukkitClass.getMethod("getMobManager").invoke(mythicInst);
            if (mobManager == null) return null;

            Object optional = mobManager.getClass().getMethod("getMythicMob", String.class).invoke(mobManager, id);
            if (!(optional instanceof java.util.Optional<?> opt) || opt.isEmpty()) return null;
            Object mythicMob = opt.get();

            Class<?> bukkitAdapterClass = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter");
            Object mythicLoc = bukkitAdapterClass.getMethod("adapt", Location.class).invoke(null, loc);

            // ✅ robuust: zoek spawn-method met parameter die mythicLoc accepteert
            java.lang.reflect.Method spawnMethod = null;
            for (var m : mythicMob.getClass().getMethods()) {
                if (!m.getName().equals("spawn")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 2 && pts[1] == double.class && pts[0].isInstance(mythicLoc)) {
                    spawnMethod = m;
                    break;
                }
            }
            if (spawnMethod == null) return null;

            Object activeMob = spawnMethod.invoke(mythicMob, mythicLoc, 1.0d);
            if (activeMob == null) return null;

            Object mythicEntity = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
            if (mythicEntity == null) return null;

            Object bukkitEntity = mythicEntity.getClass().getMethod("getBukkitEntity").invoke(mythicEntity);
            if (bukkitEntity instanceof Entity ent) {
                if (ent instanceof Tameable tam && owner != null) tam.setOwner(owner);
                return ent;
            }
        } catch (ClassNotFoundException e) {
            return null; // Mythic niet aanwezig
        } catch (Throwable t){
            Pets.getInstance().getLogger().warning("[Pets] MythicMobs spawn failed for id '" + id + "': " + t.getMessage());
            return null;
        }
        return null;
    }

    public static PetType from(String s){
        if (s == null) return null;
        try { return PetType.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e){ return null; }
    }
}
