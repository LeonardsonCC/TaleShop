package br.com.leonardson.taleshop.shop.command;

import org.jetbrains.annotations.NotNull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import br.com.leonardson.taleshop.permission.PermissionUtil;
import br.com.leonardson.taleshop.player.PlayerIdentity;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.TraderNpc;
import br.com.leonardson.taleshop.shop.ui.EntitySelectionPage;

public class SpawnShopTraderCommand extends AbstractShopCommand {
    RequiredArg<String> argName;
    OptionalArg<String> argEntityRole;

    public SpawnShopTraderCommand(ShopRegistry shopRegistry) {
        super("spawn", "Spawn shop trader", shopRegistry);
        this.requirePermission("taleshop.shop.manage");

        this.argName = this.withRequiredArg("name", "shop name", ArgTypes.STRING);
        this.argEntityRole = this.withOptionalArg("entityRole", "entity role (optional)", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NotNull CommandContext ctx, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref, @NotNull PlayerRef playerRef, @NotNull World world) {
        String name = argName.get(ctx);
        if (name.isBlank()) {
            throw new IllegalArgumentException("Usage: /shop npc spawn <shop name> [entityRole]");
        }

        Player player = ctx.senderAs(Player.class);
        String ownerId = PlayerIdentity.resolveOwnerId(player);

        Shop shop = shopRegistry.getShop(ownerId, name);
        String existingTrader = shopRegistry.getTraderUuid(ownerId, shop.name());
        if (existingTrader != null && !existingTrader.isBlank()) {
            TraderNpc.despawnByUuid(store, existingTrader);
        }

        // Check if entity role was provided
        String entityRole = argEntityRole.get(ctx);
        
        // If no entity role specified and player has permission, open UI
        if ((entityRole == null || entityRole.isBlank()) && PermissionUtil.hasEntitySelectionPermission(player)) {
            player.getPageManager().openCustomPage(ref, store, new EntitySelectionPage(playerRef, ownerId, shop.name()));
            return;
        }
        
        // If entity role specified, check permission
        if (entityRole != null && !entityRole.isBlank()) {
            if (!PermissionUtil.hasEntitySelectionPermission(player)) {
                ctx.sendMessage(Message.raw("You don't have permission to select custom entity types. Using default entity."));
                entityRole = "Klops_Merchant";
            }
        } else {
            // Use default entity role
            entityRole = "Klops_Merchant";
        }

        TraderNpc traderNpc = new TraderNpc(shop.name(), entityRole);
        try {
            traderNpc.spawn(store, ref);
        } catch (IllegalStateException ex) {
            ctx.sendMessage(Message.raw(ex.getMessage()));
            return;
        }

        String traderUuid = traderNpc.getUuid(store);
        if (traderUuid == null || traderUuid.isBlank()) {
            ctx.sendMessage(Message.raw("Trader spawned, but UUID was not available."));
            return;
        }

        shopRegistry.setTraderUuid(ownerId, shop.name(), traderUuid);
        ctx.sendMessage(Message.raw("Trader spawned as " + entityRole + " for " + shop.name() + "."));
    }
}
