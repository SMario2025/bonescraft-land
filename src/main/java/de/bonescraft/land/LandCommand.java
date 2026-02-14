package de.bonescraft.land;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class LandCommand implements CommandExecutor {
    private final BonescraftLand plugin;
    private final ClaimManager claimManager;

    public LandCommand(BonescraftLand plugin, ClaimManager claimManager) {
        this.plugin = plugin;
        this.claimManager = claimManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "claim" -> {
                int size = plugin.getConfig().getInt("default-claim-size", 1);
                if (args.length >= 2) {
                    try {
                        size = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                        player.sendMessage("§cUsage: /land claim [1|2|3]");
                        return true;
                    }
                }
                int allowedMax = claimManager.getMaxClaimSizeFor(player);
                if (size < 1) size = 1;
                if (size > allowedMax) {
                    player.sendMessage("§cYou can't claim size " + size + ". Max for you: " + allowedMax);
                    return true;
                }

                Chunk origin = player.getLocation().getChunk();
                if (claimManager.isAnyClaimInArea(origin, size)) {
                    player.sendMessage("§cThis area overlaps an existing claim.");
                    return true;
                }
                claimManager.claimArea(player.getUniqueId(), origin, size);
                player.sendMessage("§aClaimed " + size + "x" + size + " chunks at your current chunk.");
                return true;
            }
            case "unclaim" -> {
                Chunk c = player.getLocation().getChunk();
                if (!claimManager.isOwner(c, player.getUniqueId()) && !player.hasPermission("bonescraft.bypass")) {
                    player.sendMessage("§cYou don't own this claim.");
                    return true;
                }
                int removed = claimManager.unclaimAt(c, player.getUniqueId(), player.hasPermission("bonescraft.bypass"));
                if (removed <= 0) player.sendMessage("§cNo claim found here.");
                else player.sendMessage("§aUnclaimed " + removed + " chunk(s).");
                return true;
            }
            case "info" -> {
                Chunk c = player.getLocation().getChunk();
                var owner = claimManager.getOwner(c);
                if (owner.isEmpty()) {
                    player.sendMessage("§7No claim here.");
                } else {
                    UUID uuid = owner.get();
                    String name = Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName()).orElse(uuid.toString());
                    int size = claimManager.getClaimSize(c);
                    player.sendMessage("§aClaim owner: §f" + name + " §7(" + uuid + ")");
                    player.sendMessage("§aClaim size: §f" + size + "x" + size + " chunks");
                    player.sendMessage("§aTrusted: §f" + claimManager.getTrustedWithLevels(c).size());
                }
                return true;
            }
            case "add" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /land add <player> [build|break|container_view|container_take]");
                    return true;
                }
                Chunk c = player.getLocation().getChunk();
                if (!claimManager.isOwner(c, player.getUniqueId()) && !player.hasPermission("bonescraft.bypass")) {
                    player.sendMessage("§cYou don't own this claim.");
                    return true;
                }
                UUID target = claimManager.resolveUUID(args[1]);
                if (target == null) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                TrustLevel level = TrustLevel.BUILD;
                if (args.length >= 3) level = TrustLevel.parse(args[2]);
                claimManager.trust(c, target, level);
                player.sendMessage("§aTrusted " + args[1] + " (" + level.name() + ") in this claim.");
                return true;
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /land remove <player>");
                    return true;
                }
                Chunk c = player.getLocation().getChunk();
                if (!claimManager.isOwner(c, player.getUniqueId()) && !player.hasPermission("bonescraft.bypass")) {
                    player.sendMessage("§cYou don't own this claim.");
                    return true;
                }
                UUID target = claimManager.resolveUUID(args[1]);
                if (target == null) {
                    player.sendMessage("§cPlayer not found.");
                    return true;
                }
                claimManager.untrust(c, target);
                player.sendMessage("§aRemoved trust for " + args[1] + " in this claim.");
                return true;
            }
            case "list" -> {
                var list = claimManager.listClaims(player.getUniqueId());
                player.sendMessage("§aYour claims: §f" + list.size());
                for (String s : list) player.sendMessage("§7- §f" + s);
                return true;
            }
            default -> {
                sendHelp(player);
                return true;
            }
        }
    }

    private void sendHelp(Player p) {
        p.sendMessage("§a/land claim [size] §7- Claim current chunk (size 1-3)");
        p.sendMessage("§a/land unclaim §7- Unclaim current claim");
        p.sendMessage("§a/land info §7- Show claim info");
        p.sendMessage("§a/land add <player> [build|break|container_view|container_take] §7- Trust player in this claim");
        p.sendMessage("§a/land remove <player> §7- Untrust player");
        p.sendMessage("§a/land list §7- List your claims");
    }
}
