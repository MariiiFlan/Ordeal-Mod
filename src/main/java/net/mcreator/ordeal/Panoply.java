package net.mcreator.ordeal;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PANOPLY - everything worn on the body, and everything drawn from it.
 *
 * EIGHT SLOTS, SIXTEEN POINTS. A slot is a place on the body; a point is one
 * item hanging off it. FACE and HANDS take two, BACK and WAIST take four, the
 * rest take one - which is how four blades ride the back and two sit on one hip.
 *
 * CARRIED vs ACTIVE. A point holds an item either way. ACTIVE additionally
 * renders it on the model and grants its passive. Inactive is storage - the
 * spare blade you are carrying but not wearing. Toggle with a click.
 *
 * DRAWN. One point at a time can be pulled to hand. That is the Akonito swap:
 * whatever was in the hand is saved, the panoply item takes its place, and it
 * all goes back on stow. Exactly one thing is ever out, so exactly one thing
 * ever has to be put back.
 *
 * WHAT GOES WHERE IS DECIDED BY TAGS, not by code. See TAGS below - drop an
 * item into ordeal:panoply/back and it is legal on the back, no rebuild.
 *
 * STORAGE. Sixteen ItemStacks do not fit MCreator's variable system, so this
 * lives in the player's persistent data as NBT and reaches the client through
 * PanoplyPayload. No new MCreator variables are needed - see the notes at the
 * bottom for the Java calls procedures should use instead.
 */
@EventBusSubscriber(modid = "ordeal")
public final class Panoply {

	public static final int VERSION = 1;
	static { System.out.println("[ordeal] Panoply v" + VERSION + " loaded"); }

	private Panoply() {}

	// ==================== SLOTS ====================

	/** Slot order as the screen lays them out, two columns top to bottom. */
	public static final String[] SLOTS = {
			"head", "hands", "face", "waist", "shoulders", "legs", "back", "feet" };

	/** How many points each slot carries. Same order as SLOTS. */
	public static final int[] CAP = { 1, 2, 2, 4, 1, 1, 4, 1 };

	/** Display names, same order. */
	public static final String[] LABEL = {
			"HEAD", "HANDS", "FACE", "WAIST", "SHOULDERS", "LEGS", "BACK", "FEET" };

	/** Total points across every slot. 16. */
	public static final int POINTS;
	/** First flat index of each slot. */
	public static final int[] BASE;
	static {
		BASE = new int[SLOTS.length];
		int n = 0;
		for (int i = 0; i < SLOTS.length; i++) { BASE[i] = n; n += CAP[i]; }
		POINTS = n;
	}

	public static int slotIndex(String slot) {
		if (slot == null) return -1;
		String s = slot.toLowerCase(Locale.ROOT);
		for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i].equals(s)) return i;
		return -1;
	}

	/** Flat point index for slot + entry, or -1 when out of range. */
	public static int point(int slot, int entry) {
		if (slot < 0 || slot >= SLOTS.length) return -1;
		if (entry < 0 || entry >= CAP[slot]) return -1;
		return BASE[slot] + entry;
	}

	/** Which slot a flat point belongs to. */
	public static int slotOf(int pt) {
		for (int i = SLOTS.length - 1; i >= 0; i--) if (pt >= BASE[i]) return i;
		return -1;
	}

	// ==================== TAGS ====================

	/**
	 * THE TAGS TO CREATE. All under data/ordeal/tags/item/.
	 *
	 *   ordeal:panoply              master - nothing enters the menu without it
	 *   ordeal:panoply/head         legal on the head
	 *   ordeal:panoply/hands
	 *   ordeal:panoply/face
	 *   ordeal:panoply/waist
	 *   ordeal:panoply/shoulders
	 *   ordeal:panoply/legs
	 *   ordeal:panoply/back
	 *   ordeal:panoply/feet
	 *   ordeal:talent_armor         reserved for the locked lane, never placeable
	 *
	 * An item in several slot tags is legal in all of them - that is the cloak
	 * that goes on the waist OR the back, one item, your choice where it sits.
	 */
	public static final TagKey<Item> MASTER =
			ItemTags.create(ResourceLocation.fromNamespaceAndPath("ordeal", "panoply"));
	public static final TagKey<Item> TALENT_ARMOR =
			ItemTags.create(ResourceLocation.fromNamespaceAndPath("ordeal", "talent_armor"));

	private static final TagKey<Item>[] SLOT_TAG = makeSlotTags();

	@SuppressWarnings("unchecked")
	private static TagKey<Item>[] makeSlotTags() {
		TagKey<Item>[] t = new TagKey[SLOTS.length];
		for (int i = 0; i < SLOTS.length; i++)
			t[i] = ItemTags.create(ResourceLocation.fromNamespaceAndPath("ordeal", "panoply/" + SLOTS[i]));
		return t;
	}

	/**
	 * SET TO FALSE to let anything in while you are still tagging items. Handy
	 * for testing; turn it back on before anyone else plays.
	 */
	public static boolean REQUIRE_TAGS = OrdealTuning.i("panoply.require_tags", 1) != 0;

	/** Is this stack allowed at this slot? */
	public static boolean fits(ItemStack stack, int slot) {
		if (stack == null || stack.isEmpty()) return false;
		if (slot < 0 || slot >= SLOTS.length) return false;
		if (stack.is(TALENT_ARMOR)) return false;      // the locked lane owns those
		if (!REQUIRE_TAGS) return true;
		return stack.is(MASTER) && stack.is(SLOT_TAG[slot]);
	}

	/**
	 * Can this stack be pulled to hand? Anything that is in the panoply can be.
	 *
	 * There is deliberately NO drawable tag - a mask on your face is as drawable
	 * as a blade on your back, and gating it would only mean tagging every item
	 * twice. What decides whether an item is worth drawing is the item, not us.
	 */
	public static boolean drawable(ItemStack stack) {
		return stack != null && !stack.isEmpty();
	}

	/** Every slot this stack is legal in, for the "also fits" line in the tooltip. */
	public static List<String> slotsFor(ItemStack stack) {
		List<String> out = new ArrayList<>();
		if (stack == null || stack.isEmpty()) return out;
		for (int i = 0; i < SLOTS.length; i++) if (fits(stack, i)) out.add(LABEL[i]);
		return out;
	}

	// ==================== STORAGE ====================

	private static final String KEY_ITEMS  = "ordeal_panoply";
	private static final String KEY_ACTIVE = "ordeal_panoply_active";
	private static final String KEY_DRAWN  = "ordeal_panoply_drawn";
	private static final String KEY_HELD   = "ordeal_panoply_held";
	private static final String KEY_BAR    = "ordeal_panoply_bar";

	/** All sixteen points. Never null; empty points are ItemStack.EMPTY. */
	public static ItemStack[] all(Player p) {
		ItemStack[] out = new ItemStack[POINTS];
		for (int i = 0; i < POINTS; i++) out[i] = ItemStack.EMPTY;
		if (p == null) return out;
		CompoundTag root = p.getPersistentData().getCompound(KEY_ITEMS);
		ListTag list = root.getList("i", Tag.TAG_COMPOUND);
		HolderLookup.Provider reg = p.level().registryAccess();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag e = list.getCompound(i);
			int at = e.getInt("at");
			if (at < 0 || at >= POINTS) continue;
			out[at] = ItemStack.parseOptional(reg, e.getCompound("s"));
		}
		return out;
	}

	public static ItemStack get(Player p, int pt) {
		if (p == null || pt < 0 || pt >= POINTS) return ItemStack.EMPTY;
		return all(p)[pt];
	}

	private static void writeAll(Player p, ItemStack[] items) {
		CompoundTag root = new CompoundTag();
		ListTag list = new ListTag();
		HolderLookup.Provider reg = p.level().registryAccess();
		for (int i = 0; i < POINTS; i++) {
			if (items[i] == null || items[i].isEmpty()) continue;
			CompoundTag e = new CompoundTag();
			e.putInt("at", i);
			e.put("s", items[i].save(reg));
			list.add(e);
		}
		root.put("i", list);
		p.getPersistentData().put(KEY_ITEMS, root);
	}

	/** Put a stack at a point. Returns what was there, or EMPTY. */
	public static ItemStack set(Player p, int pt, ItemStack stack) {
		if (p == null || pt < 0 || pt >= POINTS) return ItemStack.EMPTY;
		ItemStack[] items = all(p);
		ItemStack old = items[pt];
		items[pt] = stack == null ? ItemStack.EMPTY : stack;
		writeAll(p, items);
		if (stack == null || stack.isEmpty()) setActive(p, pt, false);
		sync(p);
		return old;
	}

	// ---- active mask ----

	/** Points whose passive is live and whose model renders. */
	public static boolean isActive(Player p, int pt) {
		if (p == null || pt < 0 || pt >= POINTS) return false;
		if (get(p, pt).isEmpty()) return false;
		return (mask(p) & (1 << pt)) != 0;
	}

	public static int mask(Player p) {
		return p == null ? 0 : p.getPersistentData().getInt(KEY_ACTIVE);
	}

	public static void setActive(Player p, int pt, boolean on) {
		if (p == null || pt < 0 || pt >= POINTS) return;
		int m = mask(p);
		int bit = 1 << pt;
		int next = on ? (m | bit) : (m & ~bit);
		if (next == m) return;
		p.getPersistentData().putInt(KEY_ACTIVE, next);
		sync(p);
	}

	public static void toggleActive(Player p, int pt) {
		setActive(p, pt, !isActive(p, pt));
	}

	// ==================== THE EQUIP BAR ====================

	/**
	 * SIX SLOTS, and YOU say what goes in them. Drag a point onto a slot in the
	 * panoply page; the HUD bar left of your hotbar draws these, in this order,
	 * and the equip key's numbers count along them.
	 *
	 * Stored as six point indices packed five bits each - 0 means empty, and a
	 * real point is stored as pt+1. Six slots is 30 bits, so the whole bar is
	 * one int in persistent data and one VAR_INT on the wire.
	 *
	 * UNTOUCHED, the bar auto-fills with everything you are carrying, in point
	 * order. That is what you get before you have ever dragged anything, so the
	 * system works the moment you put your first item in. The instant you drag
	 * ONE slot, the auto-fill is frozen into place first and only that slot
	 * changes - dragging a blade to slot 1 must not blank out slots 2 and 3.
	 */
	public static final int BAR_SLOTS = 6;

	/**
	 * Bit 30 - "you have arranged this bar yourself, stop auto-filling it".
	 *
	 * Six slots at five bits each use bits 0..29, so this one is free. It has
	 * to exist separately from the slot values: an EMPTIED bar and a NEVER
	 * TOUCHED bar are both thirty zero bits, and without this flag clearing
	 * every slot reads as "never touched" and the auto-fill puts them all back.
	 */
	public static final int BAR_SET = 1 << 30;

	public static int packBar(int[] bar) {
		int v = 0;
		for (int i = 0; i < BAR_SLOTS; i++) {
			int pt = (bar == null || i >= bar.length) ? -1 : bar[i];
			int code = (pt < 0 || pt >= POINTS) ? 0 : pt + 1;
			v |= (code & 0x1F) << (i * 5);
		}
		return v;
	}

	public static int[] unpackBar(int v) {
		int[] out = new int[BAR_SLOTS];
		for (int i = 0; i < BAR_SLOTS; i++) {
			int code = (v >> (i * 5)) & 0x1F;
			out[i] = code == 0 ? -1 : code - 1;
		}
		return out;
	}

	/** The stored word: slots in bits 0..29, BAR_SET in bit 30. */
	public static int rawBarValue(Player p) {
		return p == null ? 0 : p.getPersistentData().getInt(KEY_BAR);
	}

	/** What is literally stored, as slots. Says nothing about BAR_SET. */
	public static int[] rawBar(Player p) {
		return unpackBar(rawBarValue(p));
	}

	/** Has the player arranged this bar, even if what they arranged is nothing? */
	public static boolean barTouched(Player p) {
		return (rawBarValue(p) & BAR_SET) != 0;
	}

	/**
	 * The bar as it should DRAW. Shared by the server and the client mirror so
	 * the HUD, the page and the equip key can never disagree about what slot 3
	 * holds. Points that are empty resolve away rather than leaving a hole you
	 * can press a number on.
	 *
	 * Takes the raw WORD, not the slots, because BAR_SET is the whole question:
	 * with it set an empty bar stays empty, without it an empty bar auto-fills.
	 */
	public static int[] resolveBar(int rawValue, ItemStack[] items) {
		int[] out = new int[BAR_SLOTS];
		for (int i = 0; i < BAR_SLOTS; i++) out[i] = -1;
		if (items == null) return out;

		if ((rawValue & BAR_SET) != 0) {
			int[] raw = unpackBar(rawValue);
			for (int i = 0; i < BAR_SLOTS; i++) {
				int pt = raw[i];
				if (pt >= 0 && pt < items.length && items[pt] != null && !items[pt].isEmpty())
					out[i] = pt;
			}
			return out;
		}
		// never arranged: everything carried, in point order
		int n = 0;
		for (int pt = 0; pt < items.length && n < BAR_SLOTS; pt++)
			if (items[pt] != null && !items[pt].isEmpty()) out[n++] = pt;
		return out;
	}

	public static int[] bar(Player p) {
		return resolveBar(rawBarValue(p), all(p));
	}

	/** Freeze the auto-fill so the first drag does not wipe the rest of the bar. */
	private static int[] barForEdit(Player p) {
		return barTouched(p) ? rawBar(p) : bar(p);
	}

	/** Always stamps BAR_SET - writing the bar IS arranging it. */
	private static void writeBar(Player p, int[] bar) {
		p.getPersistentData().putInt(KEY_BAR, packBar(bar) | BAR_SET);
		sync(p);
	}

	/** Wipe the arrangement and go back to auto-filling from what you carry. */
	public static void resetBar(Player p) {
		if (p == null) return;
		p.getPersistentData().putInt(KEY_BAR, 0);
		sync(p);
	}

	/**
	 * Drop a point onto a bar slot. Passing -1 clears the slot.
	 *
	 * A point can only be in the bar once - binding one that is already there
	 * moves it rather than duplicating it, so a number can never draw an item
	 * that is already in your hand from a different number.
	 */
	public static void setBar(Player p, int slotIdx, int pt) {
		if (p == null || slotIdx < 0 || slotIdx >= BAR_SLOTS) return;
		if (pt >= POINTS) return;
		int[] bar = barForEdit(p);
		if (pt >= 0) for (int i = 0; i < BAR_SLOTS; i++) if (bar[i] == pt) bar[i] = -1;
		bar[slotIdx] = pt < 0 ? -1 : pt;
		writeBar(p, bar);
	}

	public static void swapBar(Player p, int a, int b) {
		if (p == null) return;
		if (a < 0 || a >= BAR_SLOTS || b < 0 || b >= BAR_SLOTS || a == b) return;
		int[] bar = barForEdit(p);
		int t = bar[a]; bar[a] = bar[b]; bar[b] = t;
		writeBar(p, bar);
	}

	public static void clearBar(Player p, int slotIdx) { setBar(p, slotIdx, -1); }

	public static int countCarried(Player p) {
		ItemStack[] a = all(p);
		int n = 0;
		for (ItemStack s : a) if (!s.isEmpty()) n++;
		return n;
	}

	public static int countActive(Player p) {
		return Integer.bitCount(mask(p) & carriedMask(p));
	}

	private static int carriedMask(Player p) {
		ItemStack[] a = all(p);
		int m = 0;
		for (int i = 0; i < POINTS; i++) if (!a[i].isEmpty()) m |= (1 << i);
		return m;
	}

	// ==================== DRAW / STOW ====================

	/** The point currently in hand, or -1. */
	public static int drawnPoint(Player p) {
		if (p == null) return -1;
		if (!p.getPersistentData().contains(KEY_DRAWN)) return -1;
		return p.getPersistentData().getInt(KEY_DRAWN);
	}

	public static boolean anythingDrawn(Player p) {
		return drawnPoint(p) >= 0;
	}

	/**
	 * THE EQUIP KEY. Pull the item at this point into the main hand, saving
	 * whatever was there. Pressing it again - or drawing something else - stows.
	 *
	 * Refuses rather than dropping anything: if the hand is full and the
	 * inventory has no room, it says so and nothing moves. Nothing this system
	 * does ever puts an item on the ground.
	 */
	public static boolean draw(Player p, int pt) {
		if (!(p instanceof ServerPlayer sp)) return false;
		if (pt < 0 || pt >= POINTS) return false;

		if (drawnPoint(p) == pt) return stow(p);   // same point again = put it away
		if (anythingDrawn(p) && !stow(p)) return false;

		ItemStack want = get(p, pt);
		if (want.isEmpty()) return false;
		ItemStack held = p.getMainHandItem().copy();
		if (!held.isEmpty() && !hasRoom(p, held)) {
			say(sp, "§4no room to put your hand down");
			return false;
		}

		// hand -> saved, panoply -> hand, point emptied but remembered
		p.getPersistentData().put(KEY_HELD, held.isEmpty()
				? new CompoundTag() : (CompoundTag) held.save(p.level().registryAccess()));
		p.setItemInHand(InteractionHand.MAIN_HAND, want.copy());
		p.getPersistentData().putInt(KEY_DRAWN, pt);

		ItemStack[] items = all(p);
		items[pt] = ItemStack.EMPTY;
		writeAll(p, items);

		click(sp, 1.15f);
		sync(p);
		return true;
	}

	/** Put the drawn item back and return the saved one to the hand. */
	public static boolean stow(Player p) {
		if (!(p instanceof ServerPlayer sp)) return false;
		int pt = drawnPoint(p);
		if (pt < 0) return false;

		ItemStack inHand = p.getMainHandItem().copy();
		ItemStack[] items = all(p);
		items[pt] = inHand;                       // whatever the hand holds goes back
		writeAll(p, items);

		CompoundTag saved = p.getPersistentData().getCompound(KEY_HELD);
		ItemStack back = saved.isEmpty()
				? ItemStack.EMPTY : ItemStack.parseOptional(p.level().registryAccess(), saved);
		p.setItemInHand(InteractionHand.MAIN_HAND, back);

		p.getPersistentData().remove(KEY_DRAWN);
		p.getPersistentData().remove(KEY_HELD);
		click(sp, 0.9f);
		sync(p);
		return true;
	}

	/**
	 * SNEAK + EQUIP KEY. Take the item at this point out of the panoply entirely
	 * and put it in the inventory - the main hand first when that is free.
	 * Anything can come out this way, worn or drawable.
	 */
	public static boolean takeOut(Player p, int pt) {
		if (!(p instanceof ServerPlayer sp)) return false;
		if (pt < 0 || pt >= POINTS) return false;
		if (drawnPoint(p) == pt) stow(p);

		ItemStack item = get(p, pt);
		if (item.isEmpty()) return false;
		if (!hasRoom(p, item)) {
			say(sp, "§4your inventory is full");
			return false;
		}

		ItemStack[] items = all(p);
		items[pt] = ItemStack.EMPTY;
		writeAll(p, items);
		setActive(p, pt, false);
		give(p, item);
		click(sp, 0.8f);
		sync(p);
		return true;
	}

	/**
	 * Main hand first if it is empty, otherwise anywhere in the inventory.
	 * Never drops - hasRoom() is checked before every call.
	 */
	private static void give(Player p, ItemStack item) {
		if (p.getMainHandItem().isEmpty()) {
			p.setItemInHand(InteractionHand.MAIN_HAND, item);
			return;
		}
		if (!p.getInventory().add(item.copy()))
			p.getInventory().placeItemBackInInventory(item.copy());
	}

	/** True when this stack can land somewhere without hitting the floor. */
	public static boolean hasRoom(Player p, ItemStack item) {
		if (item == null || item.isEmpty()) return true;
		if (p.getMainHandItem().isEmpty()) return true;
		if (p.getInventory().getFreeSlot() >= 0) return true;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (ItemStack.isSameItemSameComponents(s, item) && s.getCount() < s.getMaxStackSize())
				return true;
		}
		return false;
	}

	// ==================== PLACING FROM THE SCREEN ====================

	/**
	 * Click-and-place, the same shape as the ability menu: pick a stack up in
	 * the panoply screen, click a point, it lands there. Returns false and says
	 * why when the tags refuse it.
	 */
	public static boolean place(Player p, int pt, ItemStack stack) {
		if (!(p instanceof ServerPlayer sp)) return false;
		int slot = slotOf(pt);
		if (slot < 0) return false;
		if (!fits(stack, slot)) {
			say(sp, "§8" + stack.getHoverName().getString() + " does not go on the " + LABEL[slot].toLowerCase(Locale.ROOT));
			return false;
		}
		ItemStack one = stack.copy();
		one.setCount(1);
		ItemStack old = set(p, pt, one);
		if (!old.isEmpty()) {
			if (hasRoom(p, old)) give(p, old);
			else { set(p, pt, old); say(sp, "§4no room for what is already there"); return false; }
		}
		setActive(p, pt, true);            // placing wears it; click again to just carry
		click(sp, 1.0f);
		return true;
	}

	/**
	 * Place straight out of the inventory grid drawn in the panoply page.
	 *
	 * The screen never touches the item itself - it sends the slot number and
	 * the point, and this reads the stack off the server's copy of the
	 * inventory. That way a client cannot invent a stack by lying about what
	 * was on its cursor: if the slot is empty on the server, nothing happens.
	 *
	 * Exactly ONE item moves. A stack of four blades in slot 12 leaves three
	 * behind, which is what makes "I have 3 blades, put one on each hip" work
	 * without any splitting dance.
	 */
	public static boolean placeFromInventory(Player p, int pt, int invSlot) {
		if (!(p instanceof ServerPlayer sp)) return false;
		if (invSlot < 0 || invSlot >= 36) return false;   // 0-8 hotbar, 9-35 the bag
		int slot = slotOf(pt);
		if (slot < 0) return false;

		ItemStack src = p.getInventory().getItem(invSlot);
		if (src.isEmpty()) return false;
		if (!fits(src, slot)) {
			say(sp, "\u00a78" + src.getHoverName().getString() + " does not go on the " + LABEL[slot].toLowerCase(Locale.ROOT));
			return false;
		}

		ItemStack one = src.copy();
		one.setCount(1);

		ItemStack old = set(p, pt, one);
		if (!old.isEmpty()) {
			// the point was occupied - put what was there back where this came
			// from if the source slot is about to empty, otherwise anywhere
			if (hasRoom(p, old) || src.getCount() == 1) {
				src.shrink(1);
				p.getInventory().setItem(invSlot, src.isEmpty() ? ItemStack.EMPTY : src);
				if (p.getInventory().getItem(invSlot).isEmpty()) p.getInventory().setItem(invSlot, old);
				else give(p, old);
			} else {
				set(p, pt, old);
				say(sp, "\u00a74no room for what is already there");
				return false;
			}
		} else {
			src.shrink(1);
			p.getInventory().setItem(invSlot, src.isEmpty() ? ItemStack.EMPTY : src);
		}

		setActive(p, pt, true);
		sp.getInventory().setChanged();
		sp.containerMenu.broadcastChanges();
		click(sp, 1.0f);
		return true;
	}

	// ==================== PASSIVES ====================

	/**
	 * An item grants its passives only while its point is ACTIVE. The ids come
	 * off the stack's tags, so a designer never touches Java: tag an item
	 * ordeal:panoply and give it whatever passive ids the talent system already
	 * knows, and Passives.on() answers for it like any other.
	 *
	 * Read this from a procedure with Panoply.grants(player, "id").
	 */
	public static boolean grants(Entity e, String passiveId) {
		if (!(e instanceof Player p) || passiveId == null || passiveId.isEmpty()) return false;
		ItemStack[] items = all(p);
		for (int i = 0; i < POINTS; i++) {
			if (items[i].isEmpty() || !isActive(p, i)) continue;
			if (passiveOf(items[i]).equals(passiveId)) return true;
		}
		return false;
	}

	/**
	 * The passive id an item carries. Reads the item's registry path by default
	 * - ordeal:ash_mask grants "ash_mask" - so nothing has to be registered.
	 * Swap this for a data component if you want several passives per item.
	 */
	public static String passiveOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id == null ? "" : id.getPath();
	}

	/** Every passive id live right now, for the HUD and the debug overlay. */
	public static List<String> activePassives(Player p) {
		List<String> out = new ArrayList<>();
		if (p == null) return out;
		ItemStack[] items = all(p);
		for (int i = 0; i < POINTS; i++) {
			if (items[i].isEmpty() || !isActive(p, i)) continue;
			String id = passiveOf(items[i]);
			if (!id.isEmpty() && !out.contains(id)) out.add(id);
		}
		return out;
	}

	// ==================== ODDS AND ENDS ====================

	private static void say(ServerPlayer p, String msg) {
		p.displayClientMessage(Component.literal(msg), true);
	}

	private static void click(ServerPlayer p, float pitch) {
		p.level().playSound(null, p.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
				SoundSource.PLAYERS, 0.25f, pitch);
	}

	public static void sync(Player p) {
		if (p instanceof ServerPlayer sp) PanoplyPayload.sync(sp);
	}

	/** Death and dimension change keep the panoply - it is worn, not carried. */
	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		Player oldP = event.getOriginal(), newP = event.getEntity();
		// NOTE: no reviveCaps()/invalidateCaps() - those went away with the
		// capability rework in NeoForge 1.21.1. Persistent data copies fine
		// without them; only real capabilities ever needed the revive dance.
		CompoundTag src = oldP.getPersistentData();
		CompoundTag dst = newP.getPersistentData();
		if (src.contains(KEY_ITEMS))  dst.put(KEY_ITEMS, src.getCompound(KEY_ITEMS).copy());
		if (src.contains(KEY_ACTIVE)) dst.putInt(KEY_ACTIVE, src.getInt(KEY_ACTIVE));
		// a drawn item was in the hand and is gone with the rest of the inventory
		dst.remove(KEY_DRAWN);
		dst.remove(KEY_HELD);
		sync(newP);
	}

	@SubscribeEvent
	public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
		sync(event.getEntity());
	}

	@SubscribeEvent
	public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		sync(event.getEntity());
	}
}