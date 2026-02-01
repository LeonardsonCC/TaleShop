package br.com.leonardson.taleshop.player;

import com.hypixel.hytale.server.core.entity.entities.Player;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class PlayerIdentity {
    private PlayerIdentity() {
    }

    @Nonnull
    public static String resolveOwnerId(@Nonnull Player player) {
        UUID uuid = player.getUuid();
        if (uuid != null) {
            return uuid.toString();
        }
        String name = player.getName();
        return name == null || name.isBlank() ? "Player" : name;
    }

    @Nonnull
    public static String resolveDisplayName(@Nonnull Player player) {
        String name = player.getName();
        return name == null || name.isBlank() ? "Player" : name;
    }
}
