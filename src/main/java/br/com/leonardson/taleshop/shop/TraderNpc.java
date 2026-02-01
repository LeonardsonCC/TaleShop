package br.com.leonardson.taleshop.shop;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import br.com.leonardson.taleshop.interaction.TraderMessageInteraction;
import it.unimi.dsi.fastutil.Pair;

public class TraderNpc {
    private String role = "Klops_Merchant"; //
    private static final String DEFAULT_TRADER_NAME = "Trader";
    private String traderName = DEFAULT_TRADER_NAME;
    private String uuid;

    Ref<EntityStore> ref;
    NPCEntity npc;

    public TraderNpc(String name, String role) {
        this.traderName = name;
        this.role = role;
    }

    @Nonnull
    public void spawn(@Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            throw new IllegalStateException("NPC system is not available.");
        }

        int roleIndex = npcPlugin.getIndex(role);
        if (roleIndex < 0) {
            throw new IllegalStateException("Klops merchant role not found: " + role);
        }

        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (transformComponent == null) {
            throw new IllegalStateException("Could not determine spawn position. Shop created without a trader.");
        }

        TransformComponent spawnTransform = new TransformComponent(
                new Vector3d(transformComponent.getPosition()),
                new Vector3f(transformComponent.getRotation()));
        Vector3d spawnPosition = new Vector3d(spawnTransform.getPosition());
        Vector3f spawnRotation = new Vector3f(spawnTransform.getRotation());

        Pair<Ref<EntityStore>, NPCEntity> npcPair = npcPlugin.spawnEntity(
                store,
                roleIndex,
                spawnPosition,
                spawnRotation,
                null,
                (npc, npcRef, entityStore) -> {
                    this.ref = npcRef;
                    this.npc = npc;

                    entityStore.putComponent(npcRef, DisplayNameComponent.getComponentType(),
                            new DisplayNameComponent(Message.raw(traderName)));
                    applyInteractable(entityStore);
                    applyInvulnerable(entityStore, npcRef);
                    applyFreeze(entityStore, npcRef);
                });

        if (npcPair == null || npcPair.first() == null || !npcPair.first().isValid()) {
            throw new IllegalStateException("Failed to spawn Klops merchant.");
        }

        UUIDComponent uuidComponent = store.getComponent(npcPair.first(), UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            throw new IllegalStateException("Trader spawned, but UUID was not available.");
        }

        UUID uuid = uuidComponent.getUuid();
        String traderUuid = uuid == null ? null : uuid.toString();
        if (traderUuid == null || traderUuid.isBlank()) {
            throw new IllegalStateException("Trader spawned, but UUID was not available.");
        }

        this.uuid = traderUuid;

        this.ref = npcPair.first();
        this.npc = npcPair.second();
        
        setDisplayName(store, this.ref, traderName);
    }
    
    /**
     * Sets the display name for an NPC entity.
     * This method replicates the logic from EntitySupport.setDisplayName()
     * which sets both the DisplayNameComponent and the Nameplate component.
     */
    private static void setDisplayName(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String displayName) {
        if (!ref.isValid()) {
            return;
        }
        
        // Set the DisplayNameComponent
        store.putComponent(ref, DisplayNameComponent.getComponentType(), 
                new DisplayNameComponent(Message.raw(displayName)));
        
        // Set the Nameplate component - this is what actually displays above the NPC!
        Nameplate nameplateComponent = store.ensureAndGetComponent(ref, Nameplate.getComponentType());
        nameplateComponent.setText(displayName);
    }
    
    public String getUuid(Store<EntityStore> store) {
        if (this.uuid != null && !this.uuid.isBlank()) {
            return this.uuid;
        }
        if (this.npc == null) {
            return null;
        }
        UUIDComponent uuidComponent = store.getComponent(this.ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return null;
        }
        UUID uuid = uuidComponent.getUuid();
        return uuid == null ? null : uuid.toString();
    }

    public boolean despawn(@Nonnull Store<EntityStore> store) {
        boolean removed = false;
        if (this.npc != null) {
            this.npc.despawn();
            removed = true;
        }
        if (!removed && this.ref != null) {
            removed = removeRef(store, this.ref);
        }
        return removed;
    }

    public static boolean despawnByUuid(@Nonnull Store<EntityStore> store, @Nonnull String traderUuid) {
        if (traderUuid.isBlank()) {
            return false;
        }
        UUID uuid = parseUuid(traderUuid);
        Ref<EntityStore> ref = resolveRef(store, uuid);
        if (ref != null) {
            return removeRef(store, ref);
        }

        Universe universe = Universe.get();
        if (universe != null) {
            boolean removed = false;
            for (World world : universe.getWorlds().values()) {
                EntityStore entityStore = world.getEntityStore();
                Store<EntityStore> worldStore = entityStore.getStore();
                Ref<EntityStore> worldRef = resolveRef(worldStore, uuid);
                if (worldRef == null) {
                    continue;
                }
                if (world.isInThread()) {
                    removed = removeRef(worldStore, worldRef) || removed;
                } else {
                    world.execute(() -> removeRef(worldStore, worldRef));
                    removed = true;
                }
            }
            if (removed) {
                return true;
            }
        }
        return false;
    }

    private static void applyInvulnerable(Store<EntityStore> entityStore, Ref<EntityStore> npcRef) {
        applyInvulnerableComponent(entityStore, npcRef);
    }

    private static void applyFreeze(Store<EntityStore> entityStore, Ref<EntityStore> npcRef) {
        entityStore.ensureComponent(npcRef, Frozen.getComponentType());
    }

    public void applyInteractable(Store<EntityStore> entityStore) {
        if (this.ref == null) {
            return;
        }

        // make sure to not duplicate
        if (entityStore.getComponent(this.ref, Interactable.getComponentType()) != null) {
            entityStore.removeComponentIfExists(this.ref, Interactable.getComponentType());
        }

        entityStore.putComponent(this.ref, Interactable.getComponentType(), Interactable.INSTANCE);
        Interactions interactions = entityStore.getComponent(this.ref, Interactions.getComponentType());
        if (interactions == null) {
            interactions = new Interactions();
        }
        interactions.setInteractionId(InteractionType.Use, TraderMessageInteraction.ROOT_INTERACTION_ID);
        interactions.setInteractionHint("Trade");
        entityStore.putComponent(this.ref, Interactions.getComponentType(), interactions);
    }

    private static void applyInvulnerableComponent(Store<EntityStore> entityStore, Ref<EntityStore> npcRef) {
        entityStore.ensureComponent(npcRef, Invulnerable.getComponentType());
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Ref<EntityStore> resolveRef(Store<EntityStore> store, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null) {
            return null;
        }
        return entityStore.getRefFromUUID(uuid);
    }

    private static boolean removeRef(Store<EntityStore> store, Ref<?> ref) {
        if (ref == null || !ref.isValid()) {
            return false;
        }
        store.removeEntity(ref, RemoveReason.REMOVE);
        return true;
    }
}
