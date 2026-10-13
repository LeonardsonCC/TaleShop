package br.com.leonardson.taleshop.wandering;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class WanderingTraderStateStorageTest {
    @TempDir Path directory;

    @Test
    void roundTripsEncounterAndRejectsUnsupportedSchema() throws Exception {
        var storage = new WanderingTraderStateStorage(directory, "world/one");
        var state = new WanderingTraderStateStorage.State();
        state.nextAttemptAtMillis = 1234;
        state.encounter = new WanderingTraderStateStorage.EncounterData();
        state.encounter.id = "encounter";
        state.encounter.npcUuid = "uuid";
        state.encounter.remainingLifetimeMillis = 9000;
        var offer = new WanderingTraderStateStorage.OfferData();
        offer.id = "offer"; offer.inputItemId = "Coin"; offer.inputQuantity = 2;
        offer.outputItemId = "Potion"; offer.outputQuantity = 1; offer.maxUses = 5; offer.remaining = 3;
        state.encounter.offers.add(offer);
        storage.save(state);

        var loaded = storage.load();
        assertEquals(1, loaded.schemaVersion);
        assertEquals(1234, loaded.nextAttemptAtMillis);
        assertEquals("uuid", loaded.encounter.npcUuid);
        assertEquals(3, loaded.encounter.offers.getFirst().remaining);
        assertTrue(Files.exists(directory.resolve("wandering-traders")));
    }

    @Test
    void preservesCorruptStateInsteadOfReplacingIt() throws Exception {
        var storage = new WanderingTraderStateStorage(directory, "world");
        storage.save(new WanderingTraderStateStorage.State());
        Path file = Files.list(directory.resolve("wandering-traders")).findFirst().orElseThrow();
        Files.writeString(file, "{bad json");
        assertThrows(java.io.IOException.class, storage::load);
        assertEquals("{bad json", Files.readString(file));
    }
}
