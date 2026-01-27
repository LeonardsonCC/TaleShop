package br.com.leonardson.taleshop;

import java.util.List;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import br.com.leonardson.taleshop.config.PluginConfig;
import br.com.leonardson.taleshop.config.PluginConfigManager;
import br.com.leonardson.taleshop.interaction.TraderMessageInteraction;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.TraderInteractableSystem;
import br.com.leonardson.taleshop.shop.command.ShopCommands;

public class TaleShop extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static TaleShop instance;
    private ShopRegistry shopRegistry;
    private PluginConfigManager configManager;

    public TaleShop(JavaPluginInit init) {
        super(init);
        instance = this;
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
    }

    public static TaleShop getInstance() {
        return instance;
    }

    public ShopRegistry getShopRegistry() {
        return shopRegistry;
    }

    public PluginConfig getPluginConfig() {
        return configManager != null ? configManager.getConfig() : null;
    }

    @Override
    protected void setup() {
        configManager = new PluginConfigManager(ShopRegistry.resolveDataDirectory(this));
        
        // Log configuration
        PluginConfig cfg = configManager.getConfig();
        LOGGER.atInfo().log("Storage Distance Mode: %s", cfg.getStorageDistanceMode());
        LOGGER.atInfo().log("Fixed Storage Distance: %d blocks", cfg.getFixedStorageDistance());
        
        shopRegistry = new ShopRegistry(ShopRegistry.resolveDataDirectory(this));
        // Commands
        this.getCommandRegistry().registerCommand(new ShopCommands(shopRegistry));
//        this.getCommandRegistry().registerCommand(new ExampleCommand(this.getName(), this.getManifest().getVersion().toString()));
//        this.getCommandRegistry().registerCommand(new InventoryGridCommand());
//        this.getCommandRegistry().registerCommand(new SpawnTraderCommand());

        // Interactions
        this.getCodecRegistry(Interaction.CODEC)
             .register("TraderMessageInteraction", TraderMessageInteraction.class, TraderMessageInteraction.CODEC);

        this.getEntityStoreRegistry().registerSystem(new TraderInteractableSystem(shopRegistry));
    }

    @Override
    protected void start() {
        Interaction.getAssetStore().loadAssets(
            getIdentifier().toString(),
            List.of(new TraderMessageInteraction(TraderMessageInteraction.INTERACTION_ID))
        );
        RootInteraction.getAssetStore().loadAssets(
            getIdentifier().toString(),
            List.of(TraderMessageInteraction.ROOT)
        );
    }
}
