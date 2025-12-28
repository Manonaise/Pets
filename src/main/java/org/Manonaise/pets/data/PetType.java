package org.Manonaise.pets.data;

import org.Manonaise.pets.Pets;
import org.bukkit.Location;
import org.bukkit.entity.*;

import java.util.Locale;

public enum PetType {

    WOLF(EntityType.WOLF),
    CAT(EntityType.CAT),

    MYTHIC(null);

    private final EntityType entityType;

    PetType(EntityType entityType) {
        this.entityType = entityType;
    }

    public static PetType from(String s){
        if (s == null) return null;
        try {
            return PetType.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e){
            return null;
        }
    }

    public Entity spawn(Location loc, Pet pet, Player owner) {
        if (this == MYTHIC) {
            var hook = Pets.getInstance().getMythicMobsHook();
            if (hook == null || !hook.isAvailable()) {
                throw new RuntimeException("MythicMobs is niet aanwezig maar MYTHIC pet werd gebruikt.");
            }

            // ✅ HIER is het belangrijk: pet.getMythicMobId() moet gezet zijn
            Entity e = hook.spawnMythicMobOrThrow(pet.getMythicMobId(), loc);

            if (e instanceof Ageable a) {
                if (pet.isBaby()) a.setBaby();
                else a.setAdult();
            }
            return e;
        }

        Entity e = loc.getWorld().spawnEntity(loc, entityType);

        if (e instanceof Tameable t) {
            t.setOwner(owner);
            t.setTamed(true);
        }
        if (e instanceof Ageable a) {
            if (pet.isBaby()) a.setBaby();
            else a.setAdult();
        }

        return e;
    }
}
