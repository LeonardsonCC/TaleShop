package br.com.leonardson.taleshop.shop.command;

import org.jetbrains.annotations.NotNull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import br.com.leonardson.taleshop.player.PlayerIdentity;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.TraderNpc;

public class DespawnShopTraderCommand extends AbstractShopCommand {
    RequiredArg<String> argName;

    public DespawnShopTraderCommand(ShopRegistry shopRegistry) {
        super("despawn", "Despawn shop trader", shopRegistry);
        this.requirePermission("taleshop.shop.manage");

        this.argName = this.withRequiredArg("name", "shop name", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NotNull CommandContext ctx, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref,
            @NotNull PlayerRef playerRef, @NotNull World world) {
        String name = argName.get(ctx);
        if (name.isBlank()) {
            throw new IllegalArgumentException("Usage: /shop npc despawn <shop name>");
        }

        Player player = ctx.senderAs(Player.class);
        String ownerId = PlayerIdentity.resolveOwnerId(player);

        Shop shop = shopRegistry.getShop(ownerId, name);

        boolean removed = false;
        String existingTrader = shopRegistry.getTraderUuid(ownerId, shop.name());
        if (existingTrader != null && !existingTrader.isBlank()) {
            removed = TraderNpc.despawnByUuid(store, existingTrader) || removed;
        }
        if (removed) {
            shopRegistry.clearTraderUuid(ownerId, shop.name());
        }

        if (removed) {
            ctx.sendMessage(Message.raw("Trader despawned for " + shop.name() + "."));
        } else {
            String uuidInfo = existingTrader == null || existingTrader.isBlank()
                    ? "none"
                    : existingTrader;
            ctx.sendMessage(Message.raw("No trader found for " + shop.name()
                    + ". UUID: " + uuidInfo));
        }
    }
}
