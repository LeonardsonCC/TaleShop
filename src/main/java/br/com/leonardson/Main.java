package br.com.leonardson;

import br.com.leonardson.database.DatabaseManager;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class Main extends JavaPlugin {
    private static Main instance;
    private DatabaseManager databaseManager;

    public Main(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        super.setup();

        getLogger().at(Level.INFO).log("[TaleShops] Setup");

        // Initialize database
        databaseManager = new DatabaseManager(this.getLogger());
        databaseManager.connect();
    }

    @Override
    protected void shutdown() {
        super.shutdown();

        // Disconnect database on shutdown
        if (databaseManager != null && databaseManager.isConnected()) {
            databaseManager.disconnect();
        }
    }

    public static Main getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

}
