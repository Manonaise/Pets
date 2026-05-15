package org.Manonaise.pets.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.Manonaise.pets.Pets;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * /petmenutoken
 *
 * Wordt bijvoorbeeld door ItemsAdder uitgevoerd.
 * Zet een korte token zodat /pet menu meteen daarna toegestaan is.
 */
public class PetMenuTokenCommand implements BasicCommand {

    private final Pets plugin;

    public PetMenuTokenCommand(Pets plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (!(sender instanceof Player p)) {
            return;
        }

        long until = System.currentTimeMillis() + 2000L;

        p.getPersistentDataContainer().set(
                Pets.key(Pets.KEY_MENU_ALLOW_UNTIL),
                PersistentDataType.LONG,
                until
        );
    }
}