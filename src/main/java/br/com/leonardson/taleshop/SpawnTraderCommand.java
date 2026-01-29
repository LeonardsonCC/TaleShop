package br.com.leonardson.taleshop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import br.com.leonardson.taleshop.interaction.TraderMessageInteraction;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;
import javax.annotation.Nonnull;

public class SpawnTraderCommand extends AbstractPlayerCommand {
  private static final String DEFAULT_ROLE = "Klops_Merchant";
  private static final String TRADER_NAME = "Kweebec Trader";

  public SpawnTraderCommand() {
    super("spawntrader", "Spawns a stationary Klops merchant.");
  }

  @Override
  protected void execute(
      @Nonnull CommandContext context,
      @Nonnull Store<EntityStore> store,
      @Nonnull Ref<EntityStore> ref,
      @Nonnull PlayerRef playerRef,
      @Nonnull World world) {
    NPCPlugin npcPlugin = NPCPlugin.get();
    if (npcPlugin == null) {
      context.sendMessage(Message.raw("NPC system is not available."));
      return;
    }

    int roleIndex = npcPlugin.getIndex(DEFAULT_ROLE);
    if (roleIndex < 0) {
      context.sendMessage(Message.raw("Klops merchant role not found: " + DEFAULT_ROLE));
      return;
    }

    TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
    HeadRotation headRotationComponent = store.getComponent(ref, HeadRotation.getComponentType());
    if (transformComponent == null || headRotationComponent == null) {
      context.sendMessage(Message.raw("Could not determine spawn position. Shop created without a trader."));
      return;
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
              new DisplayNameComponent(Message.raw(TRADER_NAME)));
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
      context.sendMessage(Message.raw("Failed to spawn Klops merchant."));
      return;
    }

    UUIDComponent uuidComponent = store.getComponent(npcPair.first(), UUIDComponent.getComponentType());
    if (uuidComponent == null) {
      context.sendMessage(Message.raw("Trader spawned, but UUID was not available."));
      return;
    }

    context.sendMessage(Message.raw("Klops merchant spawned."));
  }
}
