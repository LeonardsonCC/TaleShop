package br.com.leonardson.taleshop;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import br.com.leonardson.taleshop.shop.ui.PlayerInventoryPage;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import javax.annotation.Nonnull;

public class InventoryGridCommand extends AbstractPlayerCommand {
    public InventoryGridCommand() {
        super("inventoryui", "Opens a custom inventory grid UI.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().openCustomPage(ref, store, new PlayerInventoryPage(playerRef));
        }
    }
}
