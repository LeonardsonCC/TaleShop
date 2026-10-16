# Item metadata in trades

The report by `0_mika_00` in the [CurseForge comments](https://www.curseforge.com/hytale/mods/taleshop/comments)
mentions disappearing enchantments, including Simple Enchantments, without versions or detailed reproduction steps.
Code inspection identified two independent causes: the editor/storage retained only item IDs and quantities,
and purchases removed real stock/payment but recreated both from those IDs.
The inventory selection previews also reconstruct items, but those previews do not modify inventory.

Inventory selections now snapshot the SDK ItemStack codec data separately from the preview.
JSON and SQLite store optional input/output stack data; SQLite adds nullable columns on opening an older database.
Extended BSON JSON preserves metadata types such as int64, double and binary as well as nested mod data.
Editing only quantities keeps the existing templates, even when the owner no longer carries the sample item.

New GUI offers match SDK stackability: item ID, metadata, quality and current/max durability must match the sample;
quantity is independent. Normal shops deliver the actual matching stock and deposit the actual payment stacks.
Failed removal/deposit restores the original inventory contents before delivery. Buyer overflow uses the actual
output remainder for the existing drop behavior. Admin shops generate output from the saved template.
The existing ID-only editor preview workaround remains, so previews do not show enchantments.

Old JSON/SQLite/properties offers and ID-based commands retain their ID-only matching behavior.
Normal shops still deliver the actual stock for these offers, preserving its enchantments.
Reselect the sample in the editor to restrict an old offer to a particular variant. ID-based command updates
replace templates with ID-only matching. Admin offers without saved templates still generate default items.
Metadata already discarded by previous versions cannot be recovered from old offer data.

## Automated checks

Run `CI=true ./gradlew build --no-configuration-cache` against the default Hytale 0.7.0 dependency.
The tests use real SDK codecs/containers with a minimal item asset store (no game assets or running world).
Coverage includes nested enchantment-like metadata and BSON types, durability/quality, template matching,
legacy stock/payment transfer, failed partial deposits and filtered removals with rollback, admin output,
full buyer inventory remainders, JSON/SQLite restart/update round trips and old-schema migrations.

For an installed server, also run:

```sh
CI=true ./gradlew build --no-configuration-cache \
  -PhytaleServerJar=/absolute/path/to/HytaleServer.jar '-Pserver_version==0.7.2'
```

This override creates a local test artifact for 0.7.2; the project's default compile target/manifest remains 0.7.0.
Compilation and unit tests do not establish in-game compatibility or verify a specific third-party enchantment implementation.

## Manual verification still required

1. Load the test build in a fresh server session (leave and reopen a single-player world; restart the client if necessary).
2. With Simple Enchantments installed, select an enchanted/damaged item as output and save an offer.
   Reopen the editor, change only quantities, save, then restart the world and reopen the shop.
3. Stock a nearby chest with that sample and another item of the same ID with different enchantments/durability.
   Buy the offer; check that only matching stock is consumed and the delivered item retains enchantments,
   custom name, quality and durability. Check the chest's received payment too.
4. Repeat with an existing ID-only offer, multiple chests, admin offers, full buyer inventory and full/filtered stock.
   Verify overflow drops retain metadata and refused trades do not consume payment or stock.
5. Verify enchantment effects in combat/use, not just item tooltips.

No client UI, third-party mod effects, actual drops or live-world restart has been exercised by the unit tests.
