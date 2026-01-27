package br.com.leonardson.taleshop.shop;

import br.com.leonardson.taleshop.interaction.TraderMessageInteraction;
import io.sentry.util.Random;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

public class TraderNpc {
    private static final List<String> ROLES = List.of(
            "Klops_Merchant" // for now, it's the only that not moves
            // "Feran_Civilian",
            // "Klops_Gentleman",
            // "Klops_Miner_Patrol",
            // "Outlander_Cultist",
            // "Skeleton_Pirate_Captain"
        );
    private final String ROLE = ROLES.get(new Random().nextInt(ROLES.size())); //
    private static final String DEFAULT_TRADER_NAME = "Trader";
    private String traderName = DEFAULT_TRADER_NAME;
    private String uuid;

    Ref<EntityStore> ref;
    NPCEntity npc;

    public TraderNpc(String name) {
        this.traderName = name;
    }

    @Nonnull
    public void spawn(@Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            throw new IllegalStateException("NPC system is not available.");
        }

        int roleIndex = npcPlugin.getIndex(ROLE);
        if (roleIndex < 0) {
            throw new IllegalStateException("Klops merchant role not found: " + ROLE);
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
                    this.ref = npcRef;
                    this.npc = npc;

                    entityStore.putComponent(npcRef, DisplayNameComponent.getComponentType(),
                            new DisplayNameComponent(Message.raw(traderName)));
                    applyInteractable(entityStore);
                    applyInvulnerable(npc, entityStore, npcRef);
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
