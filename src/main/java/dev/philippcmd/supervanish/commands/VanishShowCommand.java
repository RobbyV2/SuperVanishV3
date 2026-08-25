package dev.philippcmd.supervanish.commands;

import dev.philippcmd.supervanish.library.VanishService;
import dev.philippcmd.supervanish.library.VanishTier;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishShowCommand implements CommandExecutor {

    private final VanishService vanishService;

    public VanishShowCommand(VanishService vanishService) {
        this.vanishService = vanishService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("Usage: /vanish-show <player|--all>");
            return true;
        }

        String targetName = args[0];

        if (targetName.equalsIgnoreCase("--all")) {
            int count = 0;
            for (Player vanished : this.vanishService.onlineVanished()) {
                if (this.vanishService.addViewer(vanished, player)) {
                    count++;
                }
            }
            player.sendMessage("You can now see " + count + " vanished players.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage("Player not found: " + targetName);
            return true;
        }

        if (!this.vanishService.isVanished(target)) {
            player.sendMessage("Player " + targetName + " is not vanished.");
            return true;
        }

        if (this.vanishService.tier(target.getUniqueId()) == VanishTier.SILENT) {
            player.sendMessage("Player " + targetName + " is in SuperVanish mode and cannot be revealed.");
            return true;
        }

        this.vanishService.addViewer(target, player);
        player.sendMessage("You can now see " + target.getName() + ".");
        return true;
    }
}
