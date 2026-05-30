package org.Manonaise.pets.integrations;

import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class AuraSkillsHook {

    private final Pets plugin;

    private final Plugin auraPlugin;
    private final ClassLoader auraClassLoader;

    private final String traitKey;

    private final Class<?> apiClass;
    private final Class<?> registryClass;
    private final Class<?> namespacedIdClass;
    private final Class<?> traitClass;
    private final Class<?> traitModifierClass;
    private final Class<?> skillsUserClass;
    private final Class<?> operationEnumClass;

    private final Method apiGetMethod;
    private final Method apiGetUserMethod;
    private final Method apiGetGlobalRegistryMethod;

    private final Method namespacedIdFromDefaultMethod;
    private final Method registryGetTraitMethod;

    private final Method userAddTraitModifierMethod;
    private final Method userRemoveTraitModifierByNameMethod;

    private final Constructor<?> traitModifierConstructor;
    private final Method setNonPersistentMethod;

    @SuppressWarnings("unchecked")
    public AuraSkillsHook(Pets plugin) {
        this.plugin = plugin;

        this.auraPlugin = Bukkit.getPluginManager().getPlugin("AuraSkills");

        if (auraPlugin == null || !auraPlugin.isEnabled()) {
            throw new RuntimeException("AuraSkills is verplicht maar niet gevonden.");
        }

        this.auraClassLoader = auraPlugin.getClass().getClassLoader();
        this.traitKey = plugin.getConfig().getString("auraskills.trait-key", "mining_speed");

        try {
            apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi", true, auraClassLoader);
            registryClass = Class.forName("dev.aurelium.auraskills.api.registry.GlobalRegistry", true, auraClassLoader);
            namespacedIdClass = Class.forName("dev.aurelium.auraskills.api.registry.NamespacedId", true, auraClassLoader);
            traitClass = Class.forName("dev.aurelium.auraskills.api.trait.Trait", true, auraClassLoader);
            traitModifierClass = Class.forName("dev.aurelium.auraskills.api.trait.TraitModifier", true, auraClassLoader);
            skillsUserClass = Class.forName("dev.aurelium.auraskills.api.user.SkillsUser", true, auraClassLoader);
            operationEnumClass = Class.forName("dev.aurelium.auraskills.api.util.AuraSkillsModifier$Operation", true, auraClassLoader);

            apiGetMethod = apiClass.getMethod("get");
            apiGetUserMethod = apiClass.getMethod("getUser", UUID.class);
            apiGetGlobalRegistryMethod = apiClass.getMethod("getGlobalRegistry");

            namespacedIdFromDefaultMethod = namespacedIdClass.getMethod("fromDefault", String.class);
            registryGetTraitMethod = registryClass.getMethod("getTrait", namespacedIdClass);

            userAddTraitModifierMethod = skillsUserClass.getMethod("addTraitModifier", traitModifierClass);
            userRemoveTraitModifierByNameMethod = skillsUserClass.getMethod("removeTraitModifier", String.class);

            traitModifierConstructor = traitModifierClass.getConstructor(
                    String.class,
                    traitClass,
                    double.class,
                    operationEnumClass
            );

            Method tmp;

            try {
                tmp = traitModifierClass.getMethod("setNonPersistent");
            } catch (NoSuchMethodException ignored) {
                tmp = null;
            }

            setNonPersistentMethod = tmp;

        } catch (Throwable t) {
            throw new RuntimeException("AuraSkills hook kon niet initialiseren.", t);
        }
    }

    public boolean isAvailable() {
        return auraPlugin != null && auraPlugin.isEnabled();
    }

    /**
     * amount is percentage als decimal.
     *
     * Voorbeelden:
     * 0.005 = +0.5%
     * 0.01  = +1%
     * 0.05  = +5%
     */
    @SuppressWarnings("unchecked")
    public void setMiningSpeedBonus(Player player, Pet pet, double amount) {
        if (player == null || !player.isOnline()) return;
        if (pet == null) return;
        if (!isAvailable()) return;

        try {
            Object api = apiGetMethod.invoke(null);
            Object user = apiGetUserMethod.invoke(api, player.getUniqueId());

            if (user == null) return;

            String modifierName = modifierName(pet);

            // Altijd oude modifier eerst verwijderen, zodat upgrades meteen goed werken.
            userRemoveTraitModifierByNameMethod.invoke(user, modifierName);

            if (amount <= 0.0) {
                return;
            }

            Object registry = apiGetGlobalRegistryMethod.invoke(api);
            Object namespacedId = namespacedIdFromDefaultMethod.invoke(null, traitKey);
            Object trait = registryGetTraitMethod.invoke(registry, namespacedId);

            if (trait == null) {
                throw new RuntimeException("AuraSkills trait bestaat niet: " + traitKey);
            }

            Object operation = Enum.valueOf(
                    (Class<? extends Enum>) operationEnumClass,
                    "ADD_PERCENT"
            );

            Object modifier = traitModifierConstructor.newInstance(
                    modifierName,
                    trait,
                    amount,
                    operation
            );

            if (setNonPersistentMethod != null) {
                try {
                    setNonPersistentMethod.invoke(modifier);
                } catch (Throwable ignored) {
                }
            }

            userAddTraitModifierMethod.invoke(user, modifier);

        } catch (Throwable t) {
            throw new RuntimeException("AuraSkills mining speed modifier kon niet gezet worden.", t);
        }
    }

    public void removeMiningSpeedBonus(Player player, Pet pet) {
        if (player == null) return;
        if (pet == null) return;
        if (!isAvailable()) return;

        try {
            Object api = apiGetMethod.invoke(null);
            Object user = apiGetUserMethod.invoke(api, player.getUniqueId());

            if (user == null) return;

            userRemoveTraitModifierByNameMethod.invoke(user, modifierName(pet));

        } catch (Throwable ignored) {
        }
    }

    public void removeAllMiningSpeedBonuses(Player player) {
        if (player == null) return;

        for (Pet pet : plugin.getPetManager().getPets(player.getUniqueId())) {
            removeMiningSpeedBonus(player, pet);
        }
    }

    /**
     * Oude methodes blijven bestaan zodat andere code niet crasht.
     * Ze verwijzen nu gewoon naar mining speed.
     */
    public void setGrindingXpBonus(Player player, Pet pet, double percent) {
        double amount = percent / 100.0;
        setMiningSpeedBonus(player, pet, amount);
    }

    public void removeGrindingXpBonus(Player player, Pet pet) {
        removeMiningSpeedBonus(player, pet);
    }

    public void removeAllGrindingXpBonuses(Player player) {
        removeAllMiningSpeedBonuses(player);
    }

    private String modifierName(Pet pet) {
        UUID id = UUID.nameUUIDFromBytes(
                ("pets-mining-speed-" + pet.getOwner() + ":" + pet.getId())
                        .getBytes(StandardCharsets.UTF_8)
        );

        return "pets_mining_speed_" + id;
    }
}