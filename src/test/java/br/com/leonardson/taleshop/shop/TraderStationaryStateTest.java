package br.com.leonardson.taleshop.shop;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.npc.systems.NPCSystems;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TraderStationaryStateTest {
    @Test
    void clearsVelocityThatSurvivesHytalePersistenceAndPendingImpulses() {
        Velocity moving = new Velocity(new Vector3d(1, -2, 3));
        Velocity restored = Velocity.CODEC.decode(
                Velocity.CODEC.encode(moving, new ExtraInfo()), new ExtraInfo());
        assertEquals(moving.getVelocity(), restored.getVelocity());
        assertTrue(restored.getSpeed() > 0, "Freezing alone does not clear persisted velocity");
        restored.setClient(4, 5, 6);
        restored.addInstruction(new Vector3d(7, 8, 9), null, ChangeVelocityType.Add);

        TraderStationaryState.resetMotion(restored, new MovementStatesComponent());

        assertEquals(new Vector3d(), restored.getVelocity());
        assertEquals(new Vector3d(), restored.getClientVelocity());
        assertTrue(restored.getInstructions().isEmpty());
        Velocity reloaded = Velocity.CODEC.decode(
                Velocity.CODEC.encode(restored, new ExtraInfo()), new ExtraInfo());
        assertEquals(0, reloaded.getSpeed());
    }

    @Test
    void clearsLocomotionButPreservesPostureAndTheStateAwaitingNetworkUpdate() {
        MovementStatesComponent component = new MovementStatesComponent();
        MovementStates moving = component.getMovementStates();
        moving.walking = moving.running = moving.sprinting = moving.jumping = true;
        moving.falling = moving.fallingFar = moving.flying = moving.climbing = true;
        moving.swimming = moving.swimJumping = moving.mantling = moving.sliding = true;
        moving.rolling = moving.gliding = true;
        moving.sitting = moving.crouching = moving.inFluid = moving.onGround = true;
        MovementStates sent = new MovementStates(moving);
        component.setSentMovementStates(sent);

        TraderStationaryState.resetMotion(null, component);

        MovementStates expected = new MovementStates();
        expected.idle = expected.horizontalIdle = true;
        expected.sitting = expected.crouching = expected.inFluid = expected.onGround = true;
        assertEquals(expected, component.getMovementStates());
        assertSame(sent, component.getSentMovementStates());
        assertTrue(sent.walking);
        assertNotEquals(sent, component.getMovementStates(), "Tracker must still send idle to clients");
    }

    @Test
    void toleratesMissingComponentsAndRepeatedApplication() {
        assertDoesNotThrow(() -> TraderStationaryState.resetMotion(null, null));
        Velocity velocity = new Velocity(new Vector3d(1, 2, 3));
        MovementStatesComponent component = new MovementStatesComponent();
        TraderStationaryState.resetMotion(velocity, component);
        MovementStates idle = new MovementStates(component.getMovementStates());
        TraderStationaryState.resetMotion(velocity, component);
        assertEquals(idle, component.getMovementStates());
        assertEquals(0, velocity.getSpeed());
    }

    @Test
    void rebindsAfterNpcInitializationIncludingExternalUnfreeze() {
        var dependencies = new TraderInteractableSystem(null).getDependencies();
        assertTrue(dependencies.stream().anyMatch(dependency ->
                dependency instanceof SystemDependency<?, ?> system
                        && system.getSystemClass() == NPCSystems.AddedFromExternalSystem.class
                        && dependency.getOrder() == Order.AFTER));
    }
}
