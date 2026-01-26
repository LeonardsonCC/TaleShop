package br.com.leonardson.taleshop.shop.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class TraderMenuPage extends InteractiveCustomUIPage<TraderMenuPage.MenuEventData> {
    private static final String PAGE_PATH = "Pages/TraderMenuPage.ui";
    private final String ownerId;
    private final String shopName;

    public TraderMenuPage(@Nonnull PlayerRef playerRef, @Nonnull String ownerId, @Nonnull String shopName) {
        super(playerRef, CustomPageLifetime.CanDismiss, MenuEventData.CODEC);
        this.ownerId = ownerId;
        this.shopName = shopName;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append(PAGE_PATH);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#PreviewShopButton",
            EventData.of("Action", "Preview"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#EditButton",
            EventData.of("Action", "Edit"),
            false
        );

        commandBuilder.set("#TitleLabel.Text", shopName);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull MenuEventData data) {
        if (data.action == null || data.action.isBlank()) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }

        if ("Preview".equals(data.action)) {
            playerRef.sendMessage(Message.raw("Preview Shop is coming soon."));
            return;
        }

        if ("Edit".equals(data.action)) {
            player.getPageManager().openCustomPage(ref, store, new TradeListPage(playerRef, ownerId, shopName));
        }
    }

    public static class MenuEventData {
        public static final BuilderCodec<MenuEventData> CODEC = BuilderCodec.builder(MenuEventData.class, MenuEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action)
            .add()
            .build();
        private String action;

        public MenuEventData() {
        }
    }
}
