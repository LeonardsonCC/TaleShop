package br.com.leonardson.taleshop.shop;

import java.lang.reflect.Method;

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
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

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
        
        // Apply freeze immediately when trader is added
        applyTraderFreeze(ref, store, commandBuffer);
        
        LOGGER.atInfo().log("Bound trader interaction and freeze for shop %s (%s)", shop.name(), shop.traderUuid());
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }
    
    /**
     * Applies all freeze-related components and states to a trader NPC.
     */
    private void applyTraderFreeze(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        // 1. Apply Frozen component
        commandBuffer.ensureComponent(ref, Frozen.getComponentType());
        
        // 2. Set MovementStates to idle
        setIdleMovementState(store, ref);
        
        // 3. Remove StepComponent if it exists
        removeStepComponent(store, ref);
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
            // If we can't set movement states, that's okay
        }
    }
    
    private static void setField(Class<?> clazz, Object instance, String fieldName, boolean value) {
        try {
            java.lang.reflect.Field field = clazz.getField(fieldName);
            field.setAccessible(true);
            field.setBoolean(instance, value);
        } catch (Exception ignored) {
        }
    }
    
    private static void removeStepComponent(Store<EntityStore> entityStore, Ref<EntityStore> npcRef) {
        Object componentType = tryLoadComponentType("com.hypixel.hytale.server.npc.components.StepComponent");
        if (componentType != null) {
            tryInvoke(entityStore, "removeComponentIfExists", npcRef, componentType);
            tryInvoke(entityStore, "tryRemoveComponent", npcRef, componentType);
        }
    }
    
    // Helper reflection methods
    private static Object tryLoadComponentType(String className) {
        try {
            Class<?> componentClass = Class.forName(className);
            Method method = findMethod(componentClass, "getComponentType");
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(null);
        } catch (Exception ignored) {
            return null;
        }
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
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
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
                    if (!params[i].isAssignableFrom(arg.getClass()) && !(params[i].isPrimitive() && isWrapper(params[i], arg.getClass()))) {
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
