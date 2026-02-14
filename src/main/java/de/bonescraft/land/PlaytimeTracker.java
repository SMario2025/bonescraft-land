package de.bonescraft.land;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlaytimeTracker {
    private final BonescraftLand plugin;
    private final File file;
    private final YamlConfiguration yml;

    private final Map<UUID, Long> sessionStart = new HashMap<>();

    public PlaytimeTracker(BonescraftLand plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playtime.yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    public void markJoin(UUID uuid) {
        sessionStart.put(uuid, System.currentTimeMillis());
    }

    public void markQuit(UUID uuid) {
        Long start = sessionStart.remove(uuid);
        if (start == null) return;
        long delta = System.currentTimeMillis() - start;
        addPlaytime(uuid, YearMonth.now().toString(), delta);
    }

    public void flushAllOnlineSessions() {
        for (UUID uuid : sessionStart.keySet().toArray(new UUID[0])) {
            markQuit(uuid);
            markJoin(uuid);
        }
        save();
    }

    private void addPlaytime(UUID uuid, String ym, long ms) {
        String path = uuid.toString() + "." + ym;
        long current = yml.getLong(path, 0L);
        yml.set(path, current + ms);
        save();
    }

    public long getPlaytimeMs(OfflinePlayer p, String ym) {
        return yml.getLong(p.getUniqueId().toString() + "." + ym, 0L);
    }

    public static String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }

    private void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save playtime.yml: " + e.getMessage());
        }
    }
}
