package br.com.leonardson.taleshop.player;

import com.hypixel.hytale.server.core.entity.entities.Player;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlayerIdentity {
    private PlayerIdentity() {
    }

    @Nonnull
    public static String resolveOwnerId(@Nonnull Player player) {
        String id = tryGetString(player, "getUuid", "getUUID", "getUniqueId", "getId", "getPlayerId", "getProfileId");
        if (id != null && !id.isBlank()) {
            return id;
        }

        Object uuidObj = invokeFirst(player, "getUuid", "getUUID", "getUniqueId");
        if (uuidObj instanceof UUID uuid) {
            return uuid.toString();
        }

        String name = resolveDisplayName(player);
        if (!name.isBlank()) {
            return name;
        }

        return player.toString();
    }

    @Nonnull
    public static String resolveDisplayName(@Nonnull Player player) {
        String name = tryGetString(player, "getName", "getUsername", "getDisplayName", "getPlayerName");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return "Player";
    }

    @Nullable
    private static String tryGetString(Object target, String... methodNames) {
        Object value = invokeFirst(target, methodNames);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private static Object invokeFirst(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            Method method = findMethod(target.getClass(), methodName);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(target);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    private static Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
