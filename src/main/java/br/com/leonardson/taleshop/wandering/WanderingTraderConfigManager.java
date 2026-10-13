package br.com.leonardson.taleshop.wandering;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WanderingTraderConfigManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private WanderingTraderConfig config = new WanderingTraderConfig();
    private boolean valid;

    public WanderingTraderConfigManager(Path directory) {
        path = directory.resolve("WanderingTraderConfig.json");
        load();
    }

    private void load() {
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                save();
                valid = true;
                return;
            }
            try (Reader reader = Files.newBufferedReader(path)) {
                WanderingTraderConfig loaded = GSON.fromJson(reader, WanderingTraderConfig.class);
                if (loaded == null) throw new IllegalArgumentException("Configuration is empty");
                loaded.validate();
                config = loaded;
                valid = true;
            }
        } catch (Exception error) {
            valid = false;
            LOGGER.atWarning().log("Wandering trader disabled: invalid configuration at %s (%s)", path, error.getMessage());
        }
    }

    public void save() throws java.io.IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary)) { GSON.toJson(config, writer); }
        try {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public WanderingTraderConfig getConfig() { return config; }
    public boolean isValid() { return valid; }
}
