package br.com.leonardson.taleshop.wandering;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Mutable state for one appearance; trade definitions are snapshotted at creation. */
public final class WanderingTraderEncounter {
    private final String id;
    private final String npcUuid;
    private final long expiresAtMillis;
    private final Map<String, Offer> offers;

    public WanderingTraderEncounter(String npcUuid, long expiresAtMillis, Iterable<WanderingTradeDefinition> definitions) {
        this(UUID.randomUUID().toString(), npcUuid, expiresAtMillis, definitions);
    }

    public WanderingTraderEncounter(String id, String npcUuid, long expiresAtMillis,
            Iterable<WanderingTradeDefinition> definitions) {
        this.id = id;
        this.npcUuid = npcUuid;
        this.expiresAtMillis = expiresAtMillis;
        this.offers = new LinkedHashMap<>();
        for (WanderingTradeDefinition definition : definitions) offers.put(definition.id(), new Offer(definition));
    }

    public String id() { return id; }
    public String npcUuid() { return npcUuid; }
    public long expiresAtMillis() { return expiresAtMillis; }
    public Map<String, Offer> offers() { return Map.copyOf(offers); }
    public Offer offer(String id) { return offers.get(id); }
    public static WanderingTraderEncounter restore(String id, String npcUuid, long expiresAtMillis,
            Iterable<WanderingTradeDefinition> definitions, Map<String, Integer> remainingUses) {
        WanderingTraderEncounter encounter = new WanderingTraderEncounter(id, npcUuid, expiresAtMillis, definitions);
        remainingUses.forEach((tradeId, remaining) -> {
            Offer offer = encounter.offer(tradeId);
            if (offer != null) offer.setRemaining(remaining);
        });
        return encounter;
    }
    public boolean isExpired(long nowMillis) { return nowMillis >= expiresAtMillis; }
    public boolean consumeUse(String tradeId) {
        Offer offer = offers.get(tradeId);
        if (offer == null || offer.remaining <= 0) return false;
        offer.remaining--;
        return true;
    }

    public static final class Offer {
        private final WanderingTradeDefinition definition;
        private int remaining;
        private Offer(WanderingTradeDefinition definition) {
            this.definition = definition;
            this.remaining = definition.maxUses();
        }
        public WanderingTradeDefinition definition() { return definition; }
        public int remaining() { return remaining; }
        public int maxUses() { return definition.maxUses(); }
        public void setRemaining(int value) {
            if (value < 0 || value > definition.maxUses()) throw new IllegalArgumentException("Invalid remaining uses");
            remaining = value;
        }
    }
}
