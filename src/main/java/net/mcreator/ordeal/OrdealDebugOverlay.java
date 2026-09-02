package net.mcreator.ordeal;

import net.mcreator.ordeal.core.client.OrdealDraw;
import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything, on one screen. /ordealadmin debug toggles it.
 *
 * Drawn at half size and wrapped into as many columns as it takes to stay on
 * screen - the readout is ~60 lines and the HUD already owns the top of the
 * display, so at full GUI scale it ran off two edges at once.
 *
 * Only reads synced player variables and client-visible state, so it is honest
 * about what the CLIENT knows - which is exactly what you want when the bug is
 * "the server thinks one thing and my screen shows another". A value the client
 * was never told shows as its default, and that in itself is the finding.
 */
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class OrdealDebugOverlay {

	private OrdealDebugOverlay() {}

	public static boolean ENABLED = false;

	/** Text size. 0.5 is half the normal GUI scale. */
	public static float SCALE = 0.5f;

	/** Empty = every section. Otherwise only sections starting with this. */
	public static String FILTER = "";

	private static final int PAD = 4;
	private static final int LINE = 10;
	private static final int COL_GAP = 12;
	/** Nothing is allowed to be wide enough to leave the screen. */
	private static final int MAX_VALUE_PX = 200;

	/** The section rows are currently being collected into. */
	private static String section = "";

	@SubscribeEvent
	public static void onRender(RenderGuiEvent.Post event) {
		if (!ENABLED) return;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.options.hideGui || mc.screen != null) return;

		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		List<String[]> rows = new ArrayList<>();
		section = "";

		head(rows, "IDENTITY");
		row(rows, "race", v.race);
		row(rows, "family/clan", str(v.family, "-") + " / " + str(v.clan, "-"));
		row(rows, "level", num(v.level) + "  xp " + num(v.xp) + "/" + num(v.xpCap));
		row(rows, "sp", num(v.sp) + "  life " + num(v.spLifetime) + "/" + num(v.spLifetime_Cap));
		row(rows, "talent sp", num(v.talentSP) + "  life " + num(v.talentSP_Lifetime)
				+ "/" + num(v.talentSp_Lifetime_Cap));

		head(rows, "STATS");
		row(rows, "str/dur/agi", num(v.statStrength) + " / " + num(v.statDurability)
				+ " / " + num(v.statAgility));
		row(rows, "hp/chi/ctrl", num(v.statHealth) + " / " + num(v.statChi)
				+ " / " + num(v.statChiControl));
		row(rows, "perception", num(v.statPerception));

		head(rows, "CHI");
		row(rows, "chi", num(v.chi) + " / " + num(v.chiMax)
				+ (v.chiCharging > 0 ? "  §eCHARGING" : ""));
		row(rows, "limit", num(v.chiLimit) + "  concealed " + num(v.ChiConcealed));
		row(rows, "blood/roll", num(v.bloodConsumed) + " / " + num(v.spawnRandom));

		head(rows, "TALENTS");
		row(rows, "slot 1", str(v.talent1_id, "none") + "  str " + num(v.talent1_strength));
		row(rows, "  reserve", num(v.talent1_Chi) + "/" + num(v.talent1_ChiMax)
				+ "  base " + num(v.talent1_chiBase));
		row(rows, "slot 2", str(v.talent2_id, "none") + "  str " + num(v.talent2_strength));
		row(rows, "  reserve", num(v.talent2_Chi) + "/" + num(v.talent2_ChiMax)
				+ "  base " + num(v.talent2_chiBase));
		row(rows, "talentState", num(v.talentState));
		row(rows, "chargePower", num(v.chargePower));

		head(rows, "STATE");
		int stage = StageLadder.stage(p);
		row(rows, "stage", stage <= 0 ? "§8none" : "§a" + stage + "/4  " + StageLadder.stageName(p));
		row(rows, "variant", str(StageLadder.variant(p), "§cUNPICKED"));
		row(rows, "dmg mult", num(StageLadder.damageMultiplier(p)));
		row(rows, "synced", StageLadder.CLIENT_VARIANT.isEmpty() && StageLadder.CLIENT_STAGE == 0
				? "§8no packet yet" : "§astage " + StageLadder.CLIENT_STAGE
				+ " / " + str(StageLadder.CLIENT_VARIANT, "-"));

		head(rows, "COMBAT");
		row(rows, "combat mode", v.combatMode ? "§aON" : "§8off");
		row(rows, "guard", num(v.guard) + "/" + num(v.guardMax) + "  rgn " + num(v.guardRegenTick));
		row(rows, "dmg/kb", num(v.damage) + " / " + num(v.knockback));
		row(rows, "reduc/power", num(v.damageReduction) + " / " + num(v.attackPower));
		row(rows, "fighting", str(v.inCombatWith, "none"));

		head(rows, "ABILITY");
		row(rows, "selected", str(v.ability_select, "-"));
		row(rows, "name", str(v.abilityName, "-"));
		row(rows, "row", num(v.ability_Row));
		row(rows, "slots 1-5", slots(v, 1));
		row(rows, "slots 6-10", slots(v, 6));

		head(rows, "FLIGHT");
		row(rows, "style", str(v.FlightStyle, "§8-"));
		row(rows, "  idle clip", clip(v.FlightStyle, "idle"));
		row(rows, "  fly clip", clip(v.FlightStyle, "flight"));
		row(rows, "on/idle/boost", flag(v.flightOn) + " " + flag(v.flightIdle)
				+ " " + flag(v.flightBoost));
		row(rows, "tier", String.valueOf(Flight.tier(p)));
		row(rows, "throttle", bar(v.flightThrottle) + " " + num(v.flightThrottle));
		row(rows, "speed", num(v.flightSpeed) + " b/t  " + num(v.flightSpeed * 20) + " b/s");
		row(rows, "fly/mayfly", flag(p.getAbilities().flying) + " " + flag(p.getAbilities().mayfly)
				+ "  glide " + flag(p.isFallFlying()));
		row(rows, "velocity", fmt(p.getDeltaMovement().length()) + " b/t");
		row(rows, "sprint key", flag(net.mcreator.ordeal.core.OrdealInput.sprint(p))
				+ "  fwd " + flag(net.mcreator.ordeal.core.OrdealInput.forward(p)));
		row(rows, "lean roll/pitch", fmt(OrdealFlightLean.lastRoll(p)) + "\u00B0 / "
				+ fmt(OrdealFlightLean.lastPitch(p)) + "\u00B0");
		row(rows, "root authored", rootRot(p));

		head(rows, "ANIM");
		row(rows, "playing", OrdealAnimPlayback.isAnimating(p) ? "§aYES" : "§8no");
		row(rows, "weight", fmt(OrdealAnimPlayback.weight(p)));
		row(rows, "wobble", String.valueOf(OrdealAnimPlayback.wobbleLevel(p)));
		row(rows, "ground/sprint", flag(p.onGround()) + " " + flag(p.isSprinting())
				+ "  shift " + flag(p.isShiftKeyDown()));

		head(rows, "PASSIVES");
		row(rows, "off", str(Passives.CLIENT_OFF, "§8(none off)"));

		draw(event.getGuiGraphics(), rows);
	}

	// ---- drawing ------------------------------------------------------------

	private static void draw(GuiGraphics g, List<String[]> rows) {
		if (rows.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		float sc = Math.max(0.25f, Math.min(1f, SCALE));

		// from here on the coordinates are scaled, so the screen is this big
		int screenW = (int) (mc.getWindow().getGuiScaledWidth() / sc);
		int screenH = (int) (mc.getWindow().getGuiScaledHeight() / sc);

		int top = 6;
		int maxRows = Math.max(6, (screenH - top - 14) / LINE);
		List<List<String[]>> cols = split(rows, maxRows);

		// measure everything first so the whole block can be pinned to the right
		// edge - it has to hug the side it is on, not grow off it
		int n = cols.size();
		int[] labelWs = new int[n], colWs = new int[n];
		int total = 0;
		for (int i = 0; i < n; i++) {
			int labelW = 0, valueW = 0;
			for (String[] r : cols.get(i)) {
				if (r[0] == null) { valueW = Math.max(valueW, OrdealDraw.width(r[1])); continue; }
				labelW = Math.max(labelW, OrdealDraw.width(r[0]));
				valueW = Math.max(valueW, OrdealDraw.width(r[1]));
			}
			labelWs[i] = labelW;
			colWs[i] = labelW + 8 + valueW + PAD * 2;
			total += colWs[i] + (i > 0 ? COL_GAP : 0);
		}

		g.pose().pushPose();
		g.pose().scale(sc, sc, 1f);

		int x = Math.max(2, screenW - total - 4);
		for (int i = 0; i < n; i++) {
			List<String[]> col = cols.get(i);
			int labelW = labelWs[i];
			int colW = colWs[i];
			int colH = col.size() * LINE + PAD * 2;

			OrdealDraw.rect(g, x, top, colW, colH, 0xC4060A10);
			OrdealDraw.outline(g, x, top, colW, colH, OrdealDraw.alpha(0xFF7ED8F5, 0x38));

			int ty = top + PAD;
			for (String[] r : col) {
				if (r[0] == null) {
					if (!r[1].isEmpty()) OrdealDraw.text(g, r[1], x + PAD, ty, 0xFFF2A63C);
				} else {
					OrdealDraw.text(g, r[0], x + PAD, ty, OrdealDraw.INK_DIM);
					OrdealDraw.text(g, r[1], x + PAD + labelW + 8, ty, OrdealDraw.INK);
				}
				ty += LINE;
			}
			x += colW + COL_GAP;
		}

		g.pose().popPose();
	}

	/** Break into columns, preferring a section boundary near the limit. */
	private static List<List<String[]>> split(List<String[]> rows, int maxRows) {
		List<List<String[]>> out = new ArrayList<>();
		List<String[]> cur = new ArrayList<>();
		for (String[] r : rows) {
			boolean boundary = r[0] == null && r[1].isEmpty();
			if (cur.size() >= maxRows || (boundary && cur.size() >= maxRows - 5)) {
				if (!cur.isEmpty()) out.add(cur);
				cur = new ArrayList<>();
				if (boundary) continue;      // no stranded spacer at a column head
			}
			cur.add(r);
		}
		if (!cur.isEmpty()) out.add(cur);
		return out;
	}

	// ---- collection ---------------------------------------------------------

	private static boolean shown() {
		return FILTER.isEmpty()
				|| section.toLowerCase(Locale.ROOT).startsWith(FILTER.toLowerCase(Locale.ROOT));
	}

	private static void head(List<String[]> rows, String title) {
		section = title;
		if (!shown()) return;
		if (!rows.isEmpty()) rows.add(new String[] { null, "" });
		rows.add(new String[] { null, title });
	}

	private static void row(List<String[]> rows, String label, String value) {
		if (!shown()) return;
		rows.add(new String[] { label, cut(value) });
	}

	// ---- formatting ---------------------------------------------------------

	private static String cut(String v) {
		if (v == null) return "";
		if (OrdealDraw.width(v) <= MAX_VALUE_PX) return v;
		String s = v;
		while (s.length() > 4 && OrdealDraw.width(s + "..") > MAX_VALUE_PX)
			s = s.substring(0, s.length() - 1);
		return s + "..";
	}

	private static String num(double d) {
		if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
		return String.format("%.2f", d);
	}

	/**
	 * The "root" channel of whatever clip is playing - the whole-body rotation
	 * OrdealRootRender draws. "-" here while flying means no root was authored
	 * in the clip, so the real elytra glide is being used instead.
	 */
	private static String rootRot(net.minecraft.world.entity.player.Player p) {
		OrdealAnimData.Pose r = OrdealAnimPlayback.pose(p, "root");
		if (r == null) return "\u00A78-";
		return fmt(r.rx) + " / " + fmt(r.ry) + " / " + fmt(r.rz);
	}

	private static String fmt(double d) { return String.format("%.3f", d); }

	private static String flag(boolean b) { return b ? "§aY" : "§8n"; }

	private static String str(String s, String fallback) {
		return s == null || s.isEmpty() ? fallback : s;
	}

	private static String bar(double f) {
		int n = (int) Math.round(Math.max(0, Math.min(1, f)) * 8);
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < 8; i++) sb.append(i < n ? '|' : '.');
		return sb.append(']').toString();
	}

	private static String slots(OrdealModVariables.PlayerVariables v, int from) {
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < from + 5; i++) {
			String s = switch (i) {
				case 1 -> v.loadout_1; case 2 -> v.loadout_2; case 3 -> v.loadout_3;
				case 4 -> v.loadout_4; case 5 -> v.loadout_5; case 6 -> v.loadout_6;
				case 7 -> v.loadout_7; case 8 -> v.loadout_8; case 9 -> v.loadout_9;
				case 10 -> v.loadout_10; default -> "";
			};
			if (sb.length() > 0) sb.append(", ");
			sb.append(s == null || s.isEmpty() ? "-" : s);
		}
		return sb.toString();
	}

	/** Does the clip this style wants actually exist? The commonest flight bug. */
	private static String clip(String style, String suffix) {
		String stem = style == null ? "" : style.replaceAll("§.", "").trim()
				.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_")
				.replaceAll("_+", "_").replaceAll("^_|_$", "");
		if (stem.isEmpty()) stem = "default";
		String name = stem + "_" + suffix;
		return OrdealAnimStore.load(name) != null ? "§a" + name : "§c" + name + " MISSING";
	}
}