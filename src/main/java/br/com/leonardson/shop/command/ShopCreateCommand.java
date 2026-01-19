package br.com.leonardson.shop.command;

import br.com.leonardson.Main;
import br.com.leonardson.shop.Shop;
import br.com.leonardson.shop.ShopRegistry;
import br.com.leonardson.shop.interaction.ShopOwnerInteraction;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.logging.Level;

public class ShopCreateCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> shopNameArg = this.withRequiredArg("name", "Shop name", ArgTypes.STRING);
    private static final String SHOP_INTERACTION_HINT = "Trade";

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
            spawnShopTrader(commandContext, store, ref, shop);
        } catch (SQLException e) {
            Main.getInstance().getLogger().at(Level.SEVERE).log("Failed to create shop: " + e.getMessage());
            commandContext.sendMessage(Message.raw("Failed to create shop. Please try again later."));
        }
    }

    private void spawnShopTrader(
            @Nonnull CommandContext commandContext,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull Shop shop
    ) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            commandContext.sendMessage(Message.raw("NPC system is not available."));
            return;
        }

        int roleIndex = getEntityIndex(npcPlugin);
        if (roleIndex < 0) {
            commandContext.sendMessage(Message.raw("Goblin Scrapper role not found. Shop created without a trader."));
            return;
        }

        TransformComponent transformComponent = store.getComponent(playerRef, TransformComponent.getComponentType());
        HeadRotation headRotationComponent = store.getComponent(playerRef, HeadRotation.getComponentType());
        if (transformComponent == null || headRotationComponent == null) {
            commandContext.sendMessage(Message.raw("Could not determine spawn position. Shop created without a trader."));
            return;
        }

        TransformComponent spawnTransform = new TransformComponent(
            new Vector3d(transformComponent.getPosition()),
            new Vector3f(headRotationComponent.getRotation())
        );
        Vector3d spawnPosition = new Vector3d(spawnTransform.getPosition()).add(0.0, 0.0, 1.5);
        Vector3f spawnRotation = new Vector3f(spawnTransform.getRotation());

        Pair<Ref<EntityStore>, NPCEntity> npcPair = npcPlugin.spawnEntity(
                store,
                roleIndex,
                spawnPosition,
                spawnRotation,
                null,
                (npc, npcRef, entityStore) -> {
                    Interactions interactions = entityStore.getComponent(npcRef, Interactions.getComponentType());
                    if (interactions == null) {
                        interactions = new Interactions();
                    }
                    interactions.setInteractionId(InteractionType.Use, ShopOwnerInteraction.INTERACTION_ID);
                    interactions.setInteractionHint(SHOP_INTERACTION_HINT + ": " + shop.getShopName());
                    entityStore.putComponent(npcRef, Interactions.getComponentType(), interactions);
                }
        );

        if (npcPair == null || npcPair.first() == null || !npcPair.first().isValid()) {
            commandContext.sendMessage(Message.raw("Failed to spawn Goblin Scrapper."));
        } else {
            UUIDComponent uuidComponent = store.getComponent(npcPair.first(), UUIDComponent.getComponentType());
            if (uuidComponent != null) {
                Main.getInstance().getShopNpcRegistry().registerNpc(uuidComponent.getUuid(), shop);
            }
            commandContext.sendMessage(Message.raw("Goblin Scrapper spawned for shop: " + shop.getShopName()));
        }
    }

    private int getEntityIndex(@Nonnull NPCPlugin npcPlugin) {
        return npcPlugin.getIndex("Klops_Merchant");
    }
}
