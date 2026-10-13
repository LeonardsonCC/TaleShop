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
import br.com.leonardson.taleshop.wandering.WanderingTraderConfigManager;
import br.com.leonardson.taleshop.wandering.WanderingTraderService;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;

public class TaleShop extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static TaleShop instance;
    private ShopRegistry shopRegistry;
    private PluginConfigManager configManager;
    private WanderingTraderService wanderingTraderService;

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

    public WanderingTraderService getWanderingTraderService() { return wanderingTraderService; }

    @Override
    protected void setup() {
        configManager = new PluginConfigManager(ShopRegistry.resolveDataDirectory(this));
        
        // Log configuration
        PluginConfig cfg = configManager.getConfig();
        LOGGER.atInfo().log("Storage Backend: %s", cfg.getStorageBackend());
        LOGGER.atInfo().log("Storage Distance Mode: %s", cfg.getStorageDistanceMode());
        LOGGER.atInfo().log("Fixed Storage Distance: %d blocks", cfg.getFixedStorageDistance());
        LOGGER.atInfo().log("Maximum Trades Per Shop: %d", cfg.getMaxTradesPerShop());
        
        shopRegistry = new ShopRegistry(ShopRegistry.resolveDataDirectory(this), cfg);
        WanderingTraderConfigManager wanderingConfig = new WanderingTraderConfigManager(ShopRegistry.resolveDataDirectory(this));
        if (wanderingConfig.isValid()) {
            wanderingTraderService = new WanderingTraderService(wanderingConfig.getConfig(), ShopRegistry.resolveDataDirectory(this));
        }
        // Commands
        this.getCommandRegistry().registerCommand(new ShopCommands(shopRegistry));

        // Interactions
        this.getCodecRegistry(Interaction.CODEC)
             .register("TraderMessageInteraction", TraderMessageInteraction.class, TraderMessageInteraction.CODEC);

        this.getEntityStoreRegistry().registerSystem(new TraderInteractableSystem(shopRegistry));

        Universe universe = Universe.get();
        if (universe != null && wanderingTraderService != null) {
            universe.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class,
                    event -> wanderingTraderService.startWorld(event.getWorld()));
        }
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

        if (wanderingTraderService != null) wanderingTraderService.start();
        
    }

    @Override
    protected void shutdown() {
        if (wanderingTraderService != null) wanderingTraderService.close();
        if (shopRegistry != null) shopRegistry.close();
    }
}
