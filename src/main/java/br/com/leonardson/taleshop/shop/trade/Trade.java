package br.com.leonardson.taleshop.shop.trade;

public record Trade(int id, String inputItemId, int inputQuantity, String outputItemId, int outputQuantity) {
}
