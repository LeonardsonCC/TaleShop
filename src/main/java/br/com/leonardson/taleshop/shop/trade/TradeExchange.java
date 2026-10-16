package br.com.leonardson.taleshop.shop.trade;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Runs on the world's thread, before delivery to the buyer. Never synthesizes stock or payment. */
public final class TradeExchange {
    private TradeExchange() {}

    public static int count(List<ItemContainer> containers, Predicate<ItemStack> matches) {
        int count = 0;
        for (ItemContainer container : containers) {
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (matches.test(stack)) count += stack.getQuantity();
            }
        }
        return count;
    }

    public static List<ItemStack> exchange(Trade trade, ItemContainer buyer, List<ItemContainer> stock, boolean admin) {
        // Decode before touching inventories, so corrupt templates cannot charge a buyer.
        TradeItems.inputStack(trade);
        ItemStack adminOutput = TradeItems.outputStack(trade);
        if (count(List.of(buyer), stack -> TradeItems.matchesInput(trade, stack)) < trade.inputQuantity()) {
            throw new IllegalArgumentException("You don't have enough items.");
        }
        if (!admin && count(stock, stack -> TradeItems.matchesOutput(trade, stack)) < trade.outputQuantity()) {
            throw new IllegalArgumentException("Shop is out of stock.");
        }

        List<Snapshot> snapshots = new ArrayList<>();
        snapshots.add(new Snapshot(buyer));
        if (!admin) for (ItemContainer container : stock) snapshots.add(new Snapshot(container));
        try {
            List<ItemStack> output = admin ? List.of(adminOutput) : take(stock, stack -> TradeItems.matchesOutput(trade, stack), trade.outputQuantity());
            List<ItemStack> payment = take(List.of(buyer), stack -> TradeItems.matchesInput(trade, stack), trade.inputQuantity());
            if (!admin) {
                for (ItemStack stack : payment) {
                    ItemStack remainder = stack;
                    for (ItemContainer container : stock) {
                        remainder = container.addItemStack(remainder).getRemainder();
                        if (ItemStack.isEmpty(remainder)) break;
                    }
                    if (!ItemStack.isEmpty(remainder)) {
                        throw new IllegalArgumentException("Shop has no space for that trade.");
                    }
                }
            }
            return output;
        } catch (RuntimeException failure) {
            // This also undoes a partially accepted deposit, preserving every original stack.
            for (Snapshot snapshot : snapshots) snapshot.restore();
            throw failure;
        }
    }

    private static List<ItemStack> take(List<ItemContainer> containers, Predicate<ItemStack> matches, int quantity) {
        List<ItemStack> taken = new ArrayList<>();
        int remaining = quantity;
        for (ItemContainer container : containers) {
            for (short slot = 0; slot < container.getCapacity() && remaining > 0; slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (!matches.test(stack)) continue;
                int amount = Math.min(remaining, stack.getQuantity());
                var transaction = container.removeItemStackFromSlot(slot, stack, amount);
                if (!transaction.succeeded()) {
                    throw new IllegalArgumentException("Items could not be removed. Please try again.");
                }
                taken.add(stack.withQuantity(amount));
                remaining -= amount;
            }
        }
        if (remaining > 0) throw new IllegalArgumentException("Items changed. Please try again.");
        return taken;
    }

    private static final class Snapshot {
        private final ItemContainer container;
        private final List<ItemStack> slots = new ArrayList<>();

        Snapshot(ItemContainer container) {
            this.container = container;
            for (short slot = 0; slot < container.getCapacity(); slot++) slots.add(container.getItemStack(slot));
        }

        void restore() {
            for (short slot = 0; slot < slots.size(); slot++) {
                // Rollback must bypass deposit/removal filters.
                if (!container.setItemStackForSlot(slot, slots.get(slot), false).succeeded()) {
                    throw new IllegalStateException("Could not restore trade inventory.");
                }
            }
        }
    }
}
