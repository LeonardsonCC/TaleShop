package br.com.leonardson.taleshop.shop;

import java.util.Collections;
import java.util.List;
import br.com.leonardson.taleshop.shop.trade.Trade;

public class Shop {
    private final String ownerId;
    private final String ownerName;
    private final String name;
    private final List<Trade> trades;
    private final String traderUuid;

    public Shop(String ownerId, String ownerName, String name, List<Trade> trades, String traderUuid) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.name = name;
        this.trades = Collections.unmodifiableList(trades);
        this.traderUuid = traderUuid == null ? "" : traderUuid;
    }

    public String ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public String name() {
        return name;
    }

    public List<Trade> trades() {
        return trades;
    }

    public String traderUuid() {
        return traderUuid;
    }

}
