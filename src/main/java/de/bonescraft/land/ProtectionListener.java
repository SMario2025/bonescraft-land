package de.bonescraft.land;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Protection rules:
 * - Wilderness is protected (no build/break/containers) unless bypass/build_anywhere
 * - Claims: only owner + trusted players can interact
 * - Trusted level defines WHAT is allowed on the claim
 * - Global "Ring" permissions define WHAT the rank is allowed to do
 */
public class ProtectionListener implements Listener {

    private final BonescraftLand plugin;

    public ProtectionListener(BonescraftLand plugin) {
        this.plugin = plugin;
    }

    private ClaimManager claims() {
        return plugin.getClaimManager();
    }

    private boolean hasPerm(Player p, String key, String def) {
        return p.hasPermission(plugin.getConfig().getString(key, def));
    }

    private boolean isBypass(Player p) {
        return p.isOp() || hasPerm(p, "permissions.bypass", "bonescraft.bypass");
    }

    private boolean canBuildAnywhere(Player p) {
        return hasPerm(p, "permissions.build_anywhere", "bonescraft.build.anywhere");
    }

    private TrustLevel trust(Player p, Location loc) {
        ClaimManager.Claim claim = claims().getClaimAt(loc);
        if (claim == null) return null;
        return claims().getTrustLevel(p.getUniqueId(), claim);
    }

    private boolean canPlace(Player p, Location loc) {
        if (isBypass(p) || canBuildAnywhere(p)) return true;
        TrustLevel t = trust(p, loc);
        if (t == null) return false;
        return t.allowsBuild() && hasPerm(p, "permissions.ring_build", "bonescraft.ring.build");
    }

    private boolean canBreak(Player p, Location loc) {
        if (isBypass(p) || canBuildAnywhere(p)) return true;
        TrustLevel t = trust(p, loc);
        if (t == null) return false;
        return t.allowsBreak() && hasPerm(p, "permissions.ring_break", "bonescraft.ring.break");
    }

    private Location resolveContainerLocation(Inventory inv) {
        if (inv == null) return null;
        if (inv.getHolder() instanceof Container c) return c.getLocation();
        if (inv.getHolder() instanceof DoubleChest dc) return dc.getLocation();
        return null;
    }

    private boolean canOpenContainer(Player p, Location containerLoc) {
        if (isBypass(p) || canBuildAnywhere(p)) return true;

        // Wilderness protected
        if (claims().getClaimAt(containerLoc) == null) return false;

        UUID placedBy = plugin.getContainerOwnerStore().getOwner(containerLoc);
        boolean isOwn = placedBy != null && placedBy.equals(p.getUniqueId());
        if (isOwn) return true; // can always open own containers

        TrustLevel t = trust(p, containerLoc);
        if (t == null || !t.allowsContainerView()) return false;
        return hasPerm(p, "permissions.ring_chest_view", "bonescraft.ring.chest.view");
    }

    private boolean canTakeFromContainer(Player p, Location containerLoc) {
        if (isBypass(p) || canBuildAnywhere(p)) return true;

        UUID placedBy = plugin.getContainerOwnerStore().getOwner(containerLoc);
        boolean isOwn = placedBy != null && placedBy.equals(p.getUniqueId());
        if (isOwn) return true;

        TrustLevel t = trust(p, containerLoc);
        if (t == null || !t.allowsContainerTake()) return false;
        return hasPerm(p, "permissions.ring_chest_take", "bonescraft.ring.chest.take");
    }

    private boolean isContainer(Block b) {
        return b != null && b.getState() instanceof Container;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();

        if (!canPlace(p, b.getLocation())) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Du darfst hier nicht bauen.");
            return;
        }

        if (isContainer(b)) {
            plugin.getContainerOwnerStore().setOwner(b.getLocation(), p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();

        if (!canBreak(p, b.getLocation())) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Du darfst hier nicht abbauen.");
            return;
        }

        if (isContainer(b)) {
            plugin.getContainerOwnerStore().removeOwner(b.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        Block clicked = e.getClickedBlock();

        // Containers (chest, barrel, shulker, etc.)
        if (clicked.getState() instanceof Container container) {
            Location loc = container.getLocation();
            if (!canOpenContainer(p, loc)) {
                e.setCancelled(true);
                p.sendMessage(ChatColor.RED + "Du darfst diese Truhe nicht öffnen.");
                return;
            }
            // set owner on first interaction (for legacy chests)
            if (plugin.getContainerOwnerStore().getOwner(loc) == null) {
                plugin.getContainerOwnerStore().setOwner(loc, p.getUniqueId());
            }
            return;
        }

        // Protect interaction in wilderness and in claims (doors/buttons/etc.)
        if (clicked.getType() != Material.AIR) {
            if (!(isBypass(p) || canBuildAnywhere(p))) {
                if (claims().getClaimAt(clicked.getLocation()) == null) {
                    e.setCancelled(true);
                    p.sendMessage(ChatColor.RED + "Geschützt (Wilderness). ");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Location loc = resolveContainerLocation(top);
        if (loc == null) return;

        if (!canOpenContainer(p, loc)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Du darfst diese Truhe nicht öffnen.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Location loc = resolveContainerLocation(top);
        if (loc == null) return;

        if (canTakeFromContainer(p, loc)) return;

        // Allow putting items INTO the container (clicking bottom inventory)
        boolean clickedTop = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
        boolean moveToOther = e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY;
        boolean hotbarSwap = e.getAction() == InventoryAction.HOTBAR_SWAP || e.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD;
        boolean collect = e.getAction() == InventoryAction.COLLECT_TO_CURSOR;

        if (clickedTop || moveToOther || hotbarSwap || collect) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Du darfst nichts aus dieser Truhe nehmen.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Location loc = resolveContainerLocation(top);
        if (loc == null) return;
        if (canTakeFromContainer(p, loc)) return;

        int topSize = top.getSize();
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot < topSize) {
                e.setCancelled(true);
                p.sendMessage(ChatColor.RED + "Du darfst nichts aus dieser Truhe nehmen.");
                return;
            }
        }
    }

    // Prevent pistons moving blocks across claim boundaries
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().isEmpty()) return;
        for (Block b : e.getBlocks()) {
            Location from = b.getLocation();
            Location to = from.clone().add(e.getDirection().getModX(), e.getDirection().getModY(), e.getDirection().getModZ());
            ClaimManager.Claim c1 = claims().getClaimAt(from);
            ClaimManager.Claim c2 = claims().getClaimAt(to);
            if ((c1 == null && c2 != null) || (c1 != null && c2 == null) || (c1 != null && c2 != null && !c1.owner().equals(c2.owner()))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!e.isSticky()) return;
        if (e.getBlocks().isEmpty()) return;
        for (Block b : e.getBlocks()) {
            Location from = b.getLocation();
            Location to = from.clone().subtract(e.getDirection().getModX(), e.getDirection().getModY(), e.getDirection().getModZ());
            ClaimManager.Claim c1 = claims().getClaimAt(from);
            ClaimManager.Claim c2 = claims().getClaimAt(to);
            if ((c1 == null && c2 != null) || (c1 != null && c2 == null) || (c1 != null && c2 != null && !c1.owner().equals(c2.owner()))) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
