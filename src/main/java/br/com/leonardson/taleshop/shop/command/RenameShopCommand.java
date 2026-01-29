package br.com.leonardson.taleshop.shop.command;

import org.jetbrains.annotations.NotNull;

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

import br.com.leonardson.taleshop.player.PlayerIdentity;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;

public class RenameShopCommand extends AbstractShopCommand {
    RequiredArg<String> argName;
    RequiredArg<String> argNewName;

    public RenameShopCommand(ShopRegistry shopRegistry) {
        super("rename", "Rename shop", shopRegistry);
        this.argName = this.withRequiredArg("name", "shop name", ArgTypes.STRING);
        this.argNewName = this.withRequiredArg("newName", "new shop name", ArgTypes.STRING);
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
