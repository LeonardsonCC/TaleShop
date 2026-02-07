package br.com.leonardson.taleshop.shop;

public final class AdminShopAccess {
    public static final String OWNER_ID = "taleshop_admin";
    public static final String OWNER_NAME = "Admin";

    private AdminShopAccess() {
    }

    public static boolean isAdminOwnerId(String ownerId) {
        return OWNER_ID.equals(ownerId);
    }
}
