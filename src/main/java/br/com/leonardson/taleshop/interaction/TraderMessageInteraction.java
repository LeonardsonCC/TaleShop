package br.com.leonardson.taleshop.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
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
        Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        playerComponent.sendMessage(Message.raw("Trader: Hello there."));
    }
}
