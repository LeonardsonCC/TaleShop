package br.com.leonardson.taleshop.shop.command;

import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.ui.ShopBuyerPage;
import br.com.leonardson.taleshop.shop.ui.TraderMenuPage;
import br.com.leonardson.taleshop.player.PlayerIdentity;
import br.com.leonardson.taleshop.permission.PermissionUtil;
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
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class OpenShopCommand extends AbstractShopCommand {
    RequiredArg<String> argOwnerName;
    RequiredArg<String> argShopName;

    public OpenShopCommand(ShopRegistry shopRegistry) {
        super("open", "Open a shop remotely", shopRegistry);
        this.requirePermission("taleshop.shop.open");

        this.argOwnerName = this.withRequiredArg("owner", "shop owner name", ArgTypes.STRING);
        this.argShopName = this.withRequiredArg("shop", "shop name", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NotNull CommandContext ctx, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref, @NotNull PlayerRef playerRef, @NotNull World world) {
        String ownerName = argOwnerName.get(ctx);
        String shopName = argShopName.get(ctx);

        if (ownerName.isBlank()) {
            throw new IllegalArgumentException("Usage: /shop open <owner name> <shop name>");
        }
        if (shopName.isBlank()) {
            throw new IllegalArgumentException("Usage: /shop open <owner name> <shop name>");
        }

        Player player = ctx.senderAs(Player.class);
        String currentPlayerId = PlayerIdentity.resolveOwnerId(player);

        // Find the shop by owner name and shop name
        Shop targetShop = findShopByOwnerName(ownerName, shopName);
        if (targetShop == null) {
            ctx.sendMessage(Message.raw("Shop '" + shopName + "' owned by '" + ownerName + "' not found."));
            return;
        }

        // Check if the current player can manage the shop
        boolean canManage = targetShop.ownerId().equals(currentPlayerId)
            || (targetShop.isAdmin() && PermissionUtil.hasAdminManagePermission(player));
        if (canManage) {
            // Owner opens the trader menu (management UI)
            player.getPageManager().openCustomPage(ref, store, new TraderMenuPage(playerRef, targetShop.ownerId(), targetShop.name()));
        } else {
            // Non-owner opens the buyer page (shopping UI)
            player.getPageManager().openCustomPage(ref, store, new ShopBuyerPage(playerRef, targetShop.ownerId(), targetShop.name()));
        }
    }

    private Shop findShopByOwnerName(String ownerName, String shopName) {
        List<Shop> allShops = shopRegistry.listAllShops();
        
        // First try exact match (case-sensitive)
        for (Shop shop : allShops) {
            if (shop.ownerName().equals(ownerName) && shop.name().equalsIgnoreCase(shopName)) {
                return shop;
            }
        }
        
        // Then try case-insensitive match
        for (Shop shop : allShops) {
            if (shop.ownerName().equalsIgnoreCase(ownerName) && shop.name().equalsIgnoreCase(shopName)) {
                return shop;
            }
        }
        
        return null;
    }
}
