package br.com.leonardson.taleshop.shop.command;

import br.com.leonardson.taleshop.shop.AdminShopAccess;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.ui.ShopListPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;

public class AdminShopEditorCommand extends AbstractShopCommand {
    public AdminShopEditorCommand(ShopRegistry shopRegistry) {
        super("admin", "Open admin shop management UI", shopRegistry);
        this.requirePermission("taleshop.admin.manage");
    }

    @Override
    protected void execute(
        @NotNull CommandContext ctx,
        @NotNull Store<EntityStore> store,
        @NotNull Ref<EntityStore> ref,
        @NotNull PlayerRef playerRef,
        @NotNull World world
    ) {
        Player player = ctx.senderAs(Player.class);
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new ShopListPage(playerRef, AdminShopAccess.OWNER_ID, true));
    }
}
