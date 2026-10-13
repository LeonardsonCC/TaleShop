package br.com.leonardson.taleshop.wandering;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Versioned per-world snapshot storage for encounters. */
public final class WanderingTraderStateStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;

    public WanderingTraderStateStorage(Path dataDirectory, String worldId) {
        String safeId = Integer.toHexString(worldId.hashCode()) + "-" + worldId.replaceAll("[^A-Za-z0-9_.-]", "_");
        path = dataDirectory.resolve("wandering-traders").resolve(safeId + ".json");
    }

    public State load() throws java.io.IOException {
        if (!Files.exists(path)) return new State();
        try (Reader reader = Files.newBufferedReader(path)) {
            State state = GSON.fromJson(reader, State.class);
            if (state == null || state.schemaVersion != 1) throw new java.io.IOException("Unsupported wandering trader state schema");
            if (state.pendingRemoval == null) state.pendingRemoval = new ArrayList<>();
            return state;
        } catch (RuntimeException malformed) {
            throw new java.io.IOException("Invalid wandering trader state", malformed);
        }
    }

    public void save(State state) throws java.io.IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary)) { GSON.toJson(state, writer); }
        try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static final class State {
        public int schemaVersion = 1;
        public long nextAttemptAtMillis;
        public long remainingLifetimeMillis;
        public EncounterData encounter;
        public List<String> pendingRemoval = new ArrayList<>();
    }

    public static final class EncounterData {
        public String id;
        public String npcUuid;
        public String npcRole;
        public String displayName;
        public double x, y, z;
        public float yaw, pitch, roll;
        public long remainingLifetimeMillis;
        public List<OfferData> offers = new ArrayList<>();
    }

    public static final class OfferData {
        public String id, inputItemId, outputItemId;
        public int inputQuantity, outputQuantity, maxUses, remaining;
    }
}
