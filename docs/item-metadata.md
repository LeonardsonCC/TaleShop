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
The ID-only editor icon workaround remains. The buyer page (also used by “Preview Shop”) uses a
TaleShop-owned copy of the native barter row with a plain Button, rather than ItemSlotButton's
ID-only automatic item tooltip. Its `TooltipTextSpans` contains `ItemStack.getDisplayName()` and
`getDisplayDescription()` as native formatted messages, plus quantity, durability and the registered
quality label. This preserves translations, parameters and formatting. Icons still receive only IDs;
no raw metadata or partially constructed ItemStack is sent to a CustomUI visual slot.

Saved templates remain visible when out of stock and in admin shops. Legacy ID-only offers show
the next real stock/payment variant in the same container/slot order as extraction, and explicitly
warn that stock variants may differ. The entire offer card shows both receive/pay details on hover,
including while its purchase button is disabled.

This uses only Hytale APIs. Simple Enchantments remains entirely optional: no build dependency,
manifest dependency, plugin class lookup or special BSON key is used. Any mod that supplies native
ItemDisplay name/description metadata can supply item details. Opaque mod metadata without native
display text cannot automatically become a human-readable tooltip.

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
Preview coverage checks native formatted names/descriptions, translation keys/parameters, payment details,
legacy next-stock selection, out-of-stock/admin templates and vanilla items with no enchantments plugin
on the test classpath. Serialized commands must contain IDs/text, never raw stack metadata.
The full suite now has 33 passing tests on both Hytale 0.7.0 and the installed 0.7.2 SDK (including
four tests inherited from the NPC fix on main).

For an installed server, also run:

```sh
CI=true ./gradlew build --no-configuration-cache \
  -PhytaleServerJar=/absolute/path/to/HytaleServer.jar '-Pserver_version==0.7.2'
```

This override creates a local test artifact for 0.7.2; the project's default compile target/manifest remains 0.7.0.
Compilation and unit tests do not establish in-game compatibility or verify a specific third-party enchantment implementation.

## Manual evidence and remaining verification

On October 16, 2026, the user tested TaleShop 2.1.1-test.1 with Simple Enchantments 1.2.1 on
Hytale 0.7.2 and reported that purchased items retain their enchantments. The user also reported
that the shop preview did not show them, which prompted the native text tooltip change above.
This confirms the reported purchase behavior; it does not confirm the new tooltip or independently
validate enchantment combat effects.

A separate read-only smoke check loaded the actual local enchanted offer (without copying save data
into the repository) and confirmed that all its native description translation keys reach the new
UI command while raw enchantment metadata stays out of it. Saves were not modified.

The updated tooltip has only automated payload validation so far; no live client rendering is claimed.
Check the following in the next local test build:

1. Load the test build in a fresh server session (leave and reopen a single-player world; restart the client if necessary).
2. Hover the offer card in “Preview Shop” and the buyer page. Confirm the actual enchanted name,
   formatted enchantment descriptions, quantities, durability and quality label appear for receive/pay.
   Check out-of-stock/insufficient-payment offers too; the tooltip should remain available and purchases
   must still work by clicking the card. Verify the UI opens without a quality-background crash.
3. With Simple Enchantments installed, select an enchanted/damaged item as output and save an offer.
   Reopen the editor, change only quantities, save, then restart the world and reopen the shop.
4. Stock a nearby chest with that sample and another item of the same ID with different enchantments/durability.
   Buy the offer; check that only matching stock is consumed and the delivered item retains enchantments,
   custom name, quality and durability. Check the chest's received payment too.
5. Repeat with an existing ID-only offer, multiple chests, admin offers, full buyer inventory and full/filtered stock.
   Verify overflow drops retain metadata and refused trades do not consume payment or stock.
6. Verify enchantment effects in combat/use, not just item tooltips.

No client UI, third-party mod effects, actual drops or live-world restart has been exercised by the unit tests.

The NPC fix is now part of main (`cb9fd59`); builds of this branch include that merged base rather
than a separate cherry-pick. The PR diff continues to cover only item metadata and preview behavior.

### UI implementation references

- [Hypixel ItemSlot documentation](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemslot): ID-based versus instance rendering and the parent's tooltip handling.
- [Hypixel ItemSlotButton documentation](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemslotbutton): automatic tooltips on item children.
- The installed Hytale SDK's `ItemStack.getDisplayName/getDisplayDescription`, `ItemDisplayMetadata`,
  `Message.CODEC` and `UICommandBuilder.set(String, Message)` provide the native text path.
- [Simple Enchantments NativeTooltipManager](https://github.com/Herolias/Simple-Enchantments/blob/main/src/main/java/org/herolias/plugin/enchantment/NativeTooltipManager.java)
  writes native ItemDisplay metadata. This was inspected to establish the mechanism, not imported or called.
