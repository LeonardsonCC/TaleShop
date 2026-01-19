package br.com.leonardson.shop.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class ShopCommand extends AbstractCommandCollection {
    public ShopCommand() {
        super("shop", "Shop commands");
        addSubCommand(new ShopCreateCommand());
    }
}
