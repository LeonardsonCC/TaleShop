package br.com.leonardson.taleshop.shop.command;

import br.com.leonardson.taleshop.shop.ShopRegistry;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class NpcCommands extends AbstractCommandCollection {
    public NpcCommands(ShopRegistry shopRegistry) {
        super("npc", "Shop NPC commands");

        // Allow non-OP players to use shop commands
        this.setPermissionGroup(GameMode.Adventure);

        // Require taleshop.shop permission
        this.requirePermission("taleshop.shop.npc");

        addSubCommand(new SpawnShopTraderCommand(shopRegistry));
        addSubCommand(new DespawnShopTraderCommand(shopRegistry));
    }
}
