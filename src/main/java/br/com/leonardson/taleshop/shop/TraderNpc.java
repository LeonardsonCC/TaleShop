package br.com.leonardson.taleshop.shop;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
                    applyInvulnerable(npc, entityStore, npcRef);
                    applyFreeze(npc, entityStore, npcRef);
                });

        if (npcPair == null || npcPair.first() == null || !npcPair.first().isValid()) {
            throw new IllegalStateException("Failed to spawn Klops merchant.");
        }

        UUIDComponent uuidComponent = store.getComponent(npcPair.first(), UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            throw new IllegalStateException("Trader spawned, but UUID was not available.");
        }

        String traderUuid = resolveUuid(uuidComponent);
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
        Object value = invokeFirst(uuidComponent, "getUuid", "getUUID", "getUniqueId", "getId");
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        return null;
    }

    public boolean despawn(@Nonnull Store<EntityStore> store) {
        boolean removed = false;
        if (this.npc != null) {
            removed = tryInvoke(this.npc, "despawn")
                    || tryInvoke(this.npc, "remove")
                    || tryInvoke(this.npc, "delete")
                    || tryInvoke(this.npc, "destroy");
        }
        if (!removed && this.ref != null) {
            removed = tryRemoveRef(store, this.ref);
        }
        return removed;
    }

    public static boolean despawnByUuid(@Nonnull Store<EntityStore> store, @Nonnull String traderUuid) {
        if (traderUuid.isBlank()) {
            return false;
        }
        UUID uuid = parseUuid(traderUuid);
        Object ref = tryResolveRef(store, uuid, traderUuid);
        if (ref instanceof Ref<?> resolvedRef) {
            return tryRemoveRef(store, resolvedRef);
        }

        Universe universe = Universe.get();
        if (universe != null) {
            boolean removed = false;
            for (World world : universe.getWorlds().values()) {
                EntityStore entityStore = world.getEntityStore();
                Store<EntityStore> worldStore = entityStore.getStore();
                Object worldRef = tryResolveRef(worldStore, uuid, traderUuid);
                if (!(worldRef instanceof Ref<?> resolvedWorldRef)) {
                    continue;
                }
                if (world.isInThread()) {
                    removed = tryRemoveRef(worldStore, resolvedWorldRef) || removed;
                } else {
                    world.execute(() -> tryRemoveRef(worldStore, resolvedWorldRef));
                    removed = true;
                }
            }
            if (removed) {
                return true;
            }
        }

        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            return false;
        }
        return tryInvoke(npcPlugin, "despawnEntity", uuid)
                || tryInvoke(npcPlugin, "removeEntity", uuid)
                || tryInvoke(npcPlugin, "deleteEntity", uuid)
                || tryInvoke(npcPlugin, "despawnEntity", traderUuid)
                || tryInvoke(npcPlugin, "removeEntity", traderUuid)
                || tryInvoke(npcPlugin, "deleteEntity", traderUuid)
                || tryInvoke(npcPlugin, "despawnEntity", store, uuid)
                || tryInvoke(npcPlugin, "removeEntity", store, uuid)
                || tryInvoke(npcPlugin, "deleteEntity", store, uuid);
    }

    private static void applyInvulnerable(Object npc, Store<EntityStore> entityStore, Ref<EntityStore> npcRef) {
        if (npc != null) {
            if (tryInvoke(npc, "setInvulnerable", true)
                    || tryInvoke(npc, "setInvincible", true)
                    || tryInvoke(npc, "setImmortal", true)
                    || tryInvoke(npc, "setDamageable", false)
                    || tryInvoke(npc, "setCanBeDamaged", false)) {
                return;
            }
        }

        applyInvulnerableComponent(entityStore, npcRef);
    }

    private static void applyFreeze(Object npc, Store<EntityStore> entityStore, Ref<EntityStore> npcRef) {
        applyFreezeInternal(entityStore, npcRef);
    }
    
    /**
     * Internal method that applies all freeze-related components and states.
     * This method can be called repeatedly to ensure the NPC stays frozen.
     */
    private static void applyFreezeInternal(Store<EntityStore> entityStore, Ref<EntityStore> npcRef) {
        // 1. Apply Frozen component
        entityStore.ensureComponent(npcRef, Frozen.getComponentType());
        
        // 2. Set MovementStates to idle to stop the walking animation
        setIdleMovementState(entityStore, npcRef);
        
        // 3. Remove StepComponent to prevent ticking when frozen
        removeStepComponent(entityStore, npcRef);
    }
    
    private static void setIdleMovementState(Store<EntityStore> entityStore, Ref<EntityStore> npcRef) {
        try {
            // Load the MovementStatesComponent class
            Class<?> movementStatesComponentClass = Class.forName("com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent");
            Object componentType = tryLoadComponentType("com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent");
            
            if (componentType == null) {
                return;
            }
            
            // Get the current MovementStatesComponent
            Object movementStatesComponent = tryInvokeForResult(entityStore, "getComponent", npcRef, componentType);
            if (movementStatesComponent == null) {
                return;
            }
            
            // Get the MovementStates object from the component
            Method getMovementStatesMethod = findMethod(movementStatesComponentClass, "getMovementStates");
            if (getMovementStatesMethod == null) {
                return;
            }
            getMovementStatesMethod.setAccessible(true);
            Object movementStates = getMovementStatesMethod.invoke(movementStatesComponent);
            
            if (movementStates == null) {
                return;
            }
            
            // Set all movement states to false except idle
            Class<?> movementStatesClass = movementStates.getClass();
            setField(movementStatesClass, movementStates, "idle", true);
            setField(movementStatesClass, movementStates, "horizontalIdle", true);
            setField(movementStatesClass, movementStates, "walking", false);
            setField(movementStatesClass, movementStates, "running", false);
            setField(movementStatesClass, movementStates, "sprinting", false);
            setField(movementStatesClass, movementStates, "jumping", false);
            setField(movementStatesClass, movementStates, "falling", false);
            setField(movementStatesClass, movementStates, "flying", false);
            setField(movementStatesClass, movementStates, "climbing", false);
            setField(movementStatesClass, movementStates, "swimming", false);
            
        } catch (Exception ignored) {
            // If we can't set movement states, that's okay - the Frozen component should still work
        }
    }
    
    private static void setField(Class<?> clazz, Object instance, String fieldName, boolean value) {
        try {
            java.lang.reflect.Field field = clazz.getField(fieldName);
            field.setAccessible(true);
            field.setBoolean(instance, value);
        } catch (Exception ignored) {
            // Field not found or can't be set
        }
    }
    
    private static void removeStepComponent(Store<EntityStore> entityStore, Ref<EntityStore> npcRef) {
        // The StepComponent allows frozen NPCs to still tick at a special rate
        // Removing it ensures the NPC completely stops ticking and moving
        Object componentType = tryLoadComponentType("com.hypixel.hytale.server.npc.components.StepComponent");
        if (componentType != null) {
            tryInvoke(entityStore, "removeComponentIfExists", npcRef, componentType);
            tryInvoke(entityStore, "tryRemoveComponent", npcRef, componentType);
        }
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
        String[] componentCandidates = new String[] {
                "com.hypixel.hytale.server.core.modules.entity.component.Invulnerable",
                "com.hypixel.hytale.server.core.modules.entity.component.InvulnerableComponent",
                "com.hypixel.hytale.server.core.modules.entity.component.Invincibility",
                "com.hypixel.hytale.server.core.modules.entity.component.Immortal",
                "com.hypixel.hytale.server.core.modules.entity.component.ImmortalComponent"
        };
        for (String className : componentCandidates) {
            Object componentType = tryLoadComponentType(className);
            if (componentType == null) {
                continue;
            }
            Object componentInstance = tryCreateComponentInstance(className);
            if (componentInstance == null) {
                continue;
            }
            if (tryPutComponent(entityStore, npcRef, componentType, componentInstance)) {
                return;
            }
        }
    }

    private static String resolveUuid(UUIDComponent uuidComponent) {
        Object value = invokeFirst(uuidComponent, "getUuid", "getUUID", "getUniqueId", "getId");
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value == null) {
            return null;
        }
        String resolved = String.valueOf(value);
        return resolved.isBlank() ? null : resolved;
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

    private static Object tryResolveRef(Store<EntityStore> store, UUID uuid, String uuidText) {
        if (uuid != null) {
            EntityStore entityStore = store.getExternalData();
            if (entityStore != null) {
                Ref<EntityStore> direct = entityStore.getRefFromUUID(uuid);
                if (direct != null) {
                    return direct;
                }
            }
        }
        if (uuid != null) {
            Object ref = tryInvokeForResult(store, "getEntity", uuid);
            if (ref != null) {
                return ref;
            }
            ref = tryInvokeForResult(store, "getEntityRef", uuid);
            if (ref != null) {
                return ref;
            }
        }
        if (uuidText != null && !uuidText.isBlank()) {
            Object ref = tryInvokeForResult(store, "getEntity", uuidText);
            if (ref != null) {
                return ref;
            }
            return tryInvokeForResult(store, "getEntityRef", uuidText);
        }
        return null;
    }

    private static boolean tryRemoveRef(Store<EntityStore> store, Ref<?> ref) {
        if (tryInvoke(store, "removeEntity", ref, RemoveReason.REMOVE)
                || tryInvoke(store, "despawnEntity", ref)
                || tryInvoke(store, "removeEntity", ref)
                || tryInvoke(store, "deleteEntity", ref)
                || tryInvoke(store, "destroyEntity", ref)) {
            return true;
        }

        EntityStore entityStore = store.getExternalData();
        return entityStore != null && (tryInvoke(entityStore, "removeEntity", ref, RemoveReason.REMOVE)
                || tryInvoke(entityStore, "despawnEntity", ref)
                || tryInvoke(entityStore, "removeEntity", ref)
                || tryInvoke(entityStore, "deleteEntity", ref)
                || tryInvoke(entityStore, "destroyEntity", ref));
    }

    private static Object tryLoadComponentType(String className) {
        try {
            Class<?> componentClass = Class.forName(className);
            Method method = findMethod(componentClass, "getComponentType");
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(null);
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Object tryCreateComponentInstance(String className) {
        try {
            Class<?> componentClass = Class.forName(className);
            try {
                Object instance = componentClass.getField("INSTANCE").get(null);
                if (instance != null) {
                    return instance;
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // fall through
            }
            return componentClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean tryPutComponent(Store<EntityStore> store, Ref<EntityStore> ref, Object componentType,
            Object component) {
        return tryInvoke(store, "putComponent", ref, componentType, component);
    }

    private static Object tryInvokeForResult(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        Method method = findMethodByArgs(target.getClass(), methodName, args);
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static boolean tryInvoke(Object target, String methodName, Object... args) {
        if (target == null) {
            return false;
        }
        Method method = findMethodByArgs(target.getClass(), methodName, args);
        if (method == null) {
            return false;
        }
        try {
            method.setAccessible(true);
            method.invoke(target, args);
            return true;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return false;
        }
    }

    private static Method findMethodByArgs(Class<?> type, String name, Object... args) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != args.length) {
                    continue;
                }
                boolean matches = true;
                for (int i = 0; i < params.length; i++) {
                    Object arg = args[i];
                    if (arg == null) {
                        continue;
                    }
                    if (!params[i].isAssignableFrom(arg.getClass())
                            && !(params[i].isPrimitive() && isWrapper(params[i], arg.getClass()))) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean isWrapper(Class<?> primitiveType, Class<?> wrapperType) {
        return (primitiveType == boolean.class && wrapperType == Boolean.class)
                || (primitiveType == int.class && wrapperType == Integer.class)
                || (primitiveType == long.class && wrapperType == Long.class)
                || (primitiveType == double.class && wrapperType == Double.class)
                || (primitiveType == float.class && wrapperType == Float.class)
                || (primitiveType == short.class && wrapperType == Short.class)
                || (primitiveType == byte.class && wrapperType == Byte.class)
                || (primitiveType == char.class && wrapperType == Character.class);
    }

    private static Object invokeFirst(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            Method method = findMethod(target.getClass(), methodName);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(target);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
