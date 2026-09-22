package net.tfminecraft.vfbuilders.managers;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.tfminecraft.vfbuilders.VFBuilders;

public class CommandManager implements CommandExecutor, TabCompleter {

    public String cmd1 = "vfbuilders";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§cUsage: /vfbuilders reload");
            return true;
        }
        if (!canReload(sender)) {
            sender.sendMessage("§cYou do not have permission to reload VFBuilders.");
            return true;
        }
        sender.sendMessage("§aReloading VFBuilders...");
        VFBuilders.plugin.reload();
        sender.sendMessage("§aReload complete.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && canReload(sender) && "reload".startsWith(args[0].toLowerCase())) {
            completions.add("reload");
        }
        return completions;
    }

    private static boolean canReload(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true;
        }
        Player player = (Player) sender;
        return player.isOp() || player.hasPermission("vfbuilders.reload");
    }
}
