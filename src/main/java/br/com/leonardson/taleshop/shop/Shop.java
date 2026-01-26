package br.com.leonardson.taleshop.shop;

import java.util.Collections;
import java.util.List;

import br.com.leonardson.taleshop.shop.trade.Trade;

public record Shop(String ownerId, String ownerName, String name, List<Trade> trades) {
    public Shop {
        trades = Collections.unmodifiableList(trades);
    }
}
