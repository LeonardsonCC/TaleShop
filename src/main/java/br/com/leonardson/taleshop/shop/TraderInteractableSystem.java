package br.com.leonardson.taleshop.shop;

import java.util.Set;

import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;

import javax.annotation.Nonnull;

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
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.NPCSystems;

import br.com.leonardson.taleshop.interaction.TraderMessageInteraction;

public class TraderInteractableSystem extends RefSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final ShopRegistry shopRegistry;

    public TraderInteractableSystem(ShopRegistry shopRegistry) {
        this.shopRegistry = shopRegistry;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(UUIDComponent.getComponentType(), NPCEntity.getComponentType());
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        // Role activation/loaded hooks run before rebinding a persisted shop trader.
        // AddedFromExternalSystem may also remove Frozen from world-generated NPCs.
        return Set.of(new SystemDependency<>(Order.AFTER, NPCSystems.AddedFromExternalSystem.class));
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
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

        TraderStationaryState.apply(commandBuffer, ref);

        LOGGER.atInfo().log("Bound trader interaction and freeze for shop %s (%s)", shop.name(), shop.traderUuid());
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }
}
