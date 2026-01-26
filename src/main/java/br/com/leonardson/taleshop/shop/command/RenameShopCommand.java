package br.com.leonardson.taleshop.shop.command;

import br.com.leonardson.taleshop.player.PlayerIdentity;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;

public class RenameShopCommand extends AbstractShopCommand {
    DefaultArg<String> argName;
    DefaultArg<String> argNewName;

    public RenameShopCommand(ShopRegistry shopRegistry) {
        super("rename", "Rename shop", shopRegistry);
        this.argName = this.withDefaultArg("name", "shop name", ArgTypes.STRING, "Shop", "Shop as default");
        this.argNewName = this.withDefaultArg("newName", "new shop name", ArgTypes.STRING, "NewShop", "New shop as default");
    }

    @Override
    protected void execute(@NotNull CommandContext ctx, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref, @NotNull PlayerRef playerRef, @NotNull World world) {
        String name = argName.get(ctx);
        String newName = argNewName.get(ctx);

        Player player = ctx.senderAs(Player.class);
        String ownerId = PlayerIdentity.resolveOwnerId(player);

        Shop shop = shopRegistry.renameShop(ownerId, name, newName);
        ctx.sendMessage(Message.raw("Shop renamed to: " + shop.name() + "."));
    }
}
