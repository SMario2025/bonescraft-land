package de.bonescraft.land;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.time.YearMonth;

public class PlaytimeCommand implements CommandExecutor {
    private final PlaytimeTracker tracker;

    public PlaytimeCommand(PlaytimeTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        OfflinePlayer target = null;
        YearMonth month = YearMonth.now();

        if (args.length >= 1) {
            target = Bukkit.getOfflinePlayer(args[0]);
        } else {
            if (sender instanceof org.bukkit.entity.Player p) {
                target = p;
            }
        }

        if (target == null) {
            sender.sendMessage("§cUsage: /playtime [player] [yyyy-MM]");
            return true;
        }

        if (args.length >= 2) {
            try {
                month = YearMonth.parse(args[1]);
            } catch (Exception e) {
                sender.sendMessage("§cInvalid month format. Use yyyy-MM (e.g. 2026-02)");
                return true;
            }
        }

        long seconds = tracker.getPlaytimeSeconds(target.getUniqueId(), month);
        sender.sendMessage("§aPlaytime for §e" + target.getName() + " §ain §e" + month + "§a: §b" + tracker.formatDuration(seconds));
        return true;
    }
}
