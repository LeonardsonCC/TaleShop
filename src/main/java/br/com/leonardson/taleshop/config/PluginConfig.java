package br.com.leonardson.taleshop.config;

import javax.annotation.Nonnull;

public class PluginConfig {
    public enum StorageDistanceMode {
        FIXED,      // Use fixed distance defined in config
        WORKBENCH   // Use the game's crafting workbench default distance
    }

    private StorageDistanceMode storageDistanceMode = StorageDistanceMode.FIXED;
    private int fixedStorageDistance = 2;

    public PluginConfig() {
    }

    public StorageDistanceMode getStorageDistanceMode() {
        return storageDistanceMode;
    }

    public int getFixedStorageDistance() {
        return fixedStorageDistance;
    }

    public void setStorageDistanceMode(@Nonnull StorageDistanceMode mode) {
        this.storageDistanceMode = mode;
    }

    public void setFixedStorageDistance(int distance) {
        this.fixedStorageDistance = Math.max(1, distance);
    }

    public boolean isUsingFixedDistance() {
        return storageDistanceMode == StorageDistanceMode.FIXED;
    }

    public boolean isUsingWorkbenchDistance() {
        return storageDistanceMode == StorageDistanceMode.WORKBENCH;
    }
}
