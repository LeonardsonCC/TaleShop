# TaleShop

A Hytale server plugin that enables players to create and manage custom trading shops with NPC traders and automatic inventory management through nearby storage containers.

## Features

- **Player-Owned Shops**: Create and manage multiple custom trading shops
- **NPC Traders**: Spawn interactive Klops Merchant NPCs to represent your shops
- **Automatic Inventory Management**: Shops automatically pull items from nearby chests/storage containers
- **Custom Trades**: Define item exchanges with configurable input/output items and quantities
- **Storage Distance Control**: Configure search radius for storage containers
- **Multi-Shop Support**: Create multiple shops per player with no hardcoded limit
- **Persistent Data**: All shops and trades saved in SQLite database
- **Interactive UI**: Full graphical interface for shop and trade management

## Requirements

- Hytale Server with NPC Plugin support
- Java 25 or higher
- Permission: `taleshop.shop.manage` (required for all shop management commands)

## Installation

1. Download the latest `TaleShop-1.0.1.jar` from releases
2. Place the JAR file in your server's `mods/` directory
3. Start or restart your server
4. Configuration will be auto-generated at `run/mods/Leonardson_TaleShop/TaleShopConfig.json`

## Building from Source

```bash
./gradlew build
```

The compiled JAR will be available at `build/libs/TaleShop-1.0.1.jar`

## Configuration

### Configuration File

**Location:** `run/mods/Leonardson_TaleShop/TaleShopConfig.json`

**Default Configuration:**
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
| `FixedStorageDistance` | Integer | `2` | Radius in blocks to search for storage (minimum: 1) |

### Storage Distance Modes

- **FIXED**: Uses the configured fixed distance (default: 2 blocks)
  - Consistent behavior across all shops
  - Better for performance tuning
  
- **WORKBENCH**: Uses the game's crafting workbench distance settings
  - Adapts to game configuration changes
  - Matches crafting bench behavior

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

## Permissions

All shop management commands require the following permission:

```
taleshop.shop.manage
```

Players without this permission cannot create or manage shops, but can still interact with shop NPCs to make trades.

## Commands

All commands use the base `/shop` command with various subcommands. **All commands require the `taleshop.shop.manage` permission.**

### Shop Management

| Command | Description | Usage |
|---------|-------------|-------|
| `/shop create <name>` | Create a new shop | `/shop create MyShop` |
| `/shop rename <name> <newName>` | Rename an existing shop | `/shop rename MyShop BetterShop` |
| `/shop delete <name>` | Delete a shop and all its trades | `/shop delete MyShop` |
| `/shop list` | List all your shops with trade counts | `/shop list` |
| `/shop get <name>` | Get detailed information about a shop | `/shop get MyShop` |
| `/shop editor` | Open the graphical shop management UI | `/shop editor` |

### NPC Management

| Command | Description | Usage |
|---------|-------------|-------|
| `/shop npc spawn <name>` | Spawn an NPC trader for your shop at your location | `/shop npc spawn MyShop` |
| `/shop npc despawn <name>` | Remove the NPC trader for your shop | `/shop npc despawn MyShop` |

### Trade Management

| Command | Description | Usage |
|---------|-------------|-------|
| `/shop trade create <shopName> <inputItem> <inputQty> <outputItem> <outputQty>` | Create a new trade in the shop (max 20 per shop) | `/shop trade create MyShop Ingredient_Gold 10 Tool_IronSword 1` |
| `/shop trade list <shopName>` | List all trades in a shop | `/shop trade list MyShop` |
| `/shop trade update <shopName> <tradeId> <inputItem> <inputQty> <outputItem> <outputQty>` | Update an existing trade | `/shop trade update MyShop 1 Ingredient_Gold 5 Tool_IronSword 1` |
| `/shop trade delete <shopName> <tradeId>` | Delete a trade from a shop | `/shop trade delete MyShop 1` |

### Command Hierarchy

```
/shop
├── create <name>
├── rename <name> <newName>
├── delete <name>
├── list
├── get <name>
├── editor
├── npc
│   ├── spawn <name>
│   └── despawn <name>
└── trade
    ├── create <shopName> <inputItem> <inputQty> <outputItem> <outputQty>
    ├── list <shopName>
    ├── update <shopName> <tradeId> <inputItem> <inputQty> <outputItem> <outputQty>
    └── delete <shopName> <tradeId>
```

## Usage Guide

### Creating Your First Shop

1. **Create a shop:**
   ```
   /shop create MyFirstShop
   ```

2. **Add a trade to your shop:**
   ```
   /shop trade create MyFirstShop Ingredient_Gold 10 Tool_IronSword 1
   ```
   This creates a trade where players give 10 gold and receive 1 iron sword.

3. **Spawn the NPC trader:**
   ```
   /shop npc spawn MyFirstShop
   ```
   The NPC will spawn 1.5 blocks in front of you.

4. **Place storage containers nearby:**
   - Place chests or other storage containers within 2 blocks of the NPC (default distance)
   - Stock them with the items you're selling (e.g., iron swords)
   - The shop will automatically pull items from these containers when players make trades

5. **Done!** Players can now interact with the NPC to view and purchase from your shop.

### Managing Multiple Shops

You can create and manage multiple shops:

```bash
/shop create Weapons
/shop create Potions
/shop create Materials
/shop list
```

### Using the Shop Editor UI

For a more user-friendly experience, use the graphical shop editor:

```
/shop editor
```

This opens an interactive UI where you can:
- Browse all your shops
- Create, edit, and delete shops
- Manage trades visually
- Configure shop settings

### Item ID Format

Items must use Hytale's internal item identifiers:

- **Ingredients:** `Ingredient_<Name>` (e.g., `Ingredient_Gold`, `Ingredient_Diamond`)
- **Tools:** `Tool_<Name>` (e.g., `Tool_IronSword`, `Tool_DiamondPickaxe`)
- **Blocks:** `Block_<Name>` (e.g., `Block_Stone`, `Block_Wood`)

Check your server's item registry for exact IDs.

## How It Works

### Trade Execution Flow

1. Player interacts with an NPC trader
2. The plugin displays all available trades from the shop
3. The plugin scans nearby storage containers (within configured distance)
4. Available stock is calculated based on items in storage
5. When a player makes a purchase:
   - Plugin validates the player has the required input items
   - Plugin verifies the shop has the output items in storage
   - Input items are removed from the player's inventory
   - Output items are transferred from storage to the player
   - The transaction is completed instantly

### Data Storage

All data is stored in an SQLite database at `run/mods/Leonardson_TaleShop/shops.db`

**Database Tables:**
- **shops** - Stores shop information (owner, name, trader UUID)
- **trades** - Stores trade definitions with foreign key relationships

The plugin automatically migrates from legacy `shops.properties` format if found.

## Limitations

- Maximum of **20 trades** per shop
- Shop names are case-insensitive
- Storage containers must be within the configured distance of the NPC
- Minimum storage distance is 1 block
- Maximum of **512 entities scanned** when searching for containers

## Examples

### Example 1: Weapon Shop

```bash
# Create the shop
/shop create WeaponShop

# Add various weapon trades
/shop trade create WeaponShop Ingredient_Gold 20 Tool_IronSword 1
/shop trade create WeaponShop Ingredient_Gold 30 Tool_IronAxe 1
/shop trade create WeaponShop Ingredient_Diamond 10 Tool_DiamondSword 1

# Spawn the trader
/shop npc spawn WeaponShop

# Place chests nearby and stock with weapons
```

### Example 2: Resource Exchange

```bash
# Create the shop
/shop create ResourceExchange

# Add resource conversion trades
/shop trade create ResourceExchange Block_Stone 64 Ingredient_Gold 5
/shop trade create ResourceExchange Block_Wood 32 Ingredient_Gold 3
/shop trade create ResourceExchange Ingredient_Coal 16 Ingredient_Diamond 1

# Spawn the trader
/shop npc spawn ResourceExchange

# Place chests nearby and stock with resources
```

### Example 3: Managing Shops

```bash
# List all your shops
/shop list

# Get details about a specific shop
/shop get WeaponShop

# Rename a shop
/shop rename WeaponShop ArmorAndWeapons

# List all trades in a shop
/shop trade list ArmorAndWeapons

# Update a trade
/shop trade update ArmorAndWeapons 1 Ingredient_Gold 15 Tool_IronSword 1

# Delete a specific trade
/shop trade delete ArmorAndWeapons 2

# Remove the NPC
/shop npc despawn ArmorAndWeapons

# Delete the entire shop
/shop delete ArmorAndWeapons
```

## Troubleshooting

### NPCs not spawning
- Ensure you have the `taleshop.shop.manage` permission
- Check that the NPC plugin is loaded on your server
- Verify there's enough space in front of you (1.5 blocks)
- Make sure the shop exists first (`/shop list`)

### Trades not working
- Ensure storage containers are within the configured distance (default: 2 blocks)
- Check that the containers have the required output items
- Verify item IDs are correct (case-sensitive)
- Confirm the shop NPC is spawned

### Shops showing "Out of Stock"
- Verify chests contain the correct items
- Check the item IDs match exactly (case-sensitive)
- Ensure chests are within configured distance of the NPC
- Increase `FixedStorageDistance` in config if needed

### Configuration not loading
- Check server logs for errors
- Verify JSON syntax (use a JSON validator)
- Ensure key names are capitalized correctly (`StorageDistanceMode`)
- Try deleting the config file and restarting to regenerate defaults

### Database errors
- Check file permissions on `run/mods/Leonardson_TaleShop/` directory
- Look for errors in server logs
- Ensure database file is not locked by another process
- The plugin will auto-migrate from legacy `shops.properties` if found

## Performance Considerations

- **Storage Distance**: Smaller distances (1-3 blocks) provide better performance
- **Number of Shops**: More shops mean more potential concurrent scans
- **Recommendations**: 
  - Keep `FixedStorageDistance` at 2-3 blocks for optimal performance
  - Use well-organized storage containers rather than many scattered chests

## License

This project is licensed under the MIT license. See [LICENSE.md](LICENSE.md) for details.

## Author

**LeonardsonCC**

## Support

For issues, questions, or contributions, please visit the project repository.
