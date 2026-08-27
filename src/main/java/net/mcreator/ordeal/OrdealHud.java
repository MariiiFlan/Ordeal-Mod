package net.mcreator.ordeal.core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.mcreator.ordeal.core.OrdealCombat;

import java.util.ArrayList;
import java.util.List;

/** K.O.D.E overlay. Passive shows health + chi; combat adds guard, ability slots and readouts. */
@EventBusSubscriber(modid = "ordeal", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class OrdealHud {

	private static final int CY      = 0xFF7ED8F5;
	private static final int CY_DIM  = 0xFF5F9CB5;
	private static final int CY_FADE = 0xFF3C6478;
	private static final int INK     = 0xFFEAF7FF;
	private static final int LABEL   = 0xFFCFE6F2;
	private static final int WHITE   = 0xFFFFFFFF;
	private static final int EMBER   = 0xFFFF8A2B;
	private static final int AMBER   = 0xFFFFB020;
	private static final int RED     = 0xFFFF3B3B;
	private static final int RED_SOFT= 0xFFFF8F8F;
	private static final int SCRIM   = 0x8C060A10;
	private static final int SCRIM_LO= 0x4D060A10;
	private static final int TRACK   = 0x66000000;

	/** Flip to false to turn the mob debug readout off. */
	public static final boolean DEBUG_SENSE_MOBS = true;

	private static final String[] KEYS = { "Z", "X", "C", "V", "B" };
	private static final int PLATE_W = 208;
	private static final int HOTBAR_CLEAR = 40;
	private static final int SENSE_W = 132;

	@SubscribeEvent
	public static void register(RegisterGuiLayersEvent event) {
		event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath("ordeal", "kode_hud"), LAYER);
	}

	private static final LayeredDraw.Layer LAYER = OrdealHud::draw;

	private static void draw(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.options.hideGui || mc.screen != null) return;
		if (p.isSpectator()) return;

		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		int w = g.guiWidth(), h = g.guiHeight();
		boolean combat = v.combatMode;

		float partial = delta.getGameTimeDeltaPartialTick(false);
		RenderSystem.enableBlend();
		net.mcreator.ordeal.OrdealVfx.drawWorld(g, partial, w, h);
		vitals(g, p, v, combat);
		plates(g, v, combat, h);
		chiBar(g, p, v, combat, w, h);
		if (combat) slots(g, v, w, h);
		combo(g, p, w, h, combat);
		dashPips(g, w, h, combat);
		net.mcreator.ordeal.AbilityCallout.draw(g, w, h, combat);
		sense(g, p, v, w);
		net.mcreator.ordeal.OrdealVfx.drawFlash(g, partial, w, h);
		RenderSystem.disableBlend();
	}

	// ---- top left: head + vitals -------------------------------------------

	private static final int HEAD = 36;
	private static final int BAR_W = 104, VAL_W = 46;

	private static void vitals(GuiGraphics g, Player p, OrdealModVariables.PlayerVariables v, boolean combat) {
		int x = 6, y = 6;

		OrdealDraw.rect(g, x, y, HEAD + 4, HEAD + 4, 0x6B142C40);
		OrdealDraw.hatch(g, x + 1, y + 1, HEAD + 2, HEAD + 2, 0x1A7ED8F5, 5);
		if (p instanceof net.minecraft.client.player.AbstractClientPlayer acp)
			PlayerFaceRenderer.draw(g, acp.getSkin(), x + 2, y + 2, HEAD);
		OrdealDraw.outline(g, x, y, HEAD + 4, HEAD + 4, 0x8C7ED8F5);
		OrdealDraw.brackets(g, x, y, HEAD + 4, HEAD + 4, 6, CY);

		int bx = x + HEAD + 8;
		int armor = p.getArmorValue();
		int rows = (combat ? 3 : 2) + (armor > 0 ? 1 : 0);
		int rowH = 10, gap = 2;
		int by = y + (HEAD + 4 - (rows * rowH + (rows - 1) * gap)) / 2;

		if (combat) {
			boolean broke = v.guard <= 0;
			boolean locked = v.guardRegenTick > 0;
			double max = Math.max(1, v.guardMax);
			row(g, bx, by, "GRD", v.guard / max,
					broke ? RED : locked ? 0x807ED8F5 : 0xD97ED8F5,
					broke ? RED : 0x8C7ED8F5,
					fmt(v.guard) + "/" + fmt(v.guardMax),
					broke ? RED_SOFT : LABEL, broke ? RED_SOFT : WHITE, 8,
					broke ? "!" : locked ? "x" : "");
			by += rowH + gap;
		}

		float hp = p.getHealth(), hpMax = Math.max(1, p.getMaxHealth());
		boolean hurt = hp / hpMax <= 0.45f;
		row(g, bx, by, "HP", hp / hpMax,
				hurt ? EMBER : 0xFFE8F4FA, 0x66FFFFFF,
				fmt(hp) + "/" + fmt(hpMax), LABEL, hurt ? 0xFFFFB877 : WHITE, 6, "");
		by += rowH + gap;

		int food = p.getFoodData().getFoodLevel();
		boolean starving = food <= 6;
		row(g, bx, by, "FOOD", food / 20.0,
				starving ? EMBER : 0xFFC9B47A, starving ? EMBER : 0x73C9B47A,
				food + "/20", starving ? 0xFFFFB877 : 0xFFC9B47A,
				starving ? 0xFFFFB877 : 0xFFE2D5AE, 6, "");
		by += rowH + gap;

		if (armor > 0)
			row(g, bx, by, "ARMR", armor / 20.0,
					0xFFB8C6D0, 0x73B8C6D0, armor + "/20", 0xFFB8C6D0, 0xFFD7E2E9, 6, "");
	}

	private static void row(GuiGraphics g, int x, int y, String label, double pct,
			int fill, int frame, String value, int labelColor, int numColor, int barH, String mark) {
		int w = 34 + BAR_W + VAL_W + 12;
		OrdealDraw.rect(g, x, y, w, 10, SCRIM_LO);
		OrdealDraw.text(g, label, x + 3, y + 1, labelColor);
		int bx = x + 34, by = y + (10 - barH) / 2;
		OrdealDraw.rect(g, bx, by, BAR_W, barH, 0x6B000000);
		int fw = (int) Math.round(Math.max(0, Math.min(1, pct)) * (BAR_W - 2));
		if (fw > 0) OrdealDraw.rect(g, bx + 1, by + 1, fw, barH - 2, fill);
		for (int i = 8; i < BAR_W; i += 8) OrdealDraw.rect(g, bx + i, by, 1, barH, 0x9E000000);
		OrdealDraw.outline(g, bx, by, BAR_W, barH, frame);
		OrdealDraw.textRight(g, value, x + w - 10, y + 1, numColor);
		if (!mark.isEmpty()) OrdealDraw.text(g, mark, x + w - 8, y + 1, CY);
	}

	// ---- bottom left: talent plates + concealment --------------------------

	private static void plates(GuiGraphics g, OrdealModVariables.PlayerVariables v, boolean combat, int h) {
		int x = 6;
		int count = 0;
		if (combat) {
			if (OrdealTalents.get(v.talent1_id) != null) count++;
			if (OrdealTalents.get(v.talent2_id) != null) count++;
		}
		boolean con = v.ChiConcealed > 0;
		int y = h - 6 - count * 16 - (con ? 12 : 0);

		if (combat) {
			y = talentPlate(g, x, y, v.talent1_id, v.talent1_strength, v.ChiConcealed);
			y = talentPlate(g, x, y, v.talent2_id, v.talent2_strength, v.ChiConcealed);
		}
		if (con) {
			String s = "[!] CONCEALED " + (int) Math.round(v.ChiConcealed * 100) + "%";
			OrdealDraw.text(g, s, x + 2, y + 2, 0xFFC9922F);
		}
	}

	private static int talentPlate(GuiGraphics g, int x, int y, String id, double str, double concealed) {
		OrdealTalents.Talent t = OrdealTalents.get(id);
		if (t == null) return y;
		String sub = "STR " + (int) str + (concealed > 0 ? " - OUTPUT " + (int) Math.round((1 - concealed) * 100) + "%" : "");
		int bw = font(t.shortName) + font(sub) + 16;
		OrdealDraw.rect(g, x, y, bw, 13, SCRIM);
		OrdealDraw.rect(g, x, y, 2, 13, t.accent);
		OrdealDraw.text(g, t.shortName, x + 6, y + 3, t.accent);
		OrdealDraw.text(g, sub, x + 10 + font(t.shortName), y + 3, 0xFF8FB6C7);
		return y + 16;
	}

	// ---- centre: chi ---------------------------------------------------------

	/** Top edge of the chi bar - anything stacking above the hotbar lines up on this. */
	public static int chiBarTop(int h, boolean combat) {
		return combat ? h - HOTBAR_CLEAR - 11 - 30 - 16 : h - HOTBAR_CLEAR - 12;
	}

	private static void chiBar(GuiGraphics g, Player p, OrdealModVariables.PlayerVariables v,
			boolean combat, int w, int h) {
		double chiMax = Math.max(1, v.chiMax);
		boolean idleFull = !combat && v.chi >= chiMax && v.chiCharging <= 0;

		int bw = combat ? 168 : 150;
		int x = (w - bw) / 2;
		int y = chiBarTop(h, combat);

		int a = idleFull ? 0x28 : 0xFF;
		OrdealDraw.rect(g, x, y, bw, 12, idleFull ? 0x14060A10 : SCRIM);
		OrdealDraw.text(g, "CHI", x + 4, y + 2, OrdealDraw.alpha(0xFF9FD4E8, a));
		String cv = Math.round(v.chi) + "/" + Math.round(chiMax);
		int tx = x + 22, tw = bw - 22 - font(cv) - 16;
		OrdealDraw.rect(g, tx, y + 2, tw, 8, OrdealDraw.alpha(0xFF000000, idleFull ? 0x20 : 0x6B));
		int fw = (int) Math.round(Math.max(0, Math.min(1, v.chi / chiMax)) * (tw - 2));
		if (fw > 0) OrdealDraw.rect(g, tx + 1, y + 3, fw, 6, OrdealDraw.alpha(CY, a));
		for (int i = 10; i < tw; i += 10) OrdealDraw.rect(g, tx + i, y + 2, 1, 8, 0x9E000000);
		OrdealDraw.outline(g, tx, y + 2, tw, 8, OrdealDraw.alpha(0xFF7ED8F5, idleFull ? 0x1C : 0x73));
		OrdealDraw.textRight(g, cv, x + bw - 12, y + 2, OrdealDraw.alpha(0xFFDFF3FB, a));
		if (v.chiCharging > 0) OrdealDraw.text(g, "^", x + bw - 8, y + 2, CY);
	}

	// ---- bottom centre: ability slots --------------------------------------

	private static void slots(GuiGraphics g, OrdealModVariables.PlayerVariables v, int w, int h) {
		int cell = 30, gap = 5;
		int total = 5 * cell + 4 * gap;
		int x0 = (w - total) / 2;
		int y = h - HOTBAR_CLEAR - 11 - cell;
		int row = v.ability_Row >= 2 ? 2 : 1;
		int offset = (row - 1) * 5;

		for (int i = 0; i < 5; i++) {
			String bound = loadout(v, offset + i + 1);
			OrdealTalents.Ability ab = bound.isEmpty() ? null : OrdealTalents.abilityByName(bound);
			OrdealTalents.Talent owner = bound.isEmpty() ? null : OrdealTalents.ownerOfName(bound);
			int x = x0 + i * (cell + gap);

			double cd = cooldown(offset + i + 1);
			double cdTotal = ab != null ? Math.max(1, ab.cdTicks * (1.0 - Math.min(0.35, v.statAgility * 0.0035))) : 1;
			boolean poor = ab != null && cd <= 0 && cost(ab, v) > v.chi;

			int accent = ab == null ? 0x4D7ED8F5 : owner != null ? owner.accent : CY;
			int frame  = ab == null ? 0x4D7ED8F5 : poor ? 0x9996A0A8 : accent;
			int bg     = ab == null ? 0x47060A10 : poor ? 0xAD14161A : SCRIM;

			OrdealDraw.rect(g, x, y, cell, cell, bg);
			if (ab != null) {
				if (ab.iconTex != null) {
					OrdealDraw.icon(g, ab.iconTex, x + 3, y + 3, cell - 6);
				} else {
					String glyph = poor ? String.valueOf((int) cost(ab, v)) : ab.icon;
					OrdealDraw.text(g, glyph, x + (cell - font(glyph)) / 2, y + (cell - 8) / 2,
							poor ? 0xFFFF5A5A : accent);
				}
			}
			// the slot itself is the charge meter - heat rises over the glyph
			net.mcreator.ordeal.ChargeMeter.draw(g, x, y, cell, i, bound, accent);
			if (cd > 0) {
				// the whole slot goes dark and the unspent part of the cooldown
				// drains downward off it, so "can't use this" reads at a glance
				// rather than having to find a small number
				double f = Math.min(1, cd / cdTotal);
				int fh = (int) Math.round(f * cell);
				OrdealDraw.rect(g, x, y, cell, cell, 0x8C05090F);
				OrdealDraw.rect(g, x, y + cell - fh, cell, fh, 0xA6040A14);
				if (fh > 0 && fh < cell)
					OrdealDraw.rect(g, x, y + cell - fh, cell, 1, OrdealDraw.alpha(accent, 0xCC));

				// seconds, centred and readable over the dim
				String t = cd >= 20 ? String.valueOf((int) Math.ceil(cd / 20.0))
						: String.format("%.1f", cd / 20.0);
				int tw = font(t);
				OrdealDraw.rect(g, x + (cell - tw) / 2 - 2, y + (cell - 8) / 2 - 1,
						tw + 4, 10, 0xB3000000);
				OrdealDraw.text(g, t, x + (cell - tw) / 2, y + (cell - 8) / 2, 0xFFEAF7FF);
			}
			OrdealDraw.outline(g, x, y, cell, cell, cd > 0 ? 0x8C5A6672 : frame);
			OrdealDraw.brackets(g, x, y, cell, cell, 5, frame);
			OrdealDraw.text(g, KEYS[i], x + (cell - font(KEYS[i])) / 2, y + cell + 3,
					ab != null ? 0xD9FFFFFF : 0x66FFFFFF);
		}

		int rx = x0 + total + 8;
		String l1 = "ROW " + row;
		String l2 = row == 1 ? "ROW 2 >" : "< ROW 1";
		int pw = Math.max(font(l1), font(l2)) + 10;
		OrdealDraw.rect(g, rx, y + cell - 24, pw, 24, 0x99060A10);
		OrdealDraw.text(g, l1, rx + (pw - font(l1)) / 2, y + cell - 21, INK);
		OrdealDraw.text(g, l2, rx + (pw - font(l2)) / 2, y + cell - 10, 0x8CEAF7FF);
	}

	private static String loadout(OrdealModVariables.PlayerVariables v, int i) {
		return switch (i) {
			case 1 -> v.loadout_1; case 2 -> v.loadout_2; case 3 -> v.loadout_3;
			case 4 -> v.loadout_4; case 5 -> v.loadout_5; case 6 -> v.loadout_6;
			case 7 -> v.loadout_7; case 8 -> v.loadout_8; case 9 -> v.loadout_9;
			case 10 -> v.loadout_10; default -> "";
		};
	}

	private static double cost(OrdealTalents.Ability a, OrdealModVariables.PlayerVariables v) {
		return Math.round(a.chi * (1.0 - Math.min(0.40, v.statChiControl * 0.004)));
	}

	private static double cooldown(int slot) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return 0;
		MobEffect e = effect("cd" + slot);
		if (e == null) return 0;
		MobEffectInstance inst = p.getEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(e));
		return inst == null ? 0 : inst.getDuration();
	}

	/**
	 * Effect elements come out of MCreator as cd_1, cd1 or CD1 depending on how
	 * they were named, so the underscores are stripped before comparing. An
	 * exact match was silently failing and taking the whole cooldown display
	 * down with it.
	 */
	private static MobEffect effect(String path) {
		String want = path.replace("_", "");
		for (var e : BuiltInRegistries.MOB_EFFECT.entrySet())
			if (e.getKey().location().getNamespace().equals("ordeal")
					&& e.getKey().location().getPath().replace("_", "").equalsIgnoreCase(want))
				return e.getValue();
		return null;
	}

	// ---- dash pips ----------------------------------------------------------
	// Three little diamonds under the hotbar centre; dim ones are recharging.
	/** Dash charges stack up the left edge of the hotbar, filling from the bottom. */
	private static void dashPips(GuiGraphics g, int w, int h, boolean combat) {
		int charges = net.mcreator.ordeal.OrdealDash.Client.charges;
		int max = net.mcreator.ordeal.OrdealDash.MAX_CHARGES;
		if (max <= 0 || (!combat && charges >= max)) return;

		int pipW = 7, pipH = 3, gap = 2;
		int x = w / 2 - 91 - pipW - 3;          // just left of the 182px hotbar
		int bottom = h - 3;
		for (int i = 0; i < max; i++) {
			int y = bottom - pipH - i * (pipH + gap);
			boolean on = i < charges;
			OrdealDraw.rect(g, x - 1, y - 1, pipW + 2, pipH + 2, 0x8C000000);
			OrdealDraw.rect(g, x, y, pipW, pipH, on ? CY : 0x4029414F);
		}
	}

	// ---- combo chain --------------------------------------------------------

	private static void combo(GuiGraphics g, Player p, int w, int h, boolean combat) {
		int n = net.mcreator.ordeal.OrdealCombo.count(p);
		if (n <= 0) return;
		int left = net.mcreator.ordeal.OrdealCombo.remaining(p);
		double frac = left / (double) net.mcreator.ordeal.OrdealCombo.WINDOW_TICKS;

		int colour = n >= 12 ? 0xFFFFFFFF : n >= 6 ? CY : 0xCCCFE6F2;
		int x = w - 6, y = combat ? h - HOTBAR_CLEAR - 11 - 30 - 40 : h - HOTBAR_CLEAR - 40;

		// The counter rides next to whoever you're hitting; the old corner
		// spot is the fallback when the target is gone or off screen.
		var mc = net.minecraft.client.Minecraft.getInstance();
		int targetId = p.getPersistentData().getInt("ordeal_combo_target");
		if (targetId > 0 && mc.level != null
				&& mc.level.getEntity(targetId) instanceof net.minecraft.world.entity.LivingEntity tle
				&& tle.isAlive()) {
			// anchor a world point just off the mob's right shoulder, so the
			// counter clears the model at any size or distance
			var camR = new org.joml.Vector3f(
					mc.gameRenderer.getMainCamera().getLeftVector()).negate();
			double off = tle.getBbWidth() * 0.5 + 0.55;
			float[] pt = net.mcreator.ordeal.OrdealVfx.project(
					tle.position().add(camR.x * off, tle.getBbHeight() * 0.85, camR.z * off));
			if (pt != null) {
				x = Math.max(74, Math.min(w - 6, (int) pt[0] + 52));
				y = Math.max(28, Math.min(h - 60, (int) pt[1] - 6));
			}
		}

		String num = String.valueOf(n);
		float scale = left > net.mcreator.ordeal.OrdealCombo.WINDOW_TICKS - 4 ? 1.9f : 1.4f;
		g.pose().pushPose();
		g.pose().translate(x - 26, y, 0);
		g.pose().scale(scale, scale, 1f);
		OrdealDraw.textRight(g, num, 0, 0, colour);
		g.pose().popPose();

		OrdealDraw.text(g, "HIT", x - 22, y + 6, colour);
		String mult = "x" + String.format("%.2f", net.mcreator.ordeal.OrdealCombo.damageMult(p));
		OrdealDraw.textRight(g, mult, x, y + 18, 0xB3FFFFFF);

		int bw = 60;
		OrdealDraw.rect(g, x - bw, y + 28, bw, 3, 0x80000000);
		int fw = (int) Math.round(bw * Math.max(0, Math.min(1, frac)));
		OrdealDraw.rect(g, x - fw, y + 28, fw, 3, colour);
	}

	// ---- right: chi sense ---------------------------------------------------

	private static void sense(GuiGraphics g, Player self, OrdealModVariables.PlayerVariables mine, int w) {
		int per = (int) mine.statPerception;

		if (DEBUG_SENSE_MOBS) {
			Entity any = lookAny(self, Math.max(12, 8 + per * 0.6));
			if (any instanceof net.minecraft.world.entity.LivingEntity le && !(any instanceof Player)) {
				mobDebug(g, le, w);
				return;
			}
		}

		if (per < 25) return;
		Player target = look(self, 8 + per * 0.6);
		if (target == null) return;

		OrdealModVariables.PlayerVariables tv = target.getData(OrdealModVariables.PLAYER_VARIABLES);
		int tier = per >= 100 ? 4 : per >= 75 ? 3 : per >= 50 ? 2 : 1;
		boolean hidden = tv.ChiConcealed >= 0.9;

		String tierLabel = hidden ? "SUPPRESSED" : switch (tier) { case 4 -> "MAX"; case 3 -> "HIGH"; case 2 -> "MID"; default -> "LOW"; };
		int frame = hidden ? AMBER : tier == 1 ? 0x667ED8F5 : CY;

		List<String[]> lines = new ArrayList<>();
		List<Integer> colours = new ArrayList<>();
		if (!hidden) {
			if (tier == 1) {
				add(lines, colours, "READING", "A CHI SIGNATURE", 0x99CFE6F2);
				add(lines, colours, "DETAIL", "PRESENCE ONLY", 0xFF41677A);
			} else {
				OrdealTalents.Talent t = OrdealTalents.get(tv.talent1_id);
				boolean kimyo = "kimyo".equals(tv.race) || t != null;
				add(lines, colours, "NAME", target.getGameProfile().getName().toUpperCase(), INK);
				add(lines, colours, "RACE", kimyo ? "KIMYO" : "HUMAN", kimyo ? (t != null ? t.accent : 0xFFF2A63C) : LABEL);
				double pres = net.mcreator.ordeal.OrdealSilhouette.presence(tv);
				add(lines, colours, "PRESENCE", net.mcreator.ordeal.OrdealSilhouette.bandName(pres),
						net.mcreator.ordeal.OrdealSilhouette.bandColour(pres));
				add(lines, colours, "CHI LEVEL", chiBand(tv), 0xFFDFF3FB);
				if (tier >= 3 && kimyo)
					add(lines, colours, "TYPE", "<" + String.join("> <", t.types) + ">", 0xFF8FB6C7);
				if (tier >= 4) {
					if (kimyo) {
						add(lines, colours, "TALENT", t.shortName, t.accent);
						add(lines, colours, "STRENGTH", (int) tv.talent1_strength + " / 100", 0xFFDFF3FB);
					}
					add(lines, colours, "CHI", pct(tv) + "%", 0xFFDFF3FB);
				}
			}
		}

		int bodyH = hidden ? 40 : lines.size() * 11;
		boolean guardPanel = tier >= 4 && !hidden && tv.guardMax > 0;
		int panelH = 8 + 12 + bodyH + (guardPanel ? 32 : 0) + 6;
		int x = w - 6 - SENSE_W;
		int y = 44;

		OrdealDraw.rect(g, x, y, SENSE_W, panelH, 0xC7060A10);
		OrdealDraw.outline(g, x, y, SENSE_W, panelH, frame);
		OrdealDraw.text(g, "CHI SENSE", x + 6, y + 5, CY_DIM);
		OrdealDraw.textRight(g, tierLabel, x + SENSE_W - 6, y + 5, frame);
		int cy = y + 18;

		if (hidden) {
			OrdealDraw.hatch(g, x + 6, cy, SENSE_W - 12, 20, 0x29FFB020, 5);
			OrdealDraw.outline(g, x + 6, cy, SENSE_W - 12, 20, 0x8CFFB020);
			String s = "[!] SIGNATURE SUPPRESSED";
			OrdealDraw.text(g, s, x + 6 + (SENSE_W - 12 - font(s)) / 2, cy + 6, AMBER);
			OrdealDraw.text(g, "A HOLE WHERE A SIGNATURE", x + 6, cy + 24, 0xFFA8703C);
			OrdealDraw.text(g, "SHOULD BE.", x + 6, cy + 33, 0xFFA8703C);
			return;
		}

		for (int i = 0; i < lines.size(); i++) {
			OrdealDraw.text(g, lines.get(i)[0], x + 6, cy + i * 11, CY_DIM);
			OrdealDraw.textRight(g, lines.get(i)[1], x + SENSE_W - 6, cy + i * 11, colours.get(i));
		}

		if (guardPanel) {
			int gy = cy + bodyH + 3;
			OrdealDraw.rect(g, x + 5, gy, SENSE_W - 10, 28, 0x1FFF8A2B);
			OrdealDraw.outline(g, x + 5, gy, SENSE_W - 10, 28, EMBER);
			OrdealDraw.text(g, "THEIR GUARD", x + 9, gy + 3, 0xFFFFB877);
			OrdealDraw.textRight(g, fmt(tv.guard) + " / " + fmt(tv.guardMax), x + SENSE_W - 9, gy + 3, 0xFFFFD7AC);
			int bw = SENSE_W - 18;
			OrdealDraw.rect(g, x + 9, gy + 13, bw, 4, 0x73000000);
			int fw = (int) Math.round(Math.max(0, Math.min(1, tv.guard / Math.max(1, tv.guardMax))) * bw);
			OrdealDraw.rect(g, x + 9, gy + 13, fw, 4, EMBER);
			OrdealDraw.text(g, "HITS UNDER " + (int) Math.round(OrdealCombat.gate(tv.statDurability)) + " BOUNCE",
					x + 9, gy + 19, 0xFFC98A52);
		}
	}

	private static void add(List<String[]> l, List<Integer> c, String k, String v, int colour) {
		l.add(new String[] { k, v });
		c.add(colour);
	}

	private static String chiBand(OrdealModVariables.PlayerVariables v) {
		int p = pct(v);
		return p >= 66 ? "HIGH" : p >= 33 ? "MODERATE" : "LOW";
	}

	private static int pct(OrdealModVariables.PlayerVariables v) {
		return (int) Math.round(100.0 * v.chi / Math.max(1, v.chiMax));
	}

	private static void mobDebug(GuiGraphics g, net.minecraft.world.entity.LivingEntity le, int w) {
		var tag = le.getPersistentData();
		double xp = tag.getDouble(net.mcreator.ordeal.OrdealMobStats.XP);
		if (xp <= 0) xp = le.getMaxHealth() * 0.5;
		double str = tag.getDouble(net.mcreator.ordeal.OrdealMobStats.STR);
		double dur = tag.getDouble(net.mcreator.ordeal.OrdealMobStats.DUR);
		String race = tag.getString(net.mcreator.ordeal.OrdealMobStats.RACE);
		boolean kimyo = "kimyo".equals(race);
		OrdealTalents.Talent t = kimyo
				? OrdealTalents.get(tag.getString(net.mcreator.ordeal.OrdealMobStats.TALENT)) : null;
		double atk = 0;
		var attr = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
		if (attr != null) atk = attr.getValue();

		List<String[]> rows = new ArrayList<>();
		List<Integer> cols = new ArrayList<>();
		add(rows, cols, "TYPE", le.getType().getDescription().getString().toUpperCase(), INK);
		add(rows, cols, "RACE", kimyo ? "KIMYO" : "HUMAN",
				kimyo ? (t != null ? t.accent : 0xFFF2A63C) : INK);
		double pres = net.mcreator.ordeal.OrdealSilhouette.presenceMob(le);
		add(rows, cols, "PRESENCE", net.mcreator.ordeal.OrdealSilhouette.bandName(pres),
				net.mcreator.ordeal.OrdealSilhouette.bandColour(pres));
		if (t != null) add(rows, cols, "TALENT", t.shortName, t.accent);
		add(rows, cols, "HP", fmt(le.getHealth()) + " / " + fmt(le.getMaxHealth()), INK);
		add(rows, cols, "ATK", atk > 0 ? fmt(atk) : "-", INK);
		if (str > 0) add(rows, cols, "STR", fmt(str), INK);
		if (dur > 0) {
			add(rows, cols, "DUR", fmt(dur), INK);
			add(rows, cols, "GUARD MAX", fmt(net.mcreator.ordeal.core.OrdealCombat.mobGuardMax(dur)), INK);
		}
		add(rows, cols, "ARMOR", String.valueOf(le.getArmorValue()), INK);
		add(rows, cols, "XP WORTH", fmt(xp), INK);

		int x = w - 6 - SENSE_W, y = 44;
		int h = 18 + rows.size() * 11 + 4;
		OrdealDraw.rect(g, x, y, SENSE_W, h, 0xC7060A10);
		OrdealDraw.outline(g, x, y, SENSE_W, h, 0xFF5FE3A0);
		OrdealDraw.text(g, "DEBUG SENSE", x + 6, y + 5, 0xFF5FE3A0);
		OrdealDraw.textRight(g, "MOB", x + SENSE_W - 6, y + 5, 0xFF5FE3A0);
		for (int i = 0; i < rows.size(); i++) {
			OrdealDraw.text(g, rows.get(i)[0], x + 6, y + 18 + i * 11, CY_DIM);
			OrdealDraw.textRight(g, rows.get(i)[1], x + SENSE_W - 6, y + 18 + i * 11, cols.get(i));
		}
	}

	private static Entity lookAny(Player self, double range) {
		Vec3 eye = self.getEyePosition();
		Vec3 dir = self.getViewVector(1.0f);
		Vec3 end = eye.add(dir.scale(range));
		AABB box = self.getBoundingBox().expandTowards(dir.scale(range)).inflate(1.0);
		Entity best = null;
		double bestDist = range * range;
		for (Entity e : self.level().getEntities(self, box,
				x -> x instanceof net.minecraft.world.entity.LivingEntity && x.isPickable())) {
			var res = e.getBoundingBox().inflate(0.4).clip(eye, end);
			if (res.isPresent()) {
				double d = eye.distanceToSqr(res.get());
				if (d < bestDist) { bestDist = d; best = e; }
			}
		}
		return best;
	}

	private static Player look(Player self, double range) {
		Vec3 eye = self.getEyePosition();
		Vec3 dir = self.getViewVector(1.0f);
		Vec3 end = eye.add(dir.scale(range));
		AABB box = self.getBoundingBox().expandTowards(dir.scale(range)).inflate(1.0);
		Player best = null;
		double bestDist = range * range;
		for (Entity e : self.level().getEntities(self, box, x -> x instanceof Player && x.isPickable())) {
			AABB hit = e.getBoundingBox().inflate(0.4);
			var res = hit.clip(eye, end);
			if (res.isPresent()) {
				double d = eye.distanceToSqr(res.get());
				if (d < bestDist) { bestDist = d; best = (Player) e; }
			}
		}
		return best;
	}

	/** The K.O.D.E block owns health, food and armour full-time. */
	@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
	public static class Vanilla {
		private static boolean shiftedOverlay = false;

		@SubscribeEvent
		public static void onLayer(RenderGuiLayerEvent.Pre event) {
			LocalPlayer p = Minecraft.getInstance().player;
			if (p == null) return;
			ResourceLocation id = event.getName();
			if (id.equals(VanillaGuiLayers.PLAYER_HEALTH)
					|| id.equals(VanillaGuiLayers.FOOD_LEVEL)
					|| id.equals(VanillaGuiLayers.ARMOR_LEVEL)
					|| id.equals(VanillaGuiLayers.AIR_LEVEL))
				event.setCanceled(true);

			// in combat mode the action bar text would sit inside the ability
			// slots, so it slides up above the chi bar instead
			if (id.equals(VanillaGuiLayers.OVERLAY_MESSAGE)
					&& p.getData(OrdealModVariables.PLAYER_VARIABLES).combatMode) {
				event.getGuiGraphics().pose().pushPose();
				event.getGuiGraphics().pose().translate(0, -48, 0);
				shiftedOverlay = true;
			}
		}

		@SubscribeEvent
		public static void onLayerPost(RenderGuiLayerEvent.Post event) {
			if (shiftedOverlay && event.getName().equals(VanillaGuiLayers.OVERLAY_MESSAGE)) {
				event.getGuiGraphics().pose().popPose();
				shiftedOverlay = false;
			}
		}
	}

	private static int font(String s) { return OrdealDraw.width(s); }

	private static String fmt(double d) {
		return d == Math.rint(d) ? String.valueOf((int) d) : String.format("%.1f", d);
	}
}