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
    private final ClassLoader auraCl;

    private final String traitKey;

    // cached reflection
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

    private final Constructor<?> traitModifierCtor4;
    private final Method setNonPersistentNoArgs; // AuraSkills 2.3.6: setNonPersistent()

    @SuppressWarnings("unchecked")
    public AuraSkillsHook(Pets plugin) {
        this.plugin = plugin;

        this.auraPlugin = Bukkit.getPluginManager().getPlugin("AuraSkills");
        if (auraPlugin == null) {
            // ✅ geen fallback
            throw new RuntimeException("AuraSkills is verplicht maar niet gevonden.");
        }

        this.auraCl = auraPlugin.getClass().getClassLoader();
        this.traitKey = plugin.getConfig().getString("auraskills.trait-key", "mining_speed");

        try {
            // Load AuraSkills API classes from AuraSkills classloader (niet van Pets!)
            apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi", true, auraCl);
            registryClass = Class.forName("dev.aurelium.auraskills.api.registry.GlobalRegistry", true, auraCl);
            namespacedIdClass = Class.forName("dev.aurelium.auraskills.api.registry.NamespacedId", true, auraCl);
            traitClass = Class.forName("dev.aurelium.auraskills.api.trait.Trait", true, auraCl);
            traitModifierClass = Class.forName("dev.aurelium.auraskills.api.trait.TraitModifier", true, auraCl);
            skillsUserClass = Class.forName("dev.aurelium.auraskills.api.user.SkillsUser", true, auraCl);
            operationEnumClass = Class.forName("dev.aurelium.auraskills.api.util.AuraSkillsModifier$Operation", true, auraCl);

            apiGetMethod = apiClass.getMethod("get");
            apiGetUserMethod = apiClass.getMethod("getUser", UUID.class);
            apiGetGlobalRegistryMethod = apiClass.getMethod("getGlobalRegistry");

            namespacedIdFromDefaultMethod = namespacedIdClass.getMethod("fromDefault", String.class);
            registryGetTraitMethod = registryClass.getMethod("getTrait", namespacedIdClass);

            userAddTraitModifierMethod = skillsUserClass.getMethod("addTraitModifier", traitModifierClass);
            userRemoveTraitModifierByNameMethod = skillsUserClass.getMethod("removeTraitModifier", String.class);

            // TraitModifier(String, Trait, double, Operation)
            traitModifierCtor4 = traitModifierClass.getConstructor(String.class, traitClass, double.class, operationEnumClass);

            // AuraSkillsModifier#setNonPersistent() (geen args in 2.3.6)
            Method tmp;
            try {
                tmp = traitModifierClass.getMethod("setNonPersistent");
            } catch (NoSuchMethodException ex) {
                tmp = null;
            }
            setNonPersistentNoArgs = tmp;

        } catch (Throwable t) {
            throw new RuntimeException(
                    "AuraSkills hook kon niet initialiseren. " +
                            "Je AuraSkills jar lijkt de API classes niet te exposen of is niet correct geladen.",
                    t
            );
        }
    }

    /**
     * amount = percentage (0.01 = 1%)
     */
    public void setMiningSpeedBonus(Player player, Pet pet, double amount) {
        try {
            Object api = apiGetMethod.invoke(null);
            Object user = apiGetUserMethod.invoke(api, player.getUniqueId());
            if (user == null) return;

            String modName = modifierName(pet);

            // altijd oude weg
            userRemoveTraitModifierByNameMethod.invoke(user, modName);

            if (amount <= 0.0) return;

            Object registry = apiGetGlobalRegistryMethod.invoke(api);
            Object nid = namespacedIdFromDefaultMethod.invoke(null, traitKey);

            Object trait = registryGetTraitMethod.invoke(registry, nid);
            if (trait == null) {
                throw new RuntimeException("AuraSkills trait '" + traitKey + "' bestaat niet. Pas auraskills.trait-key aan.");
            }

            // Operation.ADD_PERCENT
            Object addPercent = Enum.valueOf((Class<? extends Enum>) operationEnumClass, "ADD_PERCENT");

            Object mod = traitModifierCtor4.newInstance(modName, trait, amount, addPercent);

            // non persistent (indien aanwezig)
            if (setNonPersistentNoArgs != null) {
                try { setNonPersistentNoArgs.invoke(mod); } catch (Throwable ignored) {}
            }

            userAddTraitModifierMethod.invoke(user, mod);

        } catch (RuntimeException re) {
            throw re;
        } catch (Throwable t) {
            throw new RuntimeException("AuraSkills setMiningSpeedBonus faalde (reflection).", t);
        }
    }

    public void removeMiningSpeedBonus(Player player, Pet pet) {
        try {
            Object api = apiGetMethod.invoke(null);
            Object user = apiGetUserMethod.invoke(api, player.getUniqueId());
            if (user == null) return;

            userRemoveTraitModifierByNameMethod.invoke(user, modifierName(pet));
        } catch (Throwable ignored) {
        }
    }

    private String modifierName(Pet pet) {
        UUID id = UUID.nameUUIDFromBytes(("pets-grinden-" + pet.getOwner() + ":" + pet.getId())
                .getBytes(StandardCharsets.UTF_8));
        return "pets_grinden_" + id;
    }
}
