package org.Manonaise.pets.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;

/**
 * Verwijdert alléén "wandelende"/"rondloop" goals wanneer de Paper MobGoals API aanwezig is.
 * We laten de rest staan, zodat pathfinding (moveTo) blijft werken.
 */
public final class AIGoalUtils {
    private AIGoalUtils(){}

    public static void stripWanderingGoals(Mob mob){
        try {
            var mg = Bukkit.getMobGoals();
            // getAllGoals(mob) geeft een collection van WrappedGoals met getKey()
            for (var wrapped : mg.getAllGoals(mob)) {
                String key = wrapped.getKey().toString().toLowerCase();

                // Filter bekende "rondloop" en ongewilde kattengedragingen
                if (key.contains("random_stroll") ||
                        key.contains("stroll") ||
                        key.contains("wander") ||
                        key.contains("tempt") ||
                        key.contains("look_randomly") ||
                        key.contains("jump_on_block") ||
                        key.contains("relax") ||
                        key.contains("sleep") ||
                        key.contains("sit")) {
                    mg.removeGoal(mob, wrapped);
                }
            }
        } catch (Throwable ignored) {
            // Geen Paper MobGoals API of andere server: gewoon niets doen
        }
    }
}