package br.com.leonardson.taleshop.shop.command;

import br.com.leonardson.taleshop.player.PlayerIdentity;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.TraderNpc;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class SpawnShopTraderCommand extends AbstractShopCommand {
    DefaultArg<String> argName;
    private final Map<String, TraderNpc.SpawnResult> activeTraders = new HashMap<>();

    public SpawnShopTraderCommand(ShopRegistry shopRegistry) {
        super("spawn", "Spawn shop trader", shopRegistry);

        this.argName = this.withDefaultArg("name", "shop name", ArgTypes.STRING, "Shop", "Shop as default");
    }

    @Override
    protected void execute(@NotNull CommandContext ctx, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref, @NotNull PlayerRef playerRef, @NotNull World world) {
        String name = argName.get(ctx);
        if (name.isBlank()) {
            throw new IllegalArgumentException("Usage: /shop spawn <shop name>");
        }

        Player player = ctx.senderAs(Player.class);
        String ownerId = PlayerIdentity.resolveOwnerId(player);

        Shop shop = shopRegistry.getShop(ownerId, name);
        String key = ownerId + ":" + shop.name();
        TraderNpc.SpawnResult existingHandle = activeTraders.remove(key);
        if (existingHandle != null) {
            TraderNpc.despawn(store, existingHandle);
        }

        String existingTrader = shopRegistry.getTraderUuid(ownerId, shop.name());
        if (existingTrader != null && !existingTrader.isBlank()) {
            TraderNpc.despawnByUuid(store, existingTrader);
        }

        TraderNpc traderNpc = new TraderNpc(shop.name() + " Trader");
        TraderNpc.SpawnResult spawnResult;
        try {
            spawnResult = traderNpc.spawn(store, ref);
        } catch (IllegalStateException ex) {
            ctx.sendMessage(Message.raw(ex.getMessage()));
            return;
        }

        shopRegistry.setTraderUuid(ownerId, shop.name(), spawnResult.uuid());
        activeTraders.put(key, spawnResult);
        ctx.sendMessage(Message.raw("Trader spawned for " + shop.name() + "."));
    }
}
