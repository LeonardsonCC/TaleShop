package br.com.leonardson.taleshop.shop;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.StepComponent;

/** Stationary state shared by fresh spawns and persisted shop traders. */
final class TraderStationaryState {
    private TraderStationaryState() {
    }

    static void prepareSpawn(Holder<EntityStore> holder) {
        // Prevent behaviour and steering from running before postSpawn binds the shop.
        holder.ensureComponent(Frozen.getComponentType());
    }

    static void apply(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        accessor.putComponent(ref, Frozen.getComponentType(), Frozen.get());
        // Step explicitly bypasses Frozen in Hytale's behaviour/steering systems.
        accessor.tryRemoveComponent(ref, StepComponent.getComponentType());
        resetMotion(accessor.getComponent(ref, Velocity.getComponentType()),
                accessor.getComponent(ref, MovementStatesComponent.getComponentType()));
    }

    static void resetMotion(Velocity velocity, MovementStatesComponent movement) {
        // Velocity is persisted. Frozen skips ComputeVelocitySystem, so an old
        // nonzero value otherwise survives indefinitely after a chunk reload.
        if (velocity != null) {
            velocity.setZero();
            velocity.setClient(0, 0, 0);
            velocity.getInstructions().clear();
        }
        if (movement != null) {
            var states = movement.getMovementStates();
            states.idle = true;
            states.horizontalIdle = true;
            states.walking = false;
            states.running = false;
            states.sprinting = false;
            states.jumping = false;
            states.falling = false;
            states.fallingFar = false;
            states.flying = false;
            states.climbing = false;
            states.swimming = false;
            states.swimJumping = false;
            states.mantling = false;
            states.sliding = false;
            states.rolling = false;
            states.gliding = false;
            // Keep posture/environment and sentMovementStates: the tracker must
            // still detect the change and send the idle state to existing clients.
        }
    }
}
