package de.bonescraft.land;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class BonescraftLand extends JavaPlugin {
    private ClaimManager claimManager;
    private ContainerOwnerStore containerOwnerStore;
    private PlaytimeTracker playtimeTracker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.claimManager = new ClaimManager(this);
        this.containerOwnerStore = new ContainerOwnerStore(this);
        this.playtimeTracker = new PlaytimeTracker(this);

        // Listeners
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(playtimeTracker, this);

        // Commands
        getCommand("land").setExecutor(new LandCommand(this));
        getCommand("playtime").setExecutor(new PlaytimeCommand(this));

        getLogger().info("BonescraftLand enabled.");
    }

    @Override
    public void onDisable() {
        if (playtimeTracker != null) {
            playtimeTracker.flushAllOnline();
        }
        if (claimManager != null) {
            claimManager.save();
        }
        if (containerOwnerStore != null) {
            containerOwnerStore.save();
        }
        if (playtimeTracker != null) {
            playtimeTracker.save();
        }
        getLogger().info("BonescraftLand disabled.");
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public ContainerOwnerStore getContainerOwnerStore() {
        return containerOwnerStore;
    }

    public PlaytimeTracker getPlaytimeTracker() {
        return playtimeTracker;
    }
}
