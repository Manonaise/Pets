package org.Manonaise.pets.data;

import org.Manonaise.pets.Pets;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.*;

import java.util.Locale;

public enum PetType {
    WOLF, CAT, RABBIT, FOX, PARROT, MYTHIC;

    public Entity spawn(Location loc, Pet pet, Player owner) {

        // ✅ Mythic pet: spawn exact MythicMob ID
        if (this == MYTHIC) {
            var hook = Pets.getInstance().getMythicMobsHook();
            if (hook == null || !hook.isAvailable()) {
                throw new RuntimeException("MythicMobs is niet aanwezig maar MYTHIC pet werd gebruikt.");
            }
            Entity e = hook.spawnMythicMobOrThrow(pet.getMythicMobId(), loc);

            applyCommonFlags(e, pet, owner);
            return e;
        }

        // ✅ Vanilla pet
        Entity e;
        switch (this) {
            case WOLF -> {
                e = loc.getWorld().spawn(loc, Wolf.class, w -> {
                    w.setTamed(true);
                    if (owner != null) w.setOwner(owner);
                    w.setSitting(false);
                });
            }
            case CAT -> {
                e = loc.getWorld().spawn(loc, Cat.class, c -> {
                    c.setTamed(true);
                    if (owner != null) c.setOwner(owner);
                    c.setSitting(false);

                    String var = pet.getVariant();
                    if (var != null) {
                        try { c.setCatType(Cat.Type.valueOf(var.toUpperCase(Locale.ROOT))); }
                        catch (IllegalArgumentException ignored) {}
                    }
                });
            }
            case RABBIT -> e = loc.getWorld().spawn(loc, Rabbit.class);
            case FOX -> {
                e = loc.getWorld().spawn(loc, Fox.class);
                Player p = owner != null ? owner : Bukkit.getPlayer(pet.getOwner());
                if (p != null) {
                    try { ((Fox) e).setFirstTrustedPlayer(p); } catch (Throwable ignored) {}
                }
            }
            case PARROT -> {
                e = loc.getWorld().spawn(loc, Parrot.class, pa -> {
                    pa.setTamed(true);
                    if (owner != null) pa.setOwner(owner);
                });
            }
            default -> e = loc.getWorld().spawn(loc, ArmorStand.class);
        }

        applyCommonFlags(e, pet, owner);
        return e;
    }

    private void applyCommonFlags(Entity e, Pet pet, Player owner) {
        // baby/adult
        if (e instanceof Ageable a) {
            if (pet.isBaby()) a.setBaby();
            else a.setAdult();
        }

        // tamed owner (als het kan)
        if (owner != null && e instanceof Tameable t) {
            try {
                t.setOwner(owner);
                t.setTamed(true);
            } catch (Throwable ignored) {}
        }

        // We gebruiken onze eigen ArmorStand-naam, dus entity-nameplate uit
        try { e.setCustomNameVisible(false); } catch (Throwable ignored) {}

        // algemene flags
        try { e.setInvulnerable(true); } catch (Throwable ignored) {}
        try { e.setPersistent(false); } catch (Throwable ignored) {}
    }

    public static PetType from(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase(Locale.ROOT);

        // extra aliases
        if (t.equals("MM") || t.equals("MYTHICMOBS")) return MYTHIC;

        try { return PetType.valueOf(t); }
        catch (IllegalArgumentException e) { return null; }
    }
}
