package br.com.leonardson.taleshop.permission;

import com.hypixel.hytale.server.core.entity.entities.Player;
import javax.annotation.Nonnull;

public final class PermissionUtil {
    public static final String ADMIN_MANAGE_PERMISSION = "taleshop.admin.manage";
    private PermissionUtil() {}

    public static boolean hasPermission(@Nonnull Player player, @Nonnull String permission) {
        return !permission.isBlank() && player.getPlayerRef().hasPermission(permission);
    }

    public static boolean hasEntitySelectionPermission(@Nonnull Player player) {
        return hasPermission(player, "taleshop.npc.selectentity");
    }

    public static boolean hasAdminManagePermission(@Nonnull Player player) {
        return hasPermission(player, ADMIN_MANAGE_PERMISSION);
    }
}
