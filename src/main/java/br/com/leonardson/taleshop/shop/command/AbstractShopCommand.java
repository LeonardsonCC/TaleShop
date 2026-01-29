package br.com.leonardson.taleshop.shop.command;

import br.com.leonardson.taleshop.shop.ShopRegistry;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;

public abstract class AbstractShopCommand extends AbstractPlayerCommand {
    public final ShopRegistry shopRegistry;

    public AbstractShopCommand(String command, String description, ShopRegistry shopRegistry) {
        this.shopRegistry = shopRegistry;
        super(command, description);
    }
}
