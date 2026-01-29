package br.com.leonardson.taleshop.shop.command;

import br.com.leonardson.taleshop.player.PlayerIdentity;
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

public class ShopEditorCommand extends AbstractShopCommand {
    public ShopEditorCommand(ShopRegistry shopRegistry) {
        super("editor", "Open shop management UI", shopRegistry);
    }

    @Override
    protected void execute(@NotNull CommandContext ctx, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref, @NotNull PlayerRef playerRef, @NotNull World world) {
        Player player = ctx.senderAs(Player.class);
        String ownerId = PlayerIdentity.resolveOwnerId(player);

        // Open the shop list page
        player.getPageManager().openCustomPage(ref, store, new ShopListPage(playerRef, ownerId));
    }
}
