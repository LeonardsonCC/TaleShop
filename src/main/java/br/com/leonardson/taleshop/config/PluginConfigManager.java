package br.com.leonardson.taleshop.config;

import com.hypixel.hytale.logger.HytaleLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class PluginConfigManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String CONFIG_FILE = "TaleShopConfig.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Path configPath;
    private PluginConfig config;
    
    public PluginConfigManager(@Nonnull Path dataDirectory) {
        this.configPath = dataDirectory.resolve(CONFIG_FILE);
        this.config = new PluginConfig();
        load();
    }
    
    private void load() {
        try {
            if (!Files.exists(configPath)) {
                LOGGER.atInfo().log("Config file not found, creating default configuration");
                save();
                return;
            }
            
            try (Reader reader = Files.newBufferedReader(configPath)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    config.setStorageBackend(parseBackend(data.StorageBackend));
                    config.setStorageDistanceMode(parseMode(data.StorageDistanceMode));
                    config.setFixedStorageDistance(data.FixedStorageDistance);
                    LOGGER.atInfo().log("Loaded configuration from: %s", configPath);
                } else {
                    LOGGER.atInfo().log("Config file is empty, using defaults");
                    save();
                }
            }
            
        } catch (Exception e) {
            LOGGER.atInfo().log("Failed to load configuration: %s - using defaults", e.getMessage());
            save();
        }
    }
    
    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            
            ConfigData data = new ConfigData();
            data.StorageBackend = config.getStorageBackend().name();
            data.StorageDistanceMode = config.getStorageDistanceMode().name();
            data.FixedStorageDistance = config.getFixedStorageDistance();
            
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(data, writer);
                LOGGER.atInfo().log("Saved configuration to: %s", configPath);
            }
            
        } catch (IOException e) {
            LOGGER.atInfo().log("Failed to save configuration: %s", e.getMessage());
        }
    }
    
    @Nonnull
    public PluginConfig getConfig() {
        return config;
    }
    
    private PluginConfig.StorageDistanceMode parseMode(String value) {
        try {
            return PluginConfig.StorageDistanceMode.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return PluginConfig.StorageDistanceMode.FIXED;
        }
    }

    private PluginConfig.StorageBackend parseBackend(String value) {
        try {
            return PluginConfig.StorageBackend.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return PluginConfig.StorageBackend.JSON;
        }
    }
    
    // Internal class for JSON serialization
    private static class ConfigData {
        String StorageBackend = "JSON";
        String StorageDistanceMode = "FIXED";
        int FixedStorageDistance = 2;
    }
}
