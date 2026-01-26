package br.com.leonardson.taleshop.shop;

import br.com.leonardson.taleshop.interaction.TraderMessageInteraction;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;

public class TraderNpc {
    private static final String DEFAULT_ROLE = "Klops_Merchant";
    private static final String DEFAULT_TRADER_NAME = "Trader";

    private String traderName = DEFAULT_TRADER_NAME;

    public TraderNpc(String name) {
        this.traderName = name;
    }

    public void spawn(@Nonnull Store<EntityStore> store,
                      @Nonnull Ref<EntityStore> ref) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            throw new IllegalStateException("NPC system is not available.");
        }

        int roleIndex = npcPlugin.getIndex(DEFAULT_ROLE);
        if (roleIndex < 0) {
            throw new IllegalStateException("Klops merchant role not found: " + DEFAULT_ROLE);
        }

        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation headRotationComponent = store.getComponent(ref, HeadRotation.getComponentType());
        if (transformComponent == null || headRotationComponent == null) {
            throw new IllegalStateException("Could not determine spawn position. Shop created without a trader.");
        }

        TransformComponent spawnTransform = new TransformComponent(
                new Vector3d(transformComponent.getPosition()),
                new Vector3f(headRotationComponent.getRotation()));
        Vector3d spawnPosition = new Vector3d(spawnTransform.getPosition()).add(0.0, 0.0, 1.5);
        Vector3f spawnRotation = new Vector3f(spawnTransform.getRotation());

        Pair<Ref<EntityStore>, NPCEntity> npcPair = npcPlugin.spawnEntity(
                store,
                roleIndex,
                spawnPosition,
                spawnRotation,
                null,
                (npc, npcRef, entityStore) -> {
                    entityStore.putComponent(npcRef, DisplayNameComponent.getComponentType(),
                            new DisplayNameComponent(Message.raw(traderName)));
                    entityStore.putComponent(npcRef, Interactable.getComponentType(), Interactable.INSTANCE);
                    Interactions interactions = entityStore.getComponent(npcRef, Interactions.getComponentType());
                    if (interactions == null) {
                        interactions = new Interactions();
                    }
                    interactions.setInteractionId(InteractionType.Use, TraderMessageInteraction.ROOT_INTERACTION_ID);
                    interactions.setInteractionHint("Trade");
                    entityStore.putComponent(npcRef, Interactions.getComponentType(), interactions);
                });

        if (npcPair == null || npcPair.first() == null || !npcPair.first().isValid()) {
            throw new IllegalStateException("Failed to spawn Klops merchant.");
        }

        UUIDComponent uuidComponent = store.getComponent(npcPair.first(), UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            throw new IllegalStateException("Trader spawned, but UUID was not available.");
        }
    }
}
