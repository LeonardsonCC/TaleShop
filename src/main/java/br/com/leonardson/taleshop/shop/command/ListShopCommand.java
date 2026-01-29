package br.com.leonardson.taleshop.shop.command;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import br.com.leonardson.taleshop.player.PlayerIdentity;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;

public class ListShopCommand extends AbstractShopCommand {
    public ListShopCommand(ShopRegistry shopRegistry) {
        super("list", "List shop", shopRegistry);
        this.requirePermission("taleshop.shop.manage");
    }

    @Override
    protected void execute(@NotNull CommandContext ctx, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref, @NotNull PlayerRef playerRef, @NotNull World world) {
        Player player = ctx.senderAs(Player.class);
        String ownerId = PlayerIdentity.resolveOwnerId(player);

        List<Shop> shops = shopRegistry.listShops(ownerId);
        if (shops.isEmpty()) {
            ctx.sendMessage(Message.raw("You do not have any shops."));
            return;
        }

        ctx.sendMessage(Message.raw("Your shops: " + shops.size()));
        for (Shop shop : shops) {
            ctx.sendMessage(Message.raw("- " + shop.name() + " (" + shop.trades().size() + " trades)"));
        }
    }
}
