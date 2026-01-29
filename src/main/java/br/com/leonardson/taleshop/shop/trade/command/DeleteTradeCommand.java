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
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.command.AbstractShopCommand;

public class DeleteTradeCommand extends AbstractShopCommand {
    RequiredArg<String> argName;
    RequiredArg<Integer> tradeIdArg;

    public DeleteTradeCommand(ShopRegistry shopRegistry) {
        super("delete", "Delete trade", shopRegistry);
        this.argName = this.withRequiredArg("name", "shop name", ArgTypes.STRING);
        this.tradeIdArg = this.withRequiredArg("tradeId", "trade id", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@NotNull CommandContext ctx, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref, @NotNull PlayerRef playerRef, @NotNull World world) {
        String shopName = argName.get(ctx);
        int tradeId = tradeIdArg.get(ctx);

        Player player = ctx.senderAs(Player.class);
        String ownerId = PlayerIdentity.resolveOwnerId(player);
        
        shopRegistry.removeTrade(ownerId, shopName, tradeId);
        ctx.sendMessage(Message.raw("Trade removed (#" + tradeId + ")."));
    }
}
