package br.com.leonardson.shop.command;

import br.com.leonardson.Main;
import br.com.leonardson.shop.Shop;
import br.com.leonardson.shop.ShopRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.logging.Level;

public class ShopCreateCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> shopNameArg = this.withRequiredArg("name", "Shop name", ArgTypes.STRING);

    public ShopCreateCommand() {
        super("create", "Create a shop");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        String shopName = shopNameArg.get(commandContext);
        if (shopName == null || shopName.trim().isEmpty()) {
            commandContext.sendMessage(Message.raw("Shop name cannot be empty."));
            return;
        }

        ShopRegistry registry = Main.getInstance().getShopRegistry();
        if (registry == null) {
            commandContext.sendMessage(Message.raw("Shop system is not available."));
            return;
        }

        try {
            Shop shop = registry.registerShop(playerRef, shopName.trim());
            commandContext.sendMessage(Message.raw("Shop created: " + shop.getShopName() + " (ID " + shop.getId() + ")"));
        } catch (SQLException e) {
            Main.getInstance().getLogger().at(Level.SEVERE).log("Failed to create shop: " + e.getMessage());
            commandContext.sendMessage(Message.raw("Failed to create shop. Please try again later."));
        }
    }
}
