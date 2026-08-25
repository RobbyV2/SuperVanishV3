package dev.philippcmd.supervanish.commands;

import dev.philippcmd.supervanish.library.VanishService;
import dev.philippcmd.supervanish.library.VanishTier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor {

    private final VanishService vanishService;

    public VanishCommand(VanishService vanishService) {
        this.vanishService = vanishService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        boolean vanished = this.vanishService.toggle(player, VanishTier.NORMAL);
        player.sendMessage(vanished ? "You are now invisible." : "You are now visible.");
        return true;
    }
}
