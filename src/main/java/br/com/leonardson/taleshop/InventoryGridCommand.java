package br.com.leonardson.taleshop;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import br.com.leonardson.taleshop.shop.ui.PlayerInventoryPage;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import javax.annotation.Nonnull;

public class InventoryGridCommand extends CommandBase {
    public InventoryGridCommand() {
        super("inventoryui", "Opens a custom inventory grid UI.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(Message.raw("This command can only be used by players."));
            return;
        }

        Player sender = ctx.senderAs(Player.class);
        if (sender.getWorld() == null) {
            ctx.sendMessage(Message.raw("Player world is not available."));
            return;
        }

        sender.getWorld().execute(() -> {
            Ref<EntityStore> ref = sender.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }

            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRef == null) {
                return;
            }

            player.getPageManager().openCustomPage(ref, store, new PlayerInventoryPage(playerRef));
        });
    }
}
