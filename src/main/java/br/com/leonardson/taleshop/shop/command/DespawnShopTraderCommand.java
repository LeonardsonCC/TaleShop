package br.com.leonardson.taleshop.shop.command;

import br.com.leonardson.taleshop.player.PlayerIdentity;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.TraderNpc;
import br.com.leonardson.taleshop.interaction.TraderMessageInteraction;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class DespawnShopTraderCommand extends AbstractShopCommand {
    DefaultArg<String> argName;
    public DespawnShopTraderCommand(ShopRegistry shopRegistry) {
        super("despawn", "Despawn shop trader", shopRegistry);

        this.argName = this.withDefaultArg("name", "shop name", ArgTypes.STRING, "Shop", "Shop as default");
    }

    @Override
    protected void execute(@NotNull CommandContext ctx, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref,
            @NotNull PlayerRef playerRef, @NotNull World world) {
        String name = argName.get(ctx);
        if (name.isBlank()) {
            throw new IllegalArgumentException("Usage: /shop npc despawn <shop name>");
        }

        Player player = ctx.senderAs(Player.class);
        String ownerId = PlayerIdentity.resolveOwnerId(player);

        Shop shop = shopRegistry.getShop(ownerId, name);

        boolean removed = false;
        boolean attempted = false;
        String existingTrader = shopRegistry.getTraderUuid(ownerId, shop.name());
        if (existingTrader != null && !existingTrader.isBlank()) {
            attempted = true;
            removed = TraderNpc.despawnByUuid(store, existingTrader) || removed;
        }

        if (!removed) {
            Ref<EntityStore> fallbackRef = resolveTraderRefByName(store, shop.name());
            if (fallbackRef == null) {
                fallbackRef = resolveTraderRefByName(store, shop.name() + " Trader");
            }
            if (fallbackRef != null) {
                attempted = true;
                removed = tryRemoveRef(store, fallbackRef) || removed;
            }
        }

        if (!removed) {
            List<Ref<EntityStore>> interactionRefs = resolveTraderRefsByInteraction(store);
            if (interactionRefs.size() == 1) {
                attempted = true;
                removed = tryRemoveRef(store, interactionRefs.get(0)) || removed;
            }
        }

        if (attempted) {
            shopRegistry.clearTraderUuid(ownerId, shop.name());
        }

        if (removed) {
            ctx.sendMessage(Message.raw("Trader despawned for " + shop.name() + "."));
        } else {
            String uuidInfo = existingTrader == null || existingTrader.isBlank()
                    ? "none"
                    : existingTrader;
            int interactionMatches = resolveTraderRefsByInteraction(store).size();
            ctx.sendMessage(Message.raw("No trader found for " + shop.name()
                    + ". UUID: " + uuidInfo + " | Interaction matches: " + interactionMatches));
        }
    }

    private Ref<EntityStore> resolveTraderRefByName(Store<EntityStore> store, String expectedName) {
        for (Ref<EntityStore> ref : resolveEntityRefs(store)) {
            DisplayNameComponent displayName = store.getComponent(ref, DisplayNameComponent.getComponentType());
            if (displayName == null) {
                continue;
            }
            String name = resolveDisplayName(displayName);
            if (name == null) {
                continue;
            }
            if (matchesName(name, expectedName)) {
                return ref;
            }
        }
        return null;
    }

    private boolean matchesName(String name, String expectedName) {
        if (name.equalsIgnoreCase(expectedName)) {
            return true;
        }
        return name.toLowerCase().contains(expectedName.toLowerCase());
    }

    private List<Ref<EntityStore>> resolveTraderRefsByInteraction(Store<EntityStore> store) {
        List<Ref<EntityStore>> matches = new ArrayList<>();
        for (Ref<EntityStore> ref : resolveEntityRefs(store)) {
            Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
            if (interactions == null) {
                continue;
            }
            String id = resolveInteractionId(interactions);
            if (id == null) {
                continue;
            }
            if (TraderMessageInteraction.ROOT_INTERACTION_ID.equals(id)) {
                matches.add(ref);
            }
        }
        return matches;
    }

    private String resolveInteractionId(Interactions interactions) {
        Object value = firstNonNull(
                tryInvokeForResult(interactions, "getInteractionId", InteractionType.Use),
                tryInvokeForResult(interactions, "getInteraction", InteractionType.Use)
        );
        if (value == null) {
            return null;
        }
        if (value instanceof String id) {
            return id;
        }
        Object id = firstNonNull(
                tryInvokeForResult(value, "getId"),
                tryInvokeForResult(value, "getName"),
                tryInvokeForResult(value, "id"),
                tryInvokeForResult(value, "name")
        );
        if (id instanceof String resolved) {
            return resolved;
        }
        String resolved = String.valueOf(value);
        return resolved.isBlank() ? null : resolved;
    }

    private String resolveDisplayName(DisplayNameComponent component) {
        Object message = firstNonNull(
                tryInvokeForResult(component, "getDisplayName"),
                tryInvokeForResult(component, "getName"),
                tryInvokeForResult(component, "getMessage"),
                tryInvokeForResult(component, "getValue")
        );
        if (message == null) {
            return null;
        }
        Object text = firstNonNull(
                tryInvokeForResult(message, "getPlain"),
                tryInvokeForResult(message, "getPlainText"),
                tryInvokeForResult(message, "getText"),
                tryInvokeForResult(message, "getRaw"),
                tryInvokeForResult(message, "getContent"),
                tryInvokeForResult(message, "getString"),
                tryInvokeForResult(message, "asString")
        );
        if (text instanceof String resolved) {
            return resolved;
        }
        String value = String.valueOf(message);
        return value.isBlank() ? null : value;
    }

    private List<Ref<EntityStore>> resolveEntityRefs(Store<EntityStore> store) {
        Object result = firstNonNull(
                tryInvokeForResult(store, "getEntities"),
                tryInvokeForResult(store, "getEntityRefs"),
                tryInvokeForResult(store, "getAllEntities"),
                tryInvokeForResult(store, "getAllEntityRefs"),
                tryInvokeForResult(store, "getEntitiesView"),
                tryInvokeForResult(store, "getRefs")
        );
        if (result == null) {
            return List.of();
        }

        List<Ref<EntityStore>> refs = new ArrayList<>();
        if (result instanceof Stream<?> stream) {
            stream.forEach(value -> addRefFromValue(refs, value));
            return refs;
        }
        if (result instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                addRefFromValue(refs, value);
            }
            return refs;
        }
        if (result.getClass().isArray()) {
            Object[] array = (Object[]) result;
            for (Object value : array) {
                addRefFromValue(refs, value);
            }
            return refs;
        }
        if (result instanceof Collection<?> collection) {
            for (Object value : collection) {
                addRefFromValue(refs, value);
            }
            return refs;
        }

        addRefFromValue(refs, result);
        return refs;
    }

    private void addRefFromValue(List<Ref<EntityStore>> refs, Object value) {
        if (value instanceof Ref<?> ref) {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> entityRef = (Ref<EntityStore>) ref;
            refs.add(entityRef);
            return;
        }
        if (value != null) {
            Object resolved = firstNonNull(
                    tryInvokeForResult(value, "getRef"),
                    tryInvokeForResult(value, "getReference"),
                    tryInvokeForResult(value, "getEntityRef")
            );
            if (resolved instanceof Ref<?> ref) {
                @SuppressWarnings("unchecked")
                Ref<EntityStore> entityRef = (Ref<EntityStore>) ref;
                refs.add(entityRef);
            }
        }
    }

    private boolean tryRemoveRef(Store<EntityStore> store, Ref<?> ref) {
        if (tryInvoke(store, "despawnEntity", ref)
                || tryInvoke(store, "removeEntity", ref)
                || tryInvoke(store, "deleteEntity", ref)
                || tryInvoke(store, "destroyEntity", ref)) {
            return true;
        }

        EntityStore entityStore = store.getExternalData();
        return entityStore != null && (tryInvoke(entityStore, "despawnEntity", ref)
                || tryInvoke(entityStore, "removeEntity", ref)
                || tryInvoke(entityStore, "deleteEntity", ref)
                || tryInvoke(entityStore, "destroyEntity", ref));
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object tryInvokeForResult(Object target, String methodName, Object... args) {
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

    private boolean tryInvoke(Object target, String methodName, Object... args) {
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

    private Method findMethodByArgs(Class<?> type, String name, Object... args) {
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

    private boolean isWrapper(Class<?> primitiveType, Class<?> wrapperType) {
        return (primitiveType == boolean.class && wrapperType == Boolean.class)
                || (primitiveType == int.class && wrapperType == Integer.class)
                || (primitiveType == long.class && wrapperType == Long.class)
                || (primitiveType == double.class && wrapperType == Double.class)
                || (primitiveType == float.class && wrapperType == Float.class)
                || (primitiveType == short.class && wrapperType == Short.class)
                || (primitiveType == byte.class && wrapperType == Byte.class)
                || (primitiveType == char.class && wrapperType == Character.class);
    }
}
