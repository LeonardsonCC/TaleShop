# TaleShop

A Hytale server plugin that allows players to create custom trading shops with NPC traders and automatic inventory management through nearby storage containers.

## Features

- **Player-Owned Shops**: Create and manage your own trading shops
- **NPC Traders**: Spawn interactive NPCs to represent your shop
- **Automatic Inventory**: Shops automatically pull items from nearby chests/storage containers
- **Custom Trades**: Define custom item exchanges with configurable quantities
- **Storage Distance Control**: Configure how far storage containers can be from shop NPCs
- **Multi-Shop Support**: Players can create multiple shops
- **Persistent Data**: All shops and trades are saved across server restarts

## Installation

1. Download the latest `TaleShop-X.X.X.jar` from releases
2. Place the JAR file in your Hytale server's `mods` directory
3. Start or restart your server
4. The plugin will automatically create a configuration file at `run/mods/Leonardson_TaleShop/TaleShopConfig.json`

## Configuration

### Configuration File

**Location:** `run/mods/Leonardson_TaleShop/TaleShopConfig.json`

**Format:**
```json
{
  "StorageDistanceMode": "FIXED",
  "FixedStorageDistance": 2
}
```

### Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `StorageDistanceMode` | String | `"FIXED"` | Storage search mode: `"FIXED"` or `"WORKBENCH"` |
| `FixedStorageDistance` | Integer | `2` | Radius in blocks to search for storage (min: 1) |

#### Storage Distance Modes

- **FIXED**: Uses the fixed distance defined in `FixedStorageDistance` (default: 2 blocks)
  - Recommended for consistent behavior across all shops
  - Better for performance tuning
  
- **WORKBENCH**: Uses the game's crafting workbench default distance settings
  - Adapts to game configuration changes
  - Useful if you want shops to match crafting bench behavior

### Configuration Examples

**Increase storage distance to 5 blocks:**
```json
{
  "StorageDistanceMode": "FIXED",
  "FixedStorageDistance": 5
}
```

**Use game's workbench distance:**
```json
{
  "StorageDistanceMode": "WORKBENCH",
  "FixedStorageDistance": 2
}
```

For more configuration details, see [CONFIG.md](CONFIG.md).

## Commands

All commands start with `/shop`. Most commands require you to specify a shop name.

### Shop Management

| Command | Description | Example |
|---------|-------------|---------|
| `/shop create <name>` | Create a new shop | `/shop create MyShop` |
| `/shop rename <name> <newName>` | Rename an existing shop | `/shop rename MyShop BetterShop` |
| `/shop delete <name>` | Delete a shop | `/shop delete MyShop` |
| `/shop list` | List all your shops | `/shop list` |
| `/shop get <name>` | Get information about a shop | `/shop get MyShop` |

### NPC Management

| Command | Description | Example |
|---------|-------------|---------|
| `/shop npc spawn <name>` | Spawn an NPC trader for your shop | `/shop npc spawn MyShop` |
| `/shop npc despawn <name>` | Remove the NPC trader | `/shop npc despawn MyShop` |

### Trade Management

| Command | Description | Example |
|---------|-------------|---------|
| `/shop trade create <shopName> <inputItem> <inputQty> <outputItem> <outputQty>` | Create a new trade | `/shop trade create MyShop Ingredient_Gold 10 Tool_IronSword 1` |
| `/shop trade list <shopName>` | List all trades in a shop | `/shop trade list MyShop` |
| `/shop trade update <shopName> <tradeId> <inputItem> <inputQty> <outputItem> <outputQty>` | Update an existing trade | `/shop trade update MyShop 1 Ingredient_Gold 5 Tool_IronSword 1` |
| `/shop trade delete <shopName> <tradeId>` | Delete a trade | `/shop trade delete MyShop 1` |

## Quick Start Guide

### 1. Create a Shop

```
/shop create MyShop
```

### 2. Add Trades

```
/shop trade create MyShop Ingredient_Gold 10 Tool_IronSword 1
/shop trade create MyShop Ingredient_Diamond 5 Tool_DiamondSword 1
```

This creates trades where:
- 10 Gold → 1 Iron Sword
- 5 Diamonds → 1 Diamond Sword

### 3. Set Up Storage

Place chests within **2 blocks** (default) of where you want to spawn your NPC. Fill these chests with the items you're selling (output items).

For example:
```
     [Chest with Swords]
            |
         2 blocks
            |
      [NPC Location] ← 2 blocks → [Another Chest]
```

### 4. Spawn the NPC

Stand where you want the shop NPC to appear and run:
```
/shop npc spawn MyShop
```

### 5. Done!

Players can now right-click your NPC to view and execute trades. The shop will automatically pull items from nearby chests.

## How It Works

### Shop Storage System

When a player interacts with your shop NPC:

1. **Search**: The plugin searches for storage containers (chests) within the configured distance
2. **Check Stock**: It counts available items in those containers
3. **Display**: Shows players which trades have sufficient stock
4. **Execute**: When a trade is made, items are removed from storage and added to the buyer's inventory

### Trade Execution

When a player buys an item:

1. **Validation**: Checks if the player has enough input items
2. **Stock Check**: Verifies the shop has enough output items in nearby storage
3. **Transaction**: 
   - Removes input items from player's inventory
   - Adds output items to player's inventory
   - Removes output items from shop's storage containers
4. **Failure**: If insufficient funds or stock, the trade is cancelled

### Limitations

- Maximum **20 trades** per shop (hardcoded in `ShopRegistry.MAX_TRADES`)
- Maximum **512 entities scanned** when searching for containers (hardcoded in `ShopBuyerPage.MAX_ENTITY_SCAN`)
- Storage containers must be within configured distance (default: 2 blocks)

## Permissions

Currently, the plugin does not implement a permission system. All players can:
- Create shops
- Manage their own shops
- Trade with any shop NPC

## Data Storage

All plugin data is stored in:

**Shops & Trades:** `run/mods/Leonardson_TaleShop/shops.properties`
- Stores shop names, owners, and associated trades
- Base64 encoded shop names for safe storage
- Automatically saved after any modification

**Configuration:** `run/mods/Leonardson_TaleShop/TaleShopConfig.json`
- JSON format
- Can be edited while server is stopped

## Troubleshooting

### Shop NPC won't spawn

- Make sure the shop exists (`/shop list`)
- Check that you're using the correct shop name
- Try despawning first if it already exists

### Trades showing "Out of Stock"

- Ensure chests with output items are within the configured distance (default: 2 blocks) of the NPC
- Verify the chests contain the correct items
- Check the item IDs match exactly (case-sensitive)
- Increase `FixedStorageDistance` in config if needed

### Configuration not loading

- Check server logs for errors
- Verify JSON syntax (use a JSON validator)
- Ensure key names are capitalized (`StorageDistanceMode`, not `storageDistanceMode`)
- Try deleting the config file and restarting to regenerate defaults

### Trades not working

- Verify item IDs are correct (use game's item IDs)
- Check that quantities are positive integers
- Ensure the shop has an NPC spawned
- Confirm storage containers are close enough to the NPC

## Performance Considerations

### Storage Distance

- **Smaller distances (1-3 blocks):** Better performance, scans fewer chests
- **Larger distances (4-10 blocks):** More flexibility, but slower on servers with many shops

### Number of Shops

- Each shop scan searches for entities and blocks within the configured radius
- More shops = more potential concurrent scans
- Consider the trade-off between convenience and performance

### Recommendations

- Keep `FixedStorageDistance` at 2-3 blocks for optimal performance
- Limit shops to essential locations
- Use fewer, well-organized storage containers rather than many scattered chests

## Building from Source

### Prerequisites

- JDK 21 or higher
- Gradle (included via wrapper)

### Build Steps

```bash
# Clone the repository
git clone <repository-url>
cd TaleShop

# Build the plugin
./gradlew build

# Find the built JAR
ls build/libs/TaleShop-*.jar
```

The compiled plugin will be in `build/libs/TaleShop-X.X.X.jar`.

## Contributing

Contributions are welcome! Please feel free to:

- Report bugs by opening an issue
- Suggest features or improvements
- Submit pull requests

## License

This project is open source. See LICENSE file for details.

## Credits

**Author:** LeonardsonCC  
**Plugin:** TaleShop  
**Description:** SHOPS!

## Support

For issues, questions, or feature requests, please open an issue on the repository.

---

## Technical Details

### Architecture

- **Main Class:** `br.com.leonardson.taleshop.TaleShop`
- **Config System:** JSON-based with `PluginConfig` and `PluginConfigManager`
- **Data Persistence:** Properties file with Base64 encoding
- **UI System:** Custom UI pages for trade interactions
- **Entity System:** Registered system for trader interactable components

### Key Components

- `ShopRegistry`: Manages shop creation, deletion, and persistence
- `ShopBuyerPage`: Handles the shop UI and trade execution
- `TraderNpc`: Manages NPC spawning and despawning
- `PluginConfigManager`: Handles configuration loading and saving

### Item IDs

Item IDs must match the game's internal item identifiers. Common format:
- `Ingredient_<Name>` (e.g., `Ingredient_Gold`, `Ingredient_Diamond`)
- `Tool_<Name>` (e.g., `Tool_IronSword`, `Tool_DiamondSword`)
- `Block_<Name>` (e.g., `Block_Stone`, `Block_Wood`)

Check your server's item registry for exact IDs.
