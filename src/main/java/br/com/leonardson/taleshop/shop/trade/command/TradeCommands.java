package br.com.leonardson.taleshop.shop.trade.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

import br.com.leonardson.taleshop.shop.ShopRegistry;

public class TradeCommands extends AbstractCommandCollection {
    public TradeCommands(ShopRegistry tradeRegistry) {
        super("trade", "Trade commands");

        addSubCommand(new ListTradesCommand(tradeRegistry));
        addSubCommand(new CreateTradeCommand(tradeRegistry));
        addSubCommand(new UpdateTradeCommand(tradeRegistry));
        addSubCommand(new DeleteTradeCommand(tradeRegistry));
    }
    
}
