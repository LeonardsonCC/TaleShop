package br.com.leonardson.taleshop.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import br.com.leonardson.taleshop.TaleShop;
import br.com.leonardson.taleshop.player.PlayerIdentity;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.ui.ShopBuyerPage;
import br.com.leonardson.taleshop.shop.ui.TraderMenuPage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.annotation.Nonnull;

public class TraderMessageInteraction extends SimpleInstantInteraction {
    public static final String INTERACTION_ID = "TaleShop:TraderMessage";
    public static final String ROOT_INTERACTION_ID = "TaleShop:TraderRoot";
    public static final RootInteraction ROOT = new RootInteraction(ROOT_INTERACTION_ID, INTERACTION_ID);
    public static final BuilderCodec<TraderMessageInteraction> CODEC = BuilderCodec.builder(
        TraderMessageInteraction.class,
        TraderMessageInteraction::new,
        SimpleInstantInteraction.CODEC
    ).build();

    public TraderMessageInteraction(String id) {
        super(id);
    }

    protected TraderMessageInteraction() {
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref<EntityStore> ref = context.getEntity();
        if (ref == null || !ref.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Store<EntityStore> store = ref.getStore();
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerComponent == null || playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        TaleShop plugin = TaleShop.getInstance();
        if (plugin == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref<EntityStore> targetRef = resolveTargetRef(context);
        String traderUuid = resolveTraderUuid(store, targetRef);
        if (traderUuid == null || traderUuid.isBlank()) {
            playerRef.sendMessage(Message.raw("Shop not found for this trader."));
            return;
        }

        ShopRegistry shopRegistry = plugin.getShopRegistry();
        if (shopRegistry == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Shop shop = shopRegistry.findShopByTraderUuid(traderUuid);
        if (shop == null) {
            playerRef.sendMessage(Message.raw("Shop not found for this trader."));
            return;
        }

        String playerOwnerId = PlayerIdentity.resolveOwnerId(playerComponent);
        if (!shop.ownerId().equals(playerOwnerId)) {
            playerComponent.getPageManager().openCustomPage(ref, store, new ShopBuyerPage(playerRef, shop.ownerId(), shop.name()));
            return;
        }

        playerComponent.getPageManager().openCustomPage(ref, store, new TraderMenuPage(playerRef, shop.ownerId(), shop.name()));
    }

    @Nullable
    private static Ref<EntityStore> resolveTargetRef(@Nonnull InteractionContext context) {
        Object target = invokeFirst(
            context,
            "getTarget",
            "getTargetEntity",
            "getTargetRef",
            "getInteractionTarget",
            "getInteractedEntity",
            "getOtherEntity",
            "getNpc",
            "getEntityRef"
        );
        Ref<EntityStore> ref = castRef(target);
        if (ref != null) {
            return ref;
        }
        if (target != null) {
            Object refObj = invokeFirst(target, "getReference", "getRef", "getEntityRef");
            return castRef(refObj);
        }
        return null;
    }

    @Nullable
    private static Ref<EntityStore> castRef(@Nullable Object value) {
        if (value instanceof Ref<?> ref) {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> casted = (Ref<EntityStore>) ref;
            return casted;
        }
        return null;
    }

    @Nullable
    private static String resolveTraderUuid(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> targetRef) {
        if (targetRef == null || !targetRef.isValid()) {
            return null;
        }
        UUIDComponent uuidComponent = store.getComponent(targetRef, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return null;
        }
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

    @Nullable
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

    @Nullable
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
