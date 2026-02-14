package de.bonescraft.land;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Stores claimed chunks.
 * Data format: worldName -> chunkKey "x,z" -> ownerUUID + members[]
 */
public class ClaimManager {
    private final BonescraftLand plugin;
    private final File file;
    private YamlConfiguration cfg;

    public ClaimManager(BonescraftLand plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create claims.yml: " + e.getMessage());
            }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save claims.yml: " + e.getMessage());
        }
    }

    public int getMaxClaimSizeFor(org.bukkit.entity.Player player) {
        int max = plugin.getConfig().getInt("max-claim-size", 1);

        // Generic permission prefix support: bonescraft.claim.size.<number>
        String prefix = plugin.getConfig().getString("permissions.claim_size_prefix", "bonescraft.claim.size.");
        for (int i = 1; i <= 100; i++) {
            if (player.hasPermission(prefix + i)) {
                max = Math.max(max, i);
            }
        }
        return max;
    }

    public int getDefaultClaimSize() {
        return Math.max(1, plugin.getConfig().getInt("default-claim-size", 1));
    }

    public Claim getClaimAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        Chunk c = loc.getChunk();
        return getClaimAt(c.getWorld().getName(), c.getX(), c.getZ());
    }

    public Claim getClaimAt(String world, int chunkX, int chunkZ) {
        String key = chunkKey(chunkX, chunkZ);
        ConfigurationSection sec = cfg.getConfigurationSection(world);
        if (sec == null) return null;
        ConfigurationSection claimSec = sec.getConfigurationSection(key);
        if (claimSec == null) return null;
        String ownerStr = claimSec.getString("owner");
        if (ownerStr == null) return null;
        UUID owner;
        try {
            owner = UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
        List<String> memberStrs = claimSec.getStringList("members");
        Map<UUID, TrustLevel> members = new HashMap<>();
        for (String s : memberStrs) {
            if (s == null || s.isBlank()) continue;
            // Format: uuid:LEVEL  OR legacy: uuid
            String[] parts = s.split(":", 2);
            try {
                UUID id = UUID.fromString(parts[0].trim());
                TrustLevel lvl = parts.length == 2 ? TrustLevel.parse(parts[1]) : TrustLevel.BUILD;
                members.put(id, lvl);
            } catch (IllegalArgumentException ignored) {}
        }
        return new Claim(world, chunkX, chunkZ, owner, members);
    }

    public boolean isOwner(UUID player, Claim claim) {
        return claim != null && claim.owner().equals(player);
    }

    // Convenience overloads for commands working with the current chunk
    public boolean isOwner(Chunk chunk, UUID player) {
        Claim c = getClaimAt(chunk);
        return c != null && c.owner().equals(player);
    }

    public boolean trust(Chunk chunk, UUID member, TrustLevel level) {
        Claim c = getClaimAt(chunk);
        return addMember(c, member, level);
    }

    public boolean trust(Chunk chunk, UUID member) {
        return trust(chunk, member, TrustLevel.BUILD);
    }

    public boolean untrust(Chunk chunk, UUID member) {
        Claim c = getClaimAt(chunk);
        return removeMember(c, member);
    }

    public Set<UUID> getTrusted(Chunk chunk) {
        Claim c = getClaimAt(chunk);
        return c == null ? Collections.emptySet() : c.members().keySet();
    }

    public Map<UUID, TrustLevel> getTrustedWithLevels(Chunk chunk) {
        Claim c = getClaimAt(chunk);
        return c == null ? Collections.emptyMap() : c.members();
    }

    public TrustLevel getTrustLevel(UUID player, Claim claim) {
        if (claim == null) return null;
        if (claim.owner().equals(player)) return TrustLevel.CONTAINER_TAKE;
        return claim.members().get(player);
    }

    public boolean isTrusted(UUID player, Claim claim) {
        return getTrustLevel(player, claim) != null;
    }

    public boolean setClaim(String world, int chunkX, int chunkZ, UUID owner) {
        if (getClaimAt(world, chunkX, chunkZ) != null) return false;
        String key = chunkKey(chunkX, chunkZ);
        ConfigurationSection worldSec = cfg.getConfigurationSection(world);
        if (worldSec == null) worldSec = cfg.createSection(world);
        ConfigurationSection claimSec = worldSec.createSection(key);
        claimSec.set("owner", owner.toString());
        claimSec.set("members", new ArrayList<String>());
        save();
        return true;
    }

    public int claimSquare(World world, int centerChunkX, int centerChunkZ, int size, UUID owner) {
        if (world == null) return 0;
        int half = (size - 1) / 2;
        int startX = centerChunkX - half;
        int startZ = centerChunkZ - half;
        int claimed = 0;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int x = startX + dx;
                int z = startZ + dz;
                if (getClaimAt(world.getName(), x, z) == null) {
                    if (setClaim(world.getName(), x, z, owner)) claimed++;
                }
            }
        }
        return claimed;
    }

    public int unclaimSquare(World world, int centerChunkX, int centerChunkZ, int size, UUID owner) {
        if (world == null) return 0;
        int half = (size - 1) / 2;
        int startX = centerChunkX - half;
        int startZ = centerChunkZ - half;
        int removed = 0;
        ConfigurationSection worldSec = cfg.getConfigurationSection(world.getName());
        if (worldSec == null) return 0;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int x = startX + dx;
                int z = startZ + dz;
                String key = chunkKey(x, z);
                Claim claim = getClaimAt(world.getName(), x, z);
                if (claim != null && claim.owner().equals(owner)) {
                    worldSec.set(key, null);
                    removed++;
                }
            }
        }
        save();
        return removed;
    }

    public boolean addMember(Claim claim, UUID member, TrustLevel level) {
        if (claim == null) return false;
        String path = claim.world() + "." + chunkKey(claim.chunkX(), claim.chunkZ()) + ".members";
        List<String> members = cfg.getStringList(path);
        // remove old entry for same UUID (if any)
        members.removeIf(s -> s != null && s.startsWith(member.toString()));
        members.add(member.toString() + ":" + (level == null ? TrustLevel.BUILD.name() : level.name()));
        cfg.set(path, members);
        save();
        return true;
    }

    // Backwards-compatible helpers used by older commands
    public boolean trust(Claim claim, UUID member) {
        return addMember(claim, member, TrustLevel.BUILD);
    }

    public boolean trust(Claim claim, UUID member, TrustLevel level) {
        return addMember(claim, member, level);
    }

    public boolean untrust(Claim claim, UUID member) {
        return removeMember(claim, member);
    }

    public Map<UUID, TrustLevel> getTrustedWithLevels(Claim claim) {
        if (claim == null) return Collections.emptyMap();
        Claim fresh = getClaimAt(claim.world(), claim.chunkX(), claim.chunkZ());
        if (fresh == null) return Collections.emptyMap();
        return fresh.members();
    }

    public boolean removeMember(Claim claim, UUID member) {
        if (claim == null) return false;
        String path = claim.world() + "." + chunkKey(claim.chunkX(), claim.chunkZ()) + ".members";
        List<String> members = cfg.getStringList(path);
        boolean removed = members.removeIf(s -> s != null && s.startsWith(member.toString()));
        if (removed) {
            cfg.set(path, members);
            save();
        }
        return removed;
    }

    public List<Claim> getClaimsOf(UUID owner) {
        List<Claim> out = new ArrayList<>();
        for (String world : cfg.getKeys(false)) {
            ConfigurationSection worldSec = cfg.getConfigurationSection(world);
            if (worldSec == null) continue;
            for (String key : worldSec.getKeys(false)) {
                Claim c = null;
                try {
                    String[] parts = key.split(",");
                    int x = Integer.parseInt(parts[0]);
                    int z = Integer.parseInt(parts[1]);
                    c = getClaimAt(world, x, z);
                } catch (Exception ignored) {}
                if (c != null && c.owner().equals(owner)) out.add(c);
            }
        }
        return out;
    }

    public static String chunkKey(int x, int z) {
        return x + "," + z;
    }

    public record Claim(String world, int chunkX, int chunkZ, UUID owner, Map<UUID, TrustLevel> members) {
        public List<String> membersForSave() {
            return members.entrySet().stream()
                    .map(e -> e.getKey().toString() + ":" + e.getValue().name())
                    .collect(Collectors.toList());
        }
    }
}
