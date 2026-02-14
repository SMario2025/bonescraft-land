package de.bonescraft.land;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public class ContainerOwnerStore {
    private final BonescraftLand plugin;
    private final File file;
    private YamlConfiguration cfg;

    public ContainerOwnerStore(BonescraftLand plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "container_owners.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException ignored) {}
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save container_owners.yml: " + e.getMessage());
        }
    }

    public static boolean isTrackableContainer(Block block) {
        if (block == null) return false;
        if (!(block.getState() instanceof Container)) return false;
        Material m = block.getType();
        return switch (m) {
            case CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX,
                    WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
                    YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX, GRAY_SHULKER_BOX,
                    LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX,
                    BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX,
                    HOPPER, DISPENSER, DROPPER, FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND
                    -> true;
            default -> true; // any Container
        };
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public Optional<UUID> getOwner(Location loc) {
        String s = cfg.getString(key(loc));
        if (s == null || s.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public void setOwner(Location loc, UUID uuid) {
        cfg.set(key(loc), uuid.toString());
    }

    public void remove(Location loc) {
        cfg.set(key(loc), null);
    }
}
