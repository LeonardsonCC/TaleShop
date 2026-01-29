package br.com.leonardson.taleshop.shop;

import javax.annotation.Nonnull;

import br.com.leonardson.taleshop.interaction.TraderMessageInteraction;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class TraderInteractableSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final ShopRegistry shopRegistry;

    public TraderInteractableSystem(ShopRegistry shopRegistry) {
        this.shopRegistry = shopRegistry;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        if (UUIDComponent.getComponentType() == null) {
            return Query.any();
        }
        return UUIDComponent.getComponentType();
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (UUIDComponent.getComponentType() == null) {
            return;
        }
        UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }
        Shop shop = shopRegistry.findShopByTraderUuid(uuidComponent.getUuid().toString());
        if (shop == null) {
            return;
        }

        commandBuffer.putComponent(ref, Interactable.getComponentType(), Interactable.INSTANCE);
        Interactions interactions = commandBuffer.getComponent(ref, Interactions.getComponentType());
        if (interactions == null) {
            interactions = new Interactions();
        }
        interactions.setInteractionId(InteractionType.Use, TraderMessageInteraction.ROOT_INTERACTION_ID);
        interactions.setInteractionHint("Trade");
        commandBuffer.putComponent(ref, Interactions.getComponentType(), interactions);
        LOGGER.atInfo().log("Bound trader interaction for shop %s (%s)", shop.name(), shop.traderUuid());
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }
}
