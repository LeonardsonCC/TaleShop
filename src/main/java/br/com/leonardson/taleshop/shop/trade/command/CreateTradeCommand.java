package br.com.leonardson.taleshop.shop.trade.command;

import org.jetbrains.annotations.NotNull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import br.com.leonardson.taleshop.player.PlayerIdentity;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.command.AbstractShopCommand;
import br.com.leonardson.taleshop.shop.trade.Trade;

public class CreateTradeCommand extends AbstractShopCommand {
    DefaultArg<String> argName;
    RequiredArg<String> inputItemArg;
    RequiredArg<Integer> inputQtyArg;
    RequiredArg<String> outputItemArg;
    RequiredArg<Integer> outputQtyArg;

    public CreateTradeCommand(ShopRegistry shopRegistry) {
        super("create", "Create trade", shopRegistry);
        this.argName = this.withDefaultArg("name", "shop name", ArgTypes.STRING, "Shop", "Shop as default");
        this.inputItemArg = this.withRequiredArg("inputItem", "input item", ArgTypes.STRING);
        this.inputQtyArg = this.withRequiredArg("inputQty", "input quantity", ArgTypes.INTEGER);
        this.outputItemArg = this.withRequiredArg("outputItem", "output item", ArgTypes.STRING);
        this.outputQtyArg = this.withRequiredArg("outputQty", "output quantity", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@NotNull CommandContext ctx, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref, @NotNull PlayerRef playerRef, @NotNull World world) {
        String shopName = argName.get(ctx);
        String inputItem = inputItemArg.get(ctx);
        int inputQty = inputQtyArg.get(ctx);
        String outputItem = outputItemArg.get(ctx);
        int outputQty = outputQtyArg.get(ctx);

        Player player = ctx.senderAs(Player.class);
        String ownerId = PlayerIdentity.resolveOwnerId(player);

        Trade trade = shopRegistry.addTrade(ownerId, shopName, inputItem, inputQty, outputItem, outputQty);
        ctx.sendMessage(Message.raw("Trade added (#" + trade.id() + ")."));
    }
}
