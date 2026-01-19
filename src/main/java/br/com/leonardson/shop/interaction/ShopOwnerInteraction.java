package br.com.leonardson.shop.interaction;

import br.com.leonardson.Main;
import br.com.leonardson.shop.ShopNpcRegistry;
import br.com.leonardson.shop.ui.ShopOwnerPage;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class ShopOwnerInteraction extends SimpleInstantInteraction {
    public static final String INTERACTION_ID = "TaleShop:ShopOwner";
    public static final RootInteraction ROOT = new RootInteraction(INTERACTION_ID, INTERACTION_ID);
    public static final BuilderCodec<ShopOwnerInteraction> CODEC = BuilderCodec.builder(
            ShopOwnerInteraction.class,
            ShopOwnerInteraction::new,
            SimpleInstantInteraction.CODEC
    ).build();

    public ShopOwnerInteraction(String id) {
        super(id);
    }

    protected ShopOwnerInteraction() {
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        UUIDComponent npcUuidComponent = commandBuffer.getComponent(targetRef, UUIDComponent.getComponentType());
        if (npcUuidComponent == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        ShopNpcRegistry npcRegistry = Main.getInstance().getShopNpcRegistry();
        ShopNpcRegistry.ShopLink link = npcRegistry.getShopByNpc(npcUuidComponent.getUuid());
        if (link == null) {
            playerComponent.sendMessage(Message.raw("This NPC is not linked to a shop."));
            context.getState().state = InteractionState.Failed;
            return;
        }

        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (!link.ownerId().equals(playerRef.getUuid().toString())) {
            playerComponent.sendMessage(Message.raw("Only the shop owner can manage this shop."));
            context.getState().state = InteractionState.Failed;
            return;
        }

        npcRegistry.beginEdit(link.shopId(), playerRef.getUuid());
        ShopOwnerPage page = new ShopOwnerPage(playerRef, link.shopId(), link.shopName());
        playerComponent.getPageManager().openCustomPage(ref, ref.getStore(), page);
        playerComponent.sendMessage(Message.raw("Shop setup opened. Use Add to open item slots."));
    }
}
