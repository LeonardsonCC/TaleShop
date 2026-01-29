package br.com.leonardson.taleshop.shop.command;

import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.trade.command.TradeCommands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class ShopCommands extends AbstractCommandCollection {
    public ShopCommands(ShopRegistry shopRegistry) {
        super("shop", "Shop commands");

        // Allow non-OP players to use shop commands
        // this.setPermissionGroup(GameMode.Adventure);
        
        // Require taleshop.shop permission
        this.requirePermission("taleshop.shop");

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
