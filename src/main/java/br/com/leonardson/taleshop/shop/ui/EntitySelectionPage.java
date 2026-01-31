package br.com.leonardson.taleshop.shop.ui;

import br.com.leonardson.taleshop.TaleShop;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.TraderNpc;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.common.util.StringCompareUtil;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.command.system.exceptions.GeneralCommandException;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.spawning.ISpawnableWithModel;
import com.hypixel.hytale.server.spawning.SpawningContext;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Entity Selection Page for selecting NPC roles when spawning traders.
 * Based on Hytale's EntitySpawnPage implementation.
 */
public class EntitySelectionPage extends InteractiveCustomUIPage<EntitySelectionPage.EntitySelectionEventData> {
    private static final Value<String> BUTTON_LABEL_STYLE = Value.ref("Common/TextButton.ui", "LabelStyle");
    private static final Value<String> BUTTON_LABEL_STYLE_SELECTED = Value.ref("Common/TextButton.ui", "SelectedLabelStyle");
    private static final int LOOK_RAYCAST_DISTANCE = 4;
    private static final int FALLBACK_RAYCAST_DOWN_DISTANCE = 3;
    private static final double FALLBACK_RAYCAST_Y_OFFSET = 0.5;
    
    private final String ownerId;
    private final String shopName;
    
    @Nonnull
    private String searchQuery = "";
    private List<String> npcRoles;
    @Nullable
    private String selectedNpcRole;
    @Nullable
    private Ref<EntityStore> modelPreview;
    private Vector3d position;
    private Vector3f rotation;
    private float currentRotationOffset = 0.0F;

    public EntitySelectionPage(@Nonnull PlayerRef playerRef, @Nonnull String ownerId, @Nonnull String shopName) {
        super(playerRef, CustomPageLifetime.CanDismiss, EntitySelectionEventData.CODEC);
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
        commandBuilder.append("Pages/EntitySelectionPage.ui");
        
        // Set the title
        commandBuilder.set("#TitleLabel.Text", "Select Entity for " + shopName);
        
        // Bind search input
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SearchInput",
            EventData.of("@SearchQuery", "#SearchInput.Value"),
            false
        );
        
        // Bind spawn button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SpawnButton",
            EventData.of("Type", "Spawn"),
            false
        );
        
        // Bind cancel button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CancelButton",
            EventData.of("Type", "Cancel"),
            false
        );
        
        // Build the NPC list
        this.buildNPCList(ref, store, commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EntitySelectionEventData data) {
        if (data.searchQuery != null) {
            this.searchQuery = data.searchQuery.trim().toLowerCase();
            UICommandBuilder commandBuilder = new UICommandBuilder();
            UIEventBuilder eventBuilder = new UIEventBuilder();
            this.buildNPCList(ref, store, commandBuilder, eventBuilder);
            this.sendUpdate(commandBuilder, eventBuilder, false);
            return;
        }
        
        if (data.type == null) {
            return;
        }
        
        switch (data.type) {
            case "Select":
                if (data.npcRole != null) {
                    UICommandBuilder commandBuilder = new UICommandBuilder();
                    this.selectNPCRole(ref, store, data.npcRole, commandBuilder);
                    this.sendUpdate(commandBuilder, null, false);
                }
                break;
            case "Spawn":
                this.handleSpawn(ref, store);
                break;
            case "Cancel":
                Player player = store.getComponent(ref, Player.getComponentType());
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (player != null && playerRef != null) {
                    player.getPageManager().openCustomPage(ref, store, new ShopListPage(playerRef, ownerId));
                }
                break;
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        this.clearPreview(store);
    }

    private void buildNPCList(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder
    ) {
        commandBuilder.clear("#NPCList");
        
        // Get all role template names from NPCPlugin
        List<String> roleTemplateNames = NPCPlugin.get().getRoleTemplateNames(true);
        
        // Apply search filter
        if (!this.searchQuery.isEmpty()) {
            Object2IntMap<String> map = new Object2IntOpenHashMap<>(roleTemplateNames.size());
            
            for (String value : roleTemplateNames) {
                int fuzzyDistance = StringCompareUtil.getFuzzyDistance(value, this.searchQuery, Locale.ENGLISH);
                if (fuzzyDistance > 0) {
                    map.put(value, fuzzyDistance);
                }
            }
            
            // Sort by fuzzy distance and limit to 20 results
            this.npcRoles = map.keySet()
                .stream()
                .sorted()
                .sorted(Comparator.comparingInt(map::getInt).reversed())
                .limit(20L)
                .collect(Collectors.toList());
        } else {
            roleTemplateNames.sort(String::compareTo);
            this.npcRoles = roleTemplateNames;
        }
        
        // Build list items
        for (int i = 0; i < this.npcRoles.size(); i++) {
            String id = this.npcRoles.get(i);
            String selector = "#NPCList[" + i + "]";
            
            // Append button from common template
            commandBuilder.append("#NPCList", "Common/TextButton.ui");
            commandBuilder.set(selector + " #Button.Text", id);
            
            // Bind activation event
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #Button",
                new EventData().append("Type", "Select").append("NPCRole", id),
                false
            );
        }
        
        // Update selection display
        if (!this.npcRoles.isEmpty() && this.selectedNpcRole != null) {
            if (this.npcRoles.contains(this.selectedNpcRole)) {
                this.selectNPCRole(ref, store, this.selectedNpcRole, commandBuilder);
            } else {
                this.selectedNpcRole = null;
                this.clearPreview(store);
                commandBuilder.set("#SelectedName.Text", "Select an NPC");
            }
        } else if (this.selectedNpcRole == null) {
            commandBuilder.set("#SelectedName.Text", "Select an NPC");
        }
    }

    private void selectNPCRole(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String npcRole,
        @Nonnull UICommandBuilder commandBuilder
    ) {
        // Deselect previous selection
        if (this.selectedNpcRole != null && this.npcRoles.contains(this.selectedNpcRole)) {
            commandBuilder.set("#NPCList[" + this.npcRoles.indexOf(this.selectedNpcRole) + "] #Button.Style", BUTTON_LABEL_STYLE);
        }
        
        // Select new role
        commandBuilder.set("#NPCList[" + this.npcRoles.indexOf(npcRole) + "] #Button.Style", BUTTON_LABEL_STYLE_SELECTED);
        commandBuilder.set("#SelectedName.Text", npcRole);
        this.selectedNpcRole = npcRole;
        
        // Create or update preview
        Model model = this.getNPCModel(npcRole);
        if (model != null) {
            this.createOrUpdatePreview(ref, store, model);
        }
    }

    private void handleSpawn(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (this.selectedNpcRole == null) {
            return;
        }
        
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        
        ShopRegistry registry = resolveRegistry();
        if (registry == null) {
            player.sendMessage(Message.raw("Shop registry not available."));
            return;
        }

        Shop shop = registry.getShop(ownerId, shopName);
        if (shop == null) {
            player.sendMessage(Message.raw("Shop not found."));
            return;
        }

        // Despawn existing trader if any
        String existingTrader = shop.traderUuid();
        if (existingTrader != null && !existingTrader.isBlank()) {
            TraderNpc.despawnByUuid(store, existingTrader);
        }

        // Clear preview
        this.clearPreview(store);

        // Spawn NPC with selected entity role
        TraderNpc traderNpc = new TraderNpc(shop.name(), this.selectedNpcRole);
        try {
            traderNpc.spawn(store, ref);
            String traderUuid = traderNpc.getUuid(store);
            if (traderUuid != null && !traderUuid.isBlank()) {
                registry.setTraderUuid(ownerId, shop.name(), traderUuid);
                player.sendMessage(Message.raw("Trader spawned as " + this.selectedNpcRole + " for " + shop.name()));
            } else {
                player.sendMessage(Message.raw("Trader spawned but UUID not available."));
            }
        } catch (IllegalStateException ex) {
            player.sendMessage(Message.raw("Failed to spawn trader: " + ex.getMessage()));
            return;
        }
        
        // Close page and go back to shop list
        player.getPageManager().setPage(ref, store, Page.None);
        player.getPageManager().openCustomPage(ref, store, new ShopListPage(playerRef, ownerId));
    }

    private void clearPreview(@Nonnull Store<EntityStore> store) {
        if (this.modelPreview != null && this.modelPreview.isValid()) {
            store.removeEntity(this.modelPreview, RemoveReason.REMOVE);
        }
        this.modelPreview = null;
    }

    private void initPosition(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation headRotationComponent = store.getComponent(ref, HeadRotation.getComponentType());
        
        if (transformComponent == null || headRotationComponent == null) {
            return;
        }
        
        Vector3d playerPosition = transformComponent.getPosition();
        Vector3f headRotation = headRotationComponent.getRotation();
        Vector3d direction = Transform.getDirection(headRotation.getPitch(), headRotation.getYaw());
        
        // Try to find target location in front of player
        Vector3d lookTarget = TargetUtil.getTargetLocation(ref, LOOK_RAYCAST_DISTANCE, store);
        Vector3d previewPosition;
        
        if (lookTarget != null) {
            previewPosition = lookTarget;
        } else {
            // Fallback: place at fixed distance ahead
            Vector3d aheadPosition = playerPosition.clone().add(direction.clone().scale(LOOK_RAYCAST_DISTANCE));
            World world = store.getExternalData().getWorld();
            Vector3i groundTarget = TargetUtil.getTargetBlock(
                world,
                (blockId, fluidId) -> blockId != 0,
                aheadPosition.x,
                aheadPosition.y + FALLBACK_RAYCAST_Y_OFFSET,
                aheadPosition.z,
                0.0,
                -1.0,
                0.0,
                FALLBACK_RAYCAST_DOWN_DISTANCE
            );
            
            if (groundTarget != null) {
                previewPosition = new Vector3d(groundTarget.x + 0.5, groundTarget.y + 1, groundTarget.z + 0.5);
            } else {
                previewPosition = aheadPosition;
            }
        }
        
        // Calculate rotation to face player
        Vector3d relativePos = playerPosition.clone().subtract(previewPosition);
        relativePos.setY(0.0);
        Vector3f previewRotation = Vector3f.lookAt(relativePos);
        
        this.position = previewPosition;
        this.rotation = previewRotation;
        this.currentRotationOffset = 0.0F;
    }

    private void createOrUpdatePreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nullable Model model) {
        if (model == null) {
            return;
        }
        
        if (this.modelPreview != null && this.modelPreview.isValid()) {
            // Update existing preview
            store.putComponent(this.modelPreview, ModelComponent.getComponentType(), new ModelComponent(model));
        } else {
            // Create new preview
            this.initPosition(ref, store);
            
            Holder<EntityStore> holder = store.getRegistry().newHolder();
            holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(this.position, this.rotation));
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(this.rotation));
            
            this.modelPreview = store.addEntity(holder, AddReason.SPAWN);
        }
    }

    @Nullable
    private Model getNPCModel(@Nonnull String npcRole) {
        try {
            NPCPlugin npcPlugin = NPCPlugin.get();
            int roleIndex = npcPlugin.getIndex(npcRole);
            
            if (roleIndex < 0) {
                return null;
            }
            
            npcPlugin.forceValidation(roleIndex);
            BuilderInfo roleBuilderInfo = npcPlugin.getRoleBuilderInfo(roleIndex);
            
            if (roleBuilderInfo == null || !npcPlugin.testAndValidateRole(roleBuilderInfo)) {
                return null;
            }
            
            Builder<Role> roleBuilder = npcPlugin.tryGetCachedValidRole(roleIndex);
            
            if (roleBuilder == null || !roleBuilder.isSpawnable()) {
                return null;
            }
            
            if (roleBuilder instanceof ISpawnableWithModel spawnable) {
                SpawningContext spawningContext = new SpawningContext();
                if (spawningContext.setSpawnable(spawnable)) {
                    return spawningContext.getModel();
                }
            }
            
            return null;
        } catch (Exception e) {
            // Failed to get model, return null
            return null;
        }
    }

    private ShopRegistry resolveRegistry() {
        TaleShop plugin = TaleShop.getInstance();
        if (plugin == null) {
            return null;
        }
        return plugin.getShopRegistry();
    }

    public static class EntitySelectionEventData {
        public static final BuilderCodec<EntitySelectionEventData> CODEC = BuilderCodec.builder(
            EntitySelectionEventData.class, EntitySelectionEventData::new
        )
        .append(new KeyedCodec<>("NPCRole", Codec.STRING), (data, s) -> data.npcRole = s, data -> data.npcRole).add()
        .append(new KeyedCodec<>("Type", Codec.STRING), (data, s) -> data.type = s, data -> data.type).add()
        .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (data, s) -> data.searchQuery = s, data -> data.searchQuery).add()
        .append(new KeyedCodec<>("@RotationOffset", Codec.FLOAT), (data, s) -> data.rotationOffset = s, data -> data.rotationOffset).add()
        .build();
        
        private String npcRole;
        private String type;
        private String searchQuery;
        private float rotationOffset;

        public EntitySelectionEventData() {
        }
    }
}
