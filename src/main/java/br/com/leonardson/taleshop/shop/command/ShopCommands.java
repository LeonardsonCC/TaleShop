package br.com.leonardson.taleshop.shop.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.trade.command.TradeCommands;

public class ShopCommands extends AbstractCommandCollection {
    public ShopCommands(ShopRegistry shopRegistry) {
        super("shop", "Shop commands");

        this.requirePermission("taleshop.shop.manage");

        this.addAliases("taleshop", "tshop");

        addSubCommand(new CreateShopCommand(shopRegistry));
        addSubCommand(new RenameShopCommand(shopRegistry));
        addSubCommand(new DeleteShopCommand(shopRegistry));
        addSubCommand(new GetShopCommand(shopRegistry));
        addSubCommand(new ListShopCommand(shopRegistry));
        addSubCommand(new ShopEditorCommand(shopRegistry));
        addSubCommand(new NpcCommands(shopRegistry));

        addSubCommand(new TradeCommands(shopRegistry));
    }
}
