package net.mcreator.ordeal;

import net.minecraft.world.entity.player.Player;
import net.mcreator.ordeal.core.OrdealFx;
import net.mcreator.ordeal.network.OrdealModVariables;

/**
 * A player's chi colour. Inherited from their first talent the moment they gain
 * one, and stored as a hex string so it can be re-pointed later without touching
 * the talent.
 *
 * When you add a {@code chiColor} String player variable in MCreator, swap the two
 * bodies below to read and write {@code v.chiColor} and everything downstream
 * follows — the HUD, the charge edge and the VFX all go through here.
 */
public final class OrdealChiColor {

	private OrdealChiColor() {}

	public static final String DEFAULT = "#7ED8F5";
	private static final String KEY = "ordeal_chi_color";

	/** Stored override, or "" when the player has never been assigned one. */
	public static String stored(Player p) {
		return p == null ? "" : p.getPersistentData().getString(KEY);
	}

	public static void set(Player p, String hex) {
		if (p == null || hex == null || hex.isEmpty()) return;
		p.getPersistentData().putString(KEY, hex.startsWith("#") ? hex : "#" + hex);
	}

	/** Assign from the talent they just gained; never overwrites a colour they already carry. */
	public static void inherit(Player p, int accentArgb) {
		if (p == null || !stored(p).isEmpty()) return;
		set(p, String.format("#%06X", accentArgb & 0xFFFFFF));
	}

	/**
	 * Packed ARGB for rendering. Server-side this reads the stored string;
	 * client-side it falls back to the talent accent, which is already synced.
	 */
	public static int argb(Player p) {
		String s = stored(p);
		if (!s.isEmpty()) return OrdealFx.argb(s);
		return fromTalent(p);
	}

	private static int fromTalent(Player p) {
		if (p == null) return OrdealFx.argb(DEFAULT);
		if (!p.level().isClientSide()) return OrdealFx.argb(DEFAULT);
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		var t = net.mcreator.ordeal.core.client.OrdealTalents.get(v.talent1_id);
		return t != null ? t.accent : OrdealFx.argb(DEFAULT);
	}
}