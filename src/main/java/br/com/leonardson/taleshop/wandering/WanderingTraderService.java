package br.com.leonardson.taleshop.wandering;

import br.com.leonardson.taleshop.shop.TraderNpc;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import org.joml.Vector3d;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Per-world random spawn, encounter lifecycle, and purchase validation. */
public final class WanderingTraderService implements AutoCloseable {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final WanderingTraderConfig config;
    private final Path dataDirectory;
    private final Map<String, WanderingTraderEncounter> encounters = new ConcurrentHashMap<>();
    private final Map<String, World> encounterWorlds = new ConcurrentHashMap<>();
    private final Map<String, Long> nextAttemptAt = new ConcurrentHashMap<>();
    private final Map<String, Long> remainingLifetime = new ConcurrentHashMap<>();
    private final Map<String, Long> lastCheckpointAt = new ConcurrentHashMap<>();
    private final Set<String> runningWorlds = ConcurrentHashMap.newKeySet();
    private volatile List<WanderingTradeDefinition> activeTrades = List.of();
    private volatile boolean stopped;

    public WanderingTraderService(WanderingTraderConfig config, Path dataDirectory) {
        this.config = config;
        this.dataDirectory = dataDirectory;
    }

    public void start() {
        if (config.enabled) {
            List<WanderingTradeDefinition> valid = new ArrayList<>();
            for (var trade : config.validatedTrades()) {
                if (ItemModule.exists(trade.inputItemId()) && ItemModule.exists(trade.outputItemId())) valid.add(trade);
                else LOGGER.atWarning().log("Ignoring wandering trader offer %s because an item ID is not registered", trade.id());
            }
            activeTrades = List.copyOf(valid);
            if (activeTrades.isEmpty()) LOGGER.atWarning().log("Wandering trader is enabled but has no valid offers");
        }
        Universe universe = Universe.get();
        if (universe != null) for (World world : universe.getWorlds().values()) startWorld(world);
    }

    public void startWorld(World world) {
        if (stopped || !runningWorlds.add(world.getName())) return;
        try {
            WanderingTraderStateStorage stateFile = storage(world);
            WanderingTraderStateStorage.State state = stateFile.load();
            if (!config.enabled || activeTrades.isEmpty()) {
                world.execute(() -> clearSavedTraders(world, state));
                runningWorlds.remove(world.getName());
                return;
            }
            nextAttemptAt.put(world.getName(), System.currentTimeMillis() + Math.max(0,
                    state.nextAttemptAtMillis > 0 ? state.nextAttemptAtMillis : config.attemptIntervalSeconds * 1000L));
            lastCheckpointAt.put(world.getName(), System.currentTimeMillis());
            if (state.encounter != null) {
                List<WanderingTradeDefinition> definitions = new ArrayList<>();
                Map<String, Integer> remaining = new java.util.HashMap<>();
                for (var row : state.encounter.offers) {
                    definitions.add(new WanderingTradeDefinition(row.id, row.inputItemId, row.inputQuantity,
                            row.outputItemId, row.outputQuantity, row.maxUses));
                    remaining.put(row.id, row.remaining);
                }
                WanderingTraderEncounter restored = WanderingTraderEncounter.restore(state.encounter.id,
                        state.encounter.npcUuid, System.currentTimeMillis() + state.encounter.remainingLifetimeMillis,
                        definitions, remaining);
                encounters.put(world.getName(), restored);
                encounterWorlds.put(restored.id(), world);
                remainingLifetime.put(world.getName(), Math.max(0, state.encounter.remainingLifetimeMillis));
                scheduleTick(world);
                return;
            }
        } catch (IOException | RuntimeException error) {
            LOGGER.atWarning().log("Wandering trader suspended in world %s: %s", world.getName(), error.getMessage());
            runningWorlds.remove(world.getName());
            return;
        }
        scheduleTick(world);
    }

    private void clearSavedTraders(World world, WanderingTraderStateStorage.State state) {
        if (!world.isAlive()) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<String> uuids = new ArrayList<>(state.pendingRemoval);
        if (state.encounter != null) uuids.add(state.encounter.npcUuid);
        List<String> unresolved = new ArrayList<>();
        for (String uuidText : uuids) {
            try {
                Ref<EntityStore> ref = world.getEntityRef(UUID.fromString(uuidText));
                if (ref == null || !ref.isValid() || !TraderNpc.despawnByUuid(store, uuidText)) unresolved.add(uuidText);
            } catch (IllegalArgumentException ignored) { }
        }
        state.encounter = null;
        state.pendingRemoval = unresolved;
        state.nextAttemptAtMillis = 0;
        try { storage(world).save(state); }
        catch (IOException error) { LOGGER.atWarning().log("Could not clear saved trader state: %s", error.getMessage()); }
    }

    private void scheduleTick(World world) {
        if (stopped || !world.isAlive()) { runningWorlds.remove(world.getName()); return; }
        world.scheduleAfter(() -> {
            if (stopped) { runningWorlds.remove(world.getName()); return; }
            try { tick(world); }
            catch (RuntimeException error) { LOGGER.atWarning().log("Wandering trader tick failed in %s: %s", world.getName(), error.getMessage()); }
            scheduleTick(world);
        }, 1, TimeUnit.SECONDS);
    }

    private void tick(World world) {
        if (world.getPlayerCount() == 0) return;
        String worldId = world.getName();
        WanderingTraderEncounter encounter = encounters.get(worldId);
        if (encounter != null) {
            long left = Math.max(0, remainingLifetime.getOrDefault(worldId, 0L) - 1000);
            remainingLifetime.put(worldId, left);
            if (left == 0) endEncounter(world, encounter);
            else checkpoint(world, encounter);
            return;
        }
        checkpoint(world, null);
        long next = nextAttemptAt.getOrDefault(worldId, System.currentTimeMillis() + config.attemptIntervalSeconds * 1000L);
        if (System.currentTimeMillis() < next) return;
        nextAttemptAt.put(worldId, System.currentTimeMillis() + config.attemptIntervalSeconds * 1000L);
        if (Math.random() >= config.spawnChance) return;
        List<Player> players = world.getPlayers();
        if (players.isEmpty()) return;
        Player player = players.get(new Random().nextInt(players.size()));
        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef == null || !playerRef.isValid()) return;
        Store<EntityStore> store = playerRef.getStore();
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) return;
        Vector3d spawn = findSpawn(world, transform.getPosition());
        if (spawn == null) return;
        try {
            TraderNpc npc = new TraderNpc(config.displayName, config.npcRole);
            npc.spawn(store, spawn, new Rotation3f(transform.getRotation()));
            String uuid = npc.getUuid(store);
            if (uuid == null) { npc.despawn(store); return; }
            WanderingTraderEncounter created = new WanderingTraderEncounter(uuid,
                    System.currentTimeMillis() + config.lifetimeSeconds * 1000L, activeTrades);
            encounters.put(worldId, created);
            encounterWorlds.put(created.id(), world);
            remainingLifetime.put(worldId, config.lifetimeSeconds * 1000L);
            try { save(world, created); }
            catch (IOException failure) {
                encounters.remove(worldId, created); encounterWorlds.remove(created.id()); npc.despawn(store);
                throw failure;
            }
        } catch (Exception error) {
            LOGGER.atWarning().log("Could not spawn wandering trader in %s: %s", worldId, error.getMessage());
        }
    }

    private Vector3d findSpawn(World world, Vector3d center) {
        var grid = world.getChunkStore();
        Store<ChunkStore> chunks = grid.getStore();
        var map = BlockType.getAssetMap();
        int emptyId = map.getIndex(BlockType.EMPTY_KEY);
        Random random = new Random();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(config.minSpawnDistance * (double) config.minSpawnDistance
                    + random.nextDouble() * (config.maxSpawnDistance * (double) config.maxSpawnDistance
                    - config.minSpawnDistance * (double) config.minSpawnDistance));
            int x = (int) Math.floor(center.x + Math.cos(angle) * radius);
            int z = (int) Math.floor(center.z + Math.sin(angle) * radius);
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            if (grid.getChunkReference(chunkIndex) == null) continue;
            HeightmapColumn heightmap = grid.getChunkComponent(chunkIndex, HeightmapColumn.getComponentType());
            if (heightmap == null) continue;
            int groundY = heightmap.getCollisionHeight(x & ChunkUtil.SIZE_MASK, z & ChunkUtil.SIZE_MASK);
            if (groundY == HeightmapColumn.NO_HEIGHT || groundY < center.y - 8 || groundY > center.y + 8) continue;
            boolean clear = true;
            for (int y = groundY + 1; y <= groundY + 3; y++) {
                Ref<ChunkStore> sectionRef = grid.getChunkSectionReferenceAtBlock(x, y, z);
                if (sectionRef == null || !sectionRef.isValid()) { clear = false; break; }
                BlockSection section = chunks.getComponent(sectionRef, BlockSection.getComponentType());
                if (section == null || !section.loaded || section.get(ChunkUtil.indexBlock(x, y, z)) != emptyId) {
                    clear = false; break;
                }
                FluidSection fluids = chunks.getComponent(sectionRef, FluidSection.getComponentType());
                if (fluids != null && fluids.getFluidId(ChunkUtil.indexBlock(x, y, z)) != 0) {
                    clear = false; break;
                }
            }
            if (clear) return new Vector3d(x + 0.5, groundY + 1, z + 0.5);
        }
        return null;
    }

    private void endEncounter(World world, WanderingTraderEncounter encounter) {
        encounterWorlds.remove(encounter.id());
        encounters.remove(world.getName(), encounter);
        remainingLifetime.remove(world.getName());
        lastCheckpointAt.remove(world.getName());
        try {
            Ref<EntityStore> npcRef = world.getEntityRef(UUID.fromString(encounter.npcUuid()));
            if (npcRef != null && npcRef.isValid()) TraderNpc.despawnByUuid(npcRef.getStore(), encounter.npcUuid());
            else {
                var state = storage(world).load();
                if (!state.pendingRemoval.contains(encounter.npcUuid())) state.pendingRemoval.add(encounter.npcUuid());
                storage(world).save(state);
            }
        } catch (IllegalArgumentException | IOException error) {
            LOGGER.atWarning().log("Could not remove expired wandering trader: %s", error.getMessage());
        }
        try {
            var state = storage(world).load();
            state.encounter = null;
            state.nextAttemptAtMillis = config.attemptIntervalSeconds * 1000L;
            nextAttemptAt.put(world.getName(), System.currentTimeMillis() + state.nextAttemptAtMillis);
            storage(world).save(state);
        } catch (IOException error) { LOGGER.atWarning().log("Could not save trader expiration: %s", error.getMessage()); }
    }

    public WanderingTraderEncounter findByNpcUuid(String uuid) {
        return encounters.values().stream().filter(encounter -> encounter.npcUuid().equals(uuid)).findFirst().orElse(null);
    }
    public void onNpcRemoved(String uuid) {
        WanderingTraderEncounter encounter = findByNpcUuid(uuid);
        if (encounter == null) return;
        World world = encounterWorlds.remove(encounter.id());
        if (world == null) return;
        encounters.remove(world.getName(), encounter);
        remainingLifetime.remove(world.getName());
        long next = config.attemptIntervalSeconds * 1000L;
        nextAttemptAt.put(world.getName(), System.currentTimeMillis() + next);
        try {
            var state = storage(world).load();
            state.encounter = null;
            state.nextAttemptAtMillis = next;
            storage(world).save(state);
        } catch (IOException error) { LOGGER.atWarning().log("Could not save removed trader state: %s", error.getMessage()); }
    }
    public boolean removeIfPending(World world, Store<EntityStore> store, String uuid) {
        try {
            var state = storage(world).load();
            if (!state.pendingRemoval.contains(uuid)) return false;
            if (!TraderNpc.despawnByUuid(store, uuid)) return false;
            state.pendingRemoval.remove(uuid);
            storage(world).save(state);
            return true;
        } catch (IOException error) {
            LOGGER.atWarning().log("Could not check pending trader removal: %s", error.getMessage());
            return false;
        }
    }
    public WanderingTraderEncounter encounter(String id) { return encounters.values().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null); }
    public boolean isActive(String id) {
        WanderingTraderEncounter encounter = encounter(id);
        World world = encounterWorlds.get(id);
        return encounter != null && world != null && remainingLifetime.getOrDefault(world.getName(), 0L) > 0;
    }
    public List<WanderingTraderEncounter.Offer> offers(String encounterId) {
        var encounter = encounter(encounterId);
        return encounter == null ? List.of() : new ArrayList<>(encounter.offers().values());
    }
    public String displayName() { return config.displayName; }
    public boolean canInteract(String encounterId, Ref<EntityStore> playerRef, Store<EntityStore> store) {
        World world = encounterWorlds.get(encounterId);
        WanderingTraderEncounter encounter = encounter(encounterId);
        if (world == null || encounter == null || playerRef == null || !playerRef.isValid()
                || playerRef.getStore().getExternalData().getWorld() != world
                || remainingLifetime.getOrDefault(world.getName(), 0L) <= 0) return false;
        Ref<EntityStore> npcRef;
        try { npcRef = world.getEntityRef(UUID.fromString(encounter.npcUuid())); }
        catch (IllegalArgumentException invalid) { return false; }
        if (npcRef == null || !npcRef.isValid()) return false;
        TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        TransformComponent npcTransform = npcRef.getStore().getComponent(npcRef, TransformComponent.getComponentType());
        if (playerTransform == null || npcTransform == null) return false;
        return playerTransform.getPosition().distanceSquared(npcTransform.getPosition()) <= 64.0;
    }

    public boolean consumeUse(String encounterId, String tradeId) {
        WanderingTraderEncounter encounter = encounter(encounterId);
        World world = encounterWorlds.get(encounterId);
        if (encounter == null || remainingLifetime.getOrDefault(encounterWorlds.get(encounterId) == null ? "" : encounterWorlds.get(encounterId).getName(), 0L) <= 0
                || world == null || !encounter.consumeUse(tradeId)) return false;
        try { save(world, encounter); return true; }
        catch (IOException error) { encounter.offer(tradeId).setRemaining(encounter.offer(tradeId).remaining() + 1); return false; }
    }

    public void refundUse(String encounterId, String tradeId) {
        WanderingTraderEncounter encounter = encounter(encounterId);
        if (encounter == null) return;
        var offer = encounter.offer(tradeId);
        if (offer == null || offer.remaining() >= offer.maxUses()) return;
        offer.setRemaining(offer.remaining() + 1);
        World world = encounterWorlds.get(encounterId);
        if (world != null) try { save(world, encounter); }
        catch (IOException error) {
            offer.setRemaining(offer.remaining() - 1);
            LOGGER.atWarning().log("Could not persist refunded trader use: %s", error.getMessage());
        }
    }

    private void save(World world, WanderingTraderEncounter encounter) throws IOException {
        var state = storage(world).load();
        state.nextAttemptAtMillis = Math.max(0, nextAttemptAt.getOrDefault(world.getName(), 0L) - System.currentTimeMillis());
        var data = new WanderingTraderStateStorage.EncounterData();
        data.id = encounter.id(); data.npcUuid = encounter.npcUuid(); data.npcRole = config.npcRole;
        data.displayName = config.displayName;
        data.remainingLifetimeMillis = remainingLifetime.getOrDefault(world.getName(),
                Math.max(0, encounter.expiresAtMillis() - System.currentTimeMillis()));
        try {
            Ref<EntityStore> npcRef = world.getEntityRef(UUID.fromString(encounter.npcUuid()));
            if (npcRef != null && npcRef.isValid()) {
                TransformComponent transform = npcRef.getStore().getComponent(npcRef, TransformComponent.getComponentType());
                if (transform != null) {
                    data.x = transform.getPosition().x; data.y = transform.getPosition().y; data.z = transform.getPosition().z;
                    data.yaw = transform.getRotation().yaw(); data.pitch = transform.getRotation().pitch();
                    data.roll = transform.getRotation().roll();
                }
            }
        } catch (IllegalArgumentException ignored) { }
        for (var offer : encounter.offers().values()) {
            var row = new WanderingTraderStateStorage.OfferData(); var definition = offer.definition();
            row.id = definition.id(); row.inputItemId = definition.inputItemId(); row.inputQuantity = definition.inputQuantity();
            row.outputItemId = definition.outputItemId(); row.outputQuantity = definition.outputQuantity();
            row.maxUses = definition.maxUses(); row.remaining = offer.remaining(); data.offers.add(row);
        }
        state.encounter = data; storage(world).save(state);
        lastCheckpointAt.put(world.getName(), System.currentTimeMillis());
    }

    private void checkpoint(World world, WanderingTraderEncounter encounter) {
        long now = System.currentTimeMillis();
        if (now - lastCheckpointAt.getOrDefault(world.getName(), now) < 30_000) return;
        try {
            if (encounter != null) save(world, encounter);
            else {
                var state = storage(world).load();
                state.nextAttemptAtMillis = Math.max(0, nextAttemptAt.getOrDefault(world.getName(), now) - now);
                storage(world).save(state);
                lastCheckpointAt.put(world.getName(), now);
            }
        } catch (IOException error) { LOGGER.atWarning().log("Could not checkpoint trader state: %s", error.getMessage()); }
    }

    private WanderingTraderStateStorage storage(World world) { return new WanderingTraderStateStorage(dataDirectory, world.getName()); }
    @Override public void close() {
        stopped = true;
        Universe universe = Universe.get();
        if (universe != null) for (World world : universe.getWorlds().values()) {
            if (!encounters.containsKey(world.getName())) {
                try {
                    var state = storage(world).load();
                    state.nextAttemptAtMillis = Math.max(0, nextAttemptAt.getOrDefault(world.getName(), 0L) - System.currentTimeMillis());
                    storage(world).save(state);
                } catch (IOException error) { LOGGER.atWarning().log("Could not save trader cooldown on shutdown: %s", error.getMessage()); }
            }
        }
        for (var entry : encounters.values()) {
            World world = encounterWorlds.get(entry.id());
            if (world != null) try { save(world, entry); } catch (IOException error) {
                LOGGER.atWarning().log("Could not save active trader on shutdown: %s", error.getMessage());
            }
        }
    }
}
