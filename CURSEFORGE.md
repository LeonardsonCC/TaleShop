# TaleShop

Create and manage your own trading shops in Hytale with NPC traders and automatic inventory management!

## What is TaleShop?

TaleShop allows players to set up custom trading shops with interactive NPC merchants. Stock your shop with items from nearby chests, set your prices, and let players trade with your NPC trader. It's perfect for creating player-driven economies on your server!

## Key Features

- **Player-Owned Shops** - Create as many shops as you want with custom trades
- **NPC Traders** - Spawn NPCs that players can interact with
- **Custom Entity Selection** - Choose from any available NPC entity type for your trader (requires permission)
- **Automatic Inventory** - Your shop pulls items from nearby chests automatically
- **Easy Management** - Use commands or a graphical UI to manage your shops
- **Remote Access** - Open any shop from anywhere without finding the NPC
- **Persistent Data** - All shops and trades are saved automatically

## How It Works

1. **Create a shop** with `/shop create <name>`
2. **Open the shop editor** with `/shop editor` to manage your shops
3. **Add trades** through the graphical interface - pick items from your inventory
4. **Spawn an NPC** trader at your desired location
5. **Place chests** nearby and stock them with your goods
6. **Done!** Players can now trade with your NPC

You can also manage your shop by **right-clicking your own NPC** to access the management interface. Other players who click your NPC will see the shopping interface.

The plugin automatically manages inventory - when players buy from your shop, items come from your chests. When they sell to you, items go into your chests.

## Getting Started

### Create Your First Shop

1. **Create a shop:**
   ```
   /shop create MyShop
   ```

2. **Open the shop editor:**
   ```
   /shop editor
   ```
   Select your shop and add trades using the graphical interface. You can pick items directly from your inventory!

3. **Spawn the NPC trader:**
   ```
   /shop npc spawn MyShop
   ```
   The NPC will appear in front of you. If you have the `taleshop.npc.selectentity` permission, you can choose which entity type to use! Otherwise, it defaults to a Klops Merchant. Right-click it anytime to manage your shop!

4. **Stock your shop:**
   Place chests within 2 blocks of the NPC and fill them with the items you're selling.

That's it! Players can now interact with your NPC to browse and purchase from your shop.

### Managing Your Shop

There are two easy ways to manage your shop:

- **Shop Editor UI**: Use `/shop editor` to see all your shops and manage them through a user-friendly interface
- **Right-click your NPC**: Click your own trader NPC to open the management menu

Both methods let you:
- Add, edit, and remove trades
- Rename or delete your shop
- Preview how customers see your shop

## Commands

All commands use `/shop` (or `/taleshop`, `/tshop`, `/barter`)

### Essential Commands

| Command | Description |
|---------|-------------|
| `/shop create <name>` | Create a new shop |
| `/shop editor` | Open the shop management interface |
| `/shop list` | View all your shops |
| `/shop npc spawn <name> [entityRole]` | Spawn your shop's NPC trader (optionally specify entity type) |
| `/shop npc despawn <name>` | Remove your shop's NPC |
| `/shop open <owner> <shop>` | Open any shop remotely |

### Other Commands

| Command | Description |
|---------|-------------|
| `/shop rename <name> <newName>` | Rename a shop |
| `/shop delete <name>` | Delete a shop |

### Full Command List

| Command | Description |
|---------|-------------|
| `/shop create <name>` | Create a new shop |
| `/shop rename <name> <newName>` | Rename a shop |
| `/shop delete <name>` | Delete a shop |
| `/shop list` | View all your shops |
| `/shop get <name>` | View details about a shop |
| `/shop editor` | Open the shop management interface |
| `/shop open <owner> <shop>` | Open a shop remotely |
| `/shop npc spawn <name> [entityRole]` | Spawn your shop's NPC trader (optional entity role) |
| `/shop npc despawn <name>` | Remove your shop's NPC |
| `/shop trade create <shopName> <inputItem> <inputQty> <outputItem> <outputQty>` | Create a new trade |
| `/shop trade list <shopName>` | List trades in a shop |
| `/shop trade update <shopName> <tradeId> <inputItem> <inputQty> <outputItem> <outputQty>` | Update a trade |
| `/shop trade delete <shopName> <tradeId>` | Delete a trade |

**Tip:** Most shop management is easier through the graphical editor (`/shop editor`) or by right-clicking your NPC!

## Permissions

| Permission | What it does |
|------------|--------------|
| `taleshop.shop.manage` | Required for all `/shop` commands (base permission on the command collection) |
| `taleshop.shop.open` | Additional permission required for `/shop open` |
| `taleshop.npc.selectentity` | Choose custom NPC entity types when spawning traders |

**Note:** Anyone can trade with NPCs - no permission needed!

## Examples

### Setting Up a Weapon Shop

```bash
# Create the shop
/shop create Weapons

# Open the editor and add your trades
/shop editor

# Spawn the NPC
/shop npc spawn Weapons
```

Then place chests near the NPC and stock them with weapons. Players can now visit your shop!

### Setting Up a Resource Exchange

```bash
# Create the shop
/shop create Resources

# Open the editor to configure trades
/shop editor

# Spawn the NPC
/shop npc spawn Resources
```

Stock your chests with gold or other currency items, and set up trades where players exchange resources for coins.

### Opening Shops Remotely

With the `taleshop.shop.manage` and `taleshop.shop.open` permissions, you can access any shop without finding the NPC:

```bash
# Browse Alice's weapon shop
/shop open Alice Weapons

# Check out Bob's resource exchange
/shop open Bob Resources
```

If you're the shop owner, you'll see the management interface. If not, you'll see the shopping interface just like clicking the NPC.

## Configuration

The plugin creates a config file at `run/mods/Leonardson_TaleShop/TaleShopConfig.json`

```json
{
  "StorageDistanceMode": "FIXED",
  "FixedStorageDistance": 2
}
```

- **StorageDistanceMode**: `FIXED` (use configured distance) or `WORKBENCH` (match game's crafting bench distance)
- **FixedStorageDistance**: How many blocks away from the NPC to search for chests (default: 2)

## Tips & Tricks

- **Use the Editor**: The `/shop editor` command provides a visual interface - much easier than remembering commands!
- **Right-Click Management**: Click your own NPC to quickly manage trades without typing commands
- **Custom Entities**: If you have the `taleshop.npc.selectentity` permission, you can choose any NPC entity type (Klops, Feran, Trork, etc.) for your trader!
- **Organize Your Storage**: Use multiple chests near your NPC for better organization
- **Stock Management**: Keep your chests stocked - trades show as "Out of Stock" when empty
- **Multiple Shops**: Create different shops for different item categories (Weapons, Potions, Resources, etc.)
- **Shop Names**: Use clear, descriptive names - they're shown to all players
- **Trade Limits**: Each shop supports up to 20 different trades
- **Pick from Inventory**: When adding trades in the editor, you can select items directly from your inventory

## Common Questions

**Q: How do I add trades to my shop?**  
A: Use `/shop editor` or right-click your own NPC to open the management interface. You can add trades through the graphical UI!

**Q: How close do chests need to be?**  
A: Within 2 blocks by default (configurable)

**Q: Can I have multiple shops?**  
A: Yes! Create as many as you want.

**Q: What happens if my chests are empty?**  
A: The trade shows as "Out of Stock" until you restock

**Q: Can other players steal from my chests?**  
A: The plugin only manages trade transactions - use your server's protection plugins for chest security

**Q: Can I move my NPC?**  
A: Yes! Despawn it with `/shop npc despawn <name>` and spawn it at the new location

**Q: How do I know what items to use in trades?**  
A: When using the editor, you can pick items directly from your inventory - no need to memorize item names!

**Q: Can I use different NPC models for my traders?**  
A: Yes! If you have the `taleshop.npc.selectentity` permission, you can choose from any available NPC entity type when spawning your trader. A searchable UI lets you preview and select the entity you want!

## Support

For issues, suggestions, or questions, please visit the project repository or leave a comment!

---

**Created by LeonardsonCC**  
**License:** MIT
