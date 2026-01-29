package br.com.leonardson.taleshop.shop.trade.command;

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
import br.com.leonardson.taleshop.shop.command.AbstractShopCommand;
import br.com.leonardson.taleshop.shop.trade.Trade;

public class ListTradesCommand extends AbstractShopCommand {
    RequiredArg<String> argName;

    public ListTradesCommand(ShopRegistry shopRegistry) {
        super("list", "List trades", shopRegistry);
        this.argName = this.withRequiredArg("name", "shop name", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NotNull CommandContext ctx, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref, @NotNull PlayerRef playerRef, @NotNull World world) {
        String name = argName.get(ctx);
        if (name.isBlank()) {
            throw new IllegalArgumentException("Usage: /shop trade list <shop name>");
        }

        Player player = ctx.senderAs(Player.class);
        String ownerId = PlayerIdentity.resolveOwnerId(player);

        Shop shop = shopRegistry.getShop(ownerId, name);
        if (shop.trades().isEmpty()) {
            ctx.sendMessage(Message.raw("Shop has no trades."));
            return;
        }

        ctx.sendMessage(Message.raw("Trades for " + shop.name() + ":"));
        for (Trade trade : shop.trades()) {
            ctx.sendMessage(Message.raw("#" + trade.id() + ": " + trade.inputItemId() + " x" + trade.inputQuantity() + " -> "
                + trade.outputItemId() + " x" + trade.outputQuantity()));
        }
    }
}
