package br.com.leonardson.taleshop.shop.command;

import br.com.leonardson.taleshop.shop.ShopRegistry;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class NpcCommands extends AbstractCommandCollection {
    public NpcCommands(ShopRegistry shopRegistry) {
        super("npc", "Shop NPC commands");

        addSubCommand(new SpawnShopTraderCommand(shopRegistry));
        addSubCommand(new DespawnShopTraderCommand(shopRegistry));
    }
}
