package br.com.leonardson.taleshop.permission;

import com.hypixel.hytale.server.core.entity.entities.Player;
import javax.annotation.Nonnull;
import java.lang.reflect.Method;

/**
 * Utility class for checking player permissions.
 * Note: This uses reflection to check for permissions, as the Hytale API
 * may have permission checking capabilities not directly exposed.
 */
public final class PermissionUtil {
    public static final String ADMIN_MANAGE_PERMISSION = "taleshop.admin.manage";
    
    private PermissionUtil() {
        // Utility class
    }
    
    /**
     * Checks if a player has a specific permission.
     * This method first tries to use reflection to find a hasPermission method,
     * and falls back to always returning true if no permission system is found.
     * 
     * @param player The player to check
     * @param permission The permission string to check
     * @return true if the player has the permission or if no permission system exists
     */
    public static boolean hasPermission(@Nonnull Player player, @Nonnull String permission) {
        if (player == null || permission == null || permission.isBlank()) {
            return false;
        }
        
        // Try to find and invoke hasPermission method
        Boolean result = tryInvokePermissionCheck(player, permission, 
            "hasPermission", "checkPermission", "hasAccess", "canUse");
        
        if (result != null) {
            return result;
        }
        
        // If no permission system is found, default to true (allow access)
        return true;
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

    public static boolean hasAdminManagePermission(@Nonnull Player player) {
        return hasPermission(player, ADMIN_MANAGE_PERMISSION);
    }
    
    private static Boolean tryInvokePermissionCheck(Player player, String permission, String... methodNames) {
        Class<?> playerClass = player.getClass();
        
        for (String methodName : methodNames) {
            try {
                // Try to find method with String parameter
                Method method = findMethod(playerClass, methodName, String.class);
                if (method != null) {
                    method.setAccessible(true);
                    Object result = method.invoke(player, permission);
                    if (result instanceof Boolean) {
                        return (Boolean) result;
                    }
                }
            } catch (Exception ignored) {
                // Continue to next method name
            }
        }
        
        return null;
    }
    
    private static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
