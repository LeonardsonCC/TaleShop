package br.com.leonardson.taleshop.player;

import com.hypixel.hytale.server.core.entity.entities.Player;
import javax.annotation.Nonnull;

public final class PlayerIdentity {
    private PlayerIdentity() {}

    @Nonnull
    public static String resolveOwnerId(@Nonnull Player player) {
        return player.getPlayerRef().getUuid().toString();
    }

    @Nonnull
    public static String resolveDisplayName(@Nonnull Player player) {
        return player.getPlayerRef().getUsername();
    }
}
