package net.mcreator.ordeal;

import net.mcreator.ordeal.core.client.OrdealTalents;
import net.mcreator.ordeal.core.client.OrdealDraw;
import net.mcreator.ordeal.init.OrdealModKeyMappings;
import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Forge heat. The ability slot IS the charge meter — nothing new is added to
 * the HUD. The slot you are holding heats from its base upward, its glyph goes
 * dark like metal in a fire, and the five stages snap so you can release on
 * exactly the one you want.
 *
 * Only the charging slot heats. The other four stay cold steel.
 *
 * The level is mirrored from your own keybinds and the ability's hold block,
 * using the same thresholds the server runs, so it costs no packets and can
 * never lag behind the hand that is holding the key.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class ChargeMeter {

	private ChargeMeter() {}

	public static boolean ENABLED = true;

	/** Heat climbs this share of the slot per stage. */
	public static float FILL_PER_STAGE = 0.22f;
	/** Aura bleeds past the border from this stage up. */
	public static int AURA_FROM = 4;
	/** Embers start above this stage: 3 per stage beyond it. */
	public static int EMBER_FROM = 2;

	private static final int SLOTS = 5;
	private static final int[] ticks = new int[SLOTS];
	private static final boolean[] wasDown = new boolean[SLOTS];
	private static long clock = 0;

	// ---- mirror the hold ----------------------------------------------------

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (!ENABLED || p == null) { java.util.Arrays.fill(ticks, 0); return; }
		if (mc.isPaused()) return;
		clock++;

		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		int off = v.ability_Row == 2 ? 5 : 0;
		for (int i = 0; i < SLOTS; i++) {
			boolean down = key(i);
			String name = loadout(v, off + i + 1);
			OrdealTalents.Hold h = holdOf(name);
			// an ability on cooldown does not wind up - the server refuses to
			// charge it, so the meter must not pretend otherwise
			if (down && h != null && h.isCharge() && !onCooldown(p, off + i + 1)) {
				if (!wasDown[i]) ticks[i] = 0;
				if (ticks[i] < h.maxTicks()) ticks[i]++;
			} else {
				ticks[i] = 0;
			}
			wasDown[i] = down;
		}
	}

	/** Reads the CD_1..CD_10 effect for a loadout slot, same as the HUD does. */
	private static boolean onCooldown(LocalPlayer p, int slot) {
		var fx = cdEffect(slot);
		if (fx == null) return false;
		return p.hasEffect(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(fx));
	}

	private static final java.util.Map<Integer, net.minecraft.world.effect.MobEffect> CD_CACHE =
			new java.util.HashMap<>();

	private static net.minecraft.world.effect.MobEffect cdEffect(int slot) {
		if (CD_CACHE.containsKey(slot)) return CD_CACHE.get(slot);
		net.minecraft.world.effect.MobEffect found = null;
		for (var e : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.entrySet()) {
			if (!e.getKey().location().getNamespace().equals("ordeal")) continue;
			if (e.getKey().location().getPath().replace("_", "").equalsIgnoreCase("cd" + slot)) {
				found = e.getValue();
				break;
			}
		}
		// only cache a hit - the registry may not be filled on the first frame
		if (found != null) CD_CACHE.put(slot, found);
		return found;
	}

	private static boolean key(int i) {
		return switch (i) {
			case 0 -> OrdealModKeyMappings.ABILITY_1.isDown();
			case 1 -> OrdealModKeyMappings.ABILITY_2.isDown();
			case 2 -> OrdealModKeyMappings.ABILITY_3.isDown();
			case 3 -> OrdealModKeyMappings.ABILITY_4.isDown();
			default -> OrdealModKeyMappings.ABILITY_5.isDown();
		};
	}

	private static OrdealTalents.Hold holdOf(String bound) {
		if (bound == null || bound.isEmpty()) return null;
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(bound);
		return ab == null ? null : ab.hold;
	}

	private static String loadout(OrdealModVariables.PlayerVariables v, int i) {
		String s = switch (i) {
			case 1 -> v.loadout_1; case 2 -> v.loadout_2; case 3 -> v.loadout_3;
			case 4 -> v.loadout_4; case 5 -> v.loadout_5; case 6 -> v.loadout_6;
			case 7 -> v.loadout_7; case 8 -> v.loadout_8; case 9 -> v.loadout_9;
			case 10 -> v.loadout_10; default -> "";
		};
		return s == null ? "" : s;
	}

	/** Stage 0..levels for an on-screen slot. */
	public static int level(int slot, String bound) {
		if (!ENABLED || slot < 0 || slot >= SLOTS) return 0;
		OrdealTalents.Hold h = holdOf(bound);
		return h == null || !h.isCharge() ? 0 : h.levelAt(ticks[slot]);
	}

	public static int maxLevel(String bound) {
		OrdealTalents.Hold h = holdOf(bound);
		return h == null ? 0 : Math.max(1, h.levels);
	}

	/** True while this slot is heating — the HUD darkens its glyph. */
	public static boolean heating(int slot, String bound) {
		return level(slot, bound) > 0;
	}

	// ---- the ramp -----------------------------------------------------------

	/**
	 * Cold steel to white hot, passing through the talent's own accent. The
	 * accent is a swap, so every talent forges in its own colour.
	 */
	private static int heat(int accent, int stage, int max) {
		float f = max <= 0 ? 1 : Math.max(0f, Math.min(1f, stage / (float) max));
		int steel = 0xFF3A4042;
		return f < 0.6f ? mix(steel, accent, f / 0.6f)
				: mix(accent, 0xFFFFF3E0, (f - 0.6f) / 0.4f);
	}

	private static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return 0xFF000000
				| (Math.round(ar + (br - ar) * t) << 16)
				| (Math.round(ag + (bg - ag) * t) << 8)
				| Math.round(ab + (bb - ab) * t);
	}

	// ---- draw ---------------------------------------------------------------

	/** Called per ability cell, after the icon and before the cooldown sweep. */
	public static void draw(GuiGraphics g, int x, int y, int cell, int slot, String bound, int accent) {
		int stage = level(slot, bound);
		if (stage <= 0) return;
		int max = maxLevel(bound);
		boolean maxed = stage >= max;
		int col = heat(accent, stage, max);

		// stage 5 is a different KIND of signal, not just more of stage 4
		int nudge = maxed && (clock % 4 < 2) ? -1 : 0;
		int flick = maxed ? (int) (clock % 3 == 0 ? 0x18 : 0x00) : 0;

		// the glyph goes dark the moment the metal heats - BEFORE the heat, so
		// the scrim never mutes the colour it is supposed to be lit by
		OrdealDraw.rect(g, x + 1, y + 1, cell - 2, cell - 2,
				OrdealDraw.alpha(0xFF04070C, 0x8C + stage * 10));

		// heat rises from the base of the slot
		int fill = Math.min(cell - 2, Math.round(cell * FILL_PER_STAGE * stage));
		int top = y + cell - 1 - fill + nudge;
		OrdealDraw.rect(g, x + 1, top, cell - 2, fill, OrdealDraw.alpha(col, 0xE8 + flick));
		OrdealDraw.rect(g, x + 1, top, cell - 2, 1, OrdealDraw.alpha(0xFFFFFFFF, 0xB0));

		// aura bleeds past the border only once it is genuinely hot
		if (stage >= AURA_FROM) {
			OrdealDraw.outline(g, x - 1, y - 1 + nudge, cell + 2, cell + 2, OrdealDraw.alpha(col, 0x59));
			OrdealDraw.outline(g, x - 2, y - 2 + nudge, cell + 4, cell + 4, OrdealDraw.alpha(col, 0x24));
		}

		// embers off the top edge, three more for every stage past the second
		if (stage > EMBER_FROM) {
			int n = (stage - EMBER_FROM) * 3;
			for (int i = 0; i < n; i++) {
				long seed = i * 2654435761L;
				int ex = x + 3 + (int) Math.floorMod(seed / 7 + i * 5L, Math.max(1, cell - 6));
				int rise = (int) Math.floorMod(clock * 2 + seed % 17, 14L);
				int ey = y - rise + nudge;
				int a = Math.max(0, 0xCC - rise * 14);
				OrdealDraw.rect(g, ex, ey, 1, 1, OrdealDraw.alpha(col, a));
			}
		}

		// five marks so the stage is countable, not just felt
		int markW = 3, markGap = 2;
		int totalW = max * markW + (max - 1) * markGap;
		int mx = x + (cell - totalW) / 2;
		int my = y - 5 + nudge;
		for (int i = 0; i < max; i++) {
			boolean lit = i < stage;
			OrdealDraw.rect(g, mx + i * (markW + markGap), my, markW, 2,
					lit ? OrdealDraw.alpha(col, 0xFF) : 0x593A4042);
		}
	}
}