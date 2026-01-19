package br.com.leonardson;

import br.com.leonardson.database.DatabaseManager;
import br.com.leonardson.shop.command.ShopCommand;
import br.com.leonardson.shop.ShopRegistry;
import br.com.leonardson.shop.ShopRepository;
import br.com.leonardson.shop.ShopNpcRegistry;
import br.com.leonardson.shop.interaction.ShopOwnerInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.List;

public class Main extends JavaPlugin {
    private static Main instance;
    private DatabaseManager databaseManager;
    private ShopRegistry shopRegistry;
    private ShopNpcRegistry shopNpcRegistry;

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

        shopRegistry = new ShopRegistry(new ShopRepository(databaseManager));
        shopNpcRegistry = new ShopNpcRegistry();

        getCodecRegistry(Interaction.CODEC)
            .register("ShopOwnerInteraction", ShopOwnerInteraction.class, ShopOwnerInteraction.CODEC);
        Interaction.getAssetStore().loadAssets(getIdentifier().toString(), List.of(new ShopOwnerInteraction(ShopOwnerInteraction.INTERACTION_ID)));
        RootInteraction.getAssetStore().loadAssets(getIdentifier().toString(), List.of(ShopOwnerInteraction.ROOT));

        getCommandRegistry().registerCommand(new ShopCommand());
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

    public ShopRegistry getShopRegistry() {
        return shopRegistry;
    }

    public ShopNpcRegistry getShopNpcRegistry() {
        return shopNpcRegistry;
    }

}
