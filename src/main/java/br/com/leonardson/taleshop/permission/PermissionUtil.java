package br.com.leonardson.taleshop.permission;

import com.hypixel.hytale.server.core.entity.entities.Player;
import javax.annotation.Nonnull;
/**
 * Utility class for checking player permissions.
 */
public final class PermissionUtil {
    
    private PermissionUtil() {
        // Utility class
    }
    
    /**
     * Checks if a player has a specific permission.
     *
     * @param player The player to check
     * @param permission The permission string to check
     * @return true if the player has the permission
     */
    public static boolean hasPermission(@Nonnull Player player, @Nonnull String permission) {
        if (player == null || permission == null || permission.isBlank()) {
            return false;
        }
        return player.hasPermission(permission);
    }

    /**
     * Checks if a player has permission to select custom entity types for NPCs.
     *
     * @param player The player to check
     * @return true if the player has the permission
     */
    public static boolean hasEntitySelectionPermission(@Nonnull Player player) {
        return hasPermission(player, "taleshop.npc.selectentity");
    }
}
