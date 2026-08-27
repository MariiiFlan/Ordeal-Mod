package net.mcreator.ordeal.core;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.mcreator.ordeal.OrdealChiColor;
import net.mcreator.ordeal.network.OrdealModVariables;

/**
 * Talent strength moves between people as points, never copies. A consensual
 * transfer gives away exactly the share the giver chose; forced extraction is
 * in OrdealExtraction and pulls one point at a time.
 */
public final class OrdealTalentFlow {

	private OrdealTalentFlow() {}

	public record Result(boolean ok, String reason) {
		public static final Result OK = new Result(true, "");
		public static Result no(String why) { return new Result(false, why); }
	}

	public static String talentIn(OrdealModVariables.PlayerVariables v, int slot) {
		String id = slot == 1 ? v.talent1_id : v.talent2_id;
		return id == null || id.isEmpty() ? "none" : id;
	}

	public static double strengthIn(OrdealModVariables.PlayerVariables v, int slot) {
		return slot == 1 ? v.talent1_strength : v.talent2_strength;
	}

	public static boolean has(OrdealModVariables.PlayerVariables v, int slot) {
		return !talentIn(v, slot).equals("none");
	}

	public static int freeSlot(OrdealModVariables.PlayerVariables v) {
		if (!has(v, 1)) return 1;
		if (!has(v, 2)) return 2;
		return 0;
	}

	public static int slotOf(OrdealModVariables.PlayerVariables v, String talentId) {
		if (talentIn(v, 1).equals(talentId)) return 1;
		if (talentIn(v, 2).equals(talentId)) return 2;
		return 0;
	}

	public static double room(OrdealModVariables.PlayerVariables v) {
		return Math.max(0, v.chiLimit - (v.talent1_strength + v.talent2_strength));
	}

	// ---- writes -------------------------------------------------------------

	public static Result grant(Player to, String talentId, double strength, String source) {
		if (talentId == null || talentId.isEmpty() || talentId.equals("none"))
			return Result.no("no talent given");
		OrdealModVariables.PlayerVariables v = to.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (slotOf(v, talentId) != 0) return Result.no("already carries " + talentId);

		int slot = freeSlot(v);
		if (slot == 0) return Result.no("both talent slots are full");

		double kept = Math.min(150, Math.min(strength, room(v)));
		write(v, slot, talentId, kept, source);
		if (slot == 1) OrdealChiColor.inherit(to, accentOf(talentId));
		// Receiving a talent is how a human becomes a Kimyo — race follows automatically.
		if (!"kimyo".equals(v.race)) v.race = "kimyo";
		v.markSyncDirty();
		return Result.OK;
	}

	/** Removes the talent and clears the loadout. Spent Talent SP is gone, not refunded. */
	public static Result strip(Player from, int slot) {
		OrdealModVariables.PlayerVariables v = from.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (!has(v, slot)) return Result.no("nothing in slot " + slot);
		write(v, slot, "none", 0, "");
		clearLoadout(v);
		v.markSyncDirty();
		return Result.OK;
	}

	/**
	 * Consensual: the giver picks a percent of their strength and those points move.
	 * Give 100% and the talent leaves you entirely. A receiver who already carries
	 * the talent stacks the points instead of gaining a second copy.
	 */
	public static Result giveShare(Player from, Player to, int slot, int percent) {
		if (percent < 1 || percent > 100) return Result.no("percent must be 1-100");
		OrdealModVariables.PlayerVariables gv = from.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (!has(gv, slot)) return Result.no("nothing in slot " + slot);

		String id = talentIn(gv, slot);
		double have = strengthIn(gv, slot);
		double offered = Math.max(1, Math.floor(have * percent / 100.0));

		OrdealModVariables.PlayerVariables rv = to.getData(OrdealModVariables.PLAYER_VARIABLES);
		int rSlot = slotOf(rv, id);
		double headroom = rSlot != 0
				? Math.min(150 - strengthIn(rv, rSlot), room(rv))
				: Math.min(150, room(rv));
		if (rSlot == 0 && freeSlot(rv) == 0)
			return Result.no(to.getGameProfile().getName() + " has no free slot");
		double moved = Math.min(offered, headroom);
		if (moved < 1) return Result.no(to.getGameProfile().getName() + " has no room");

		if (have - moved <= 0) strip(from, slot);
		else { setStrength(gv, slot, have - moved); gv.markSyncDirty(); }

		if (rSlot != 0) { setStrength(rv, rSlot, strengthIn(rv, rSlot) + moved); rv.markSyncDirty(); }
		else grant(to, id, moved, "transfer:" + from.getGameProfile().getName());

		tell(from, "§7You gave " + (int) moved + " strength of " + id.toUpperCase() + ".");
		tell(to, "§b" + id.toUpperCase() + " settled into you — +" + (int) moved + " strength.");
		return Result.OK;
	}

	/** Accent lookup that works on the server, where the talent JSON isn't loaded. */
	private static int accentOf(String talentId) {
		return switch (talentId) {
			case "ilios" -> 0xFFF2A63C;
			case "kataigida" -> 0xFF9B8CFF;
			case "hide_forge" -> 0xFF46C88C;
			case "kirin" -> 0xFFC6D94A;
			case "chichioya_no_hai" -> 0xFFB08BD1;
			case "kong" -> 0xFFE8608F;
			case "weapon_mastery" -> 0xFF6E9BE8;
			default -> 0xFF7ED8F5;
		};
	}

	// ---- internals ----------------------------------------------------------

	static void setStrength(OrdealModVariables.PlayerVariables v, int slot, double strength) {
		if (slot == 1) v.talent1_strength = strength; else v.talent2_strength = strength;
	}

	static void write(OrdealModVariables.PlayerVariables v, int slot,
			String id, double strength, String source) {
		if (slot == 1) { v.talent1_id = id; v.talent1_strength = strength; v.talent1_source = source; }
		else           { v.talent2_id = id; v.talent2_strength = strength; v.talent2_source = source; }
	}

	static void clearLoadout(OrdealModVariables.PlayerVariables v) {
		v.loadout_1 = ""; v.loadout_2 = ""; v.loadout_3 = ""; v.loadout_4 = ""; v.loadout_5 = "";
		v.loadout_6 = ""; v.loadout_7 = ""; v.loadout_8 = ""; v.loadout_9 = ""; v.loadout_10 = "";
		v.ability_select = "";
	}

	static void tell(Player p, String msg) {
		p.displayClientMessage(Component.literal(msg), false);
	}
}