package net.mcreator.ordeal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.mcreator.ordeal.core.client.OrdealDraw;
import net.mcreator.ordeal.network.OrdealModVariables;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen-space combat VFX. Numbers are projected from world space into the HUD
 * layer rather than drawn as billboards, so there is no buffer or depth work —
 * they always face you and always draw.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "ordeal", value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class OrdealVfx {

	@net.neoforged.bus.api.SubscribeEvent
	public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
		tick();
	}


	private OrdealVfx() {}

	public static final int LIFE_TICKS = 22;
	public static final int FLASH_TICKS = 13;

	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY  = 0xB8D4DEE6;
	private static final int RED   = 0xFFFF3B3B;
	private static final int CHIP  = 0x6BB8C4CC;

	private static final List<Num> NUMBERS = new ArrayList<>();
	private static int flashTicks = 0;

	private static final class Num {
		int kind; double x, y, z; float amount;
		int age; float drift;
	}

	// ---- feed ---------------------------------------------------------------

	public static void number(int kind, double x, double y, double z, float amount) {
		Num n = new Num();
		n.kind = kind; n.x = x; n.y = y; n.z = z; n.amount = amount;
		// ±14px of horizontal scatter so a flurry doesn't stack into one blob
		n.drift = (NUMBERS.size() % 5 - 2) * 7f;
		NUMBERS.add(n);
		if (NUMBERS.size() > 48) NUMBERS.remove(0);
	}

	public static void flash() {
		flashTicks = FLASH_TICKS;
	}

	public static void tick() {
		if (flashTicks > 0) flashTicks--;
		NUMBERS.removeIf(n -> ++n.age > LIFE_TICKS);
	}

	// ---- draw ---------------------------------------------------------------

	/** Under the HUD: numbers and the charge edge. */
	public static void drawWorld(GuiGraphics g, float partial, int w, int h) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		chargeEdge(g, mc.player, w, h);
		mobBars(g, mc);

		for (Num n : NUMBERS) {
			float life = (n.age + partial) / LIFE_TICKS;
			if (life > 1f) continue;
			float[] p = project(new Vec3(n.x, n.y + 0.4 + life * 0.9, n.z));
			if (p == null) continue;

			int alpha = (int) (255 * Math.min(1f, (1f - life) * 2.2f));
			if (alpha <= 4) continue;

			String text = n.kind == OrdealVfxPayload.CHIP
					? String.format("%.1f", n.amount)
					: String.valueOf(Math.round(n.amount));
			int colour = switch (n.kind) {
				case OrdealVfxPayload.ABSORBED -> GREY;
				case OrdealVfxPayload.BREAK -> RED;
				case OrdealVfxPayload.CHIP -> CHIP;
				default -> WHITE;
			};
			float scale = switch (n.kind) {
				case OrdealVfxPayload.BREAK -> 1.7f;
				case OrdealVfxPayload.ABSORBED -> 0.9f;
				case OrdealVfxPayload.CHIP -> 0.62f;
				default -> 1.25f;
			};
			// a break punches out then settles
			if (n.kind == OrdealVfxPayload.BREAK && life < 0.18f)
				scale *= 1f + (0.18f - life) * 3f;

			int x = (int) (p[0] + n.drift), y = (int) p[1];
			g.pose().pushPose();
			g.pose().translate(x, y, 0);
			g.pose().scale(scale, scale, 1f);
			int tw = OrdealDraw.width(text);
			OrdealDraw.text(g, text, -tw / 2, 0, OrdealDraw.alpha(colour, alpha));
			if (n.kind == OrdealVfxPayload.BREAK) {
				int bw = OrdealDraw.width("GUARD BREAK");
				OrdealDraw.text(g, "GUARD BREAK", -bw / 2, 11, OrdealDraw.alpha(RED, alpha));
			}
			g.pose().popPose();
		}
	}

	// ---- over-mob bars ------------------------------------------------------
	// From the design mock: no name, no tags — bars plus their raw values,
	// nothing else. Guard sits above health because it breaks first; a mob
	// with no guard shows a single bar. Only visible once sense opens
	// (Perception 25+) and inside sense range. Drawn as solid segment blocks —
	// tick marks turn to mush at GUI scale.

	private static void mobBars(GuiGraphics g, Minecraft mc) {
		OrdealModVariables.PlayerVariables mine = mc.player.getData(OrdealModVariables.PLAYER_VARIABLES);
		int per = (int) mine.statPerception;
		if (per < OrdealSilhouette.MIN_PERCEPTION) return;
		/** Bars are close-quarters info: whatever your sense range, they cap here. */
		final double BAR_RANGE = 15.0;
		double range = Math.min(BAR_RANGE, 8 + per * 0.6);

		for (var e : mc.level.entitiesForRendering()) {
			if (!(e instanceof net.minecraft.world.entity.LivingEntity le) || le instanceof Player) continue;
			if (!le.isAlive() || le instanceof net.minecraft.world.entity.decoration.ArmorStand) continue;
			double dist = le.distanceTo(mc.player);
			if (dist > range) continue;
			// the glow reads through walls; the numbers don't
			if (!mc.player.hasLineOfSight(le)) continue;

			float[] pt = project(le.position().add(0, le.getBbHeight() + 0.6, 0));
			if (pt == null) continue;

			float s = (float) Math.max(0.6, Math.min(1.0, 1.12 - dist * 0.016));
			int bw = Math.round(62 * s);
			float ts = Math.max(0.5f, 0.68f * s);
			int x = (int) pt[0] - bw / 2;
			int y = (int) pt[1];

			double hp = le.getHealth(), hpMax = Math.max(1, le.getMaxHealth());
			boolean crit = hp / hpMax <= 0.25;

			double dur = le.getPersistentData().getDouble(OrdealMobStats.DUR);
			boolean hasGuard = dur > 0;
			double gMax = hasGuard ? net.mcreator.ordeal.core.OrdealCombat.mobGuardMax(dur) : 0;
			double guard = hasGuard ? net.mcreator.ordeal.core.OrdealCombat.mobGuard(le, dur) : 0;
			boolean broken = hasGuard && guard <= 0.5;
			boolean breaking = hasGuard && !broken && guard / gMax <= 0.08;
			boolean flashOn = (le.tickCount / 3) % 2 == 0;

			int gy = y;
			if (hasGuard && !broken) {
				int frame = breaking ? (flashOn ? 0xFFFF3B3B : 0x59FF3B3B) : 0x738BD9EE;
				int fill = breaking ? (flashOn ? 0xFFFF3B3B : 0x40FF3B3B) : 0xF27ED8F5;
				segBar(g, x, gy, bw, 4, guard / gMax, 6, frame, fill);
				num(g, Math.round(guard) + "/" + Math.round(gMax), x + bw + 5, gy - 1, ts,
						breaking ? 0xFFFF8F8F : 0xFFFFFFFF);
				gy += 9;
			}
			int hFill = crit ? 0xFFFF8A2B : 0xFFE8F4FA;
			segBar(g, x, gy, bw, 3, hp / hpMax, 4, 0x66FFFFFF, hFill);
			num(g, Math.round(hp) + "/" + Math.round(hpMax), x + bw + 5, gy - 1, ts,
					crit ? 0xFFFFB877 : 0xFFFFFFFF);
		}
	}

	/**
	 * One bar: black track with a thin frame, filled by solid blocks with 1px
	 * gaps — the mock's segment look. The last block clips to the exact
	 * fraction so fine damage still reads.
	 */
	private static void segBar(GuiGraphics g, int x, int y, int w, int h, double frac,
			int seg, int frame, int fill) {
		OrdealDraw.rect(g, x - 1, y - 1, w + 2, h + 2, 0xD9000000);
		OrdealDraw.outline(g, x - 1, y - 1, w + 2, h + 2, frame);
		int fw = (int) Math.round(w * Math.max(0, Math.min(1, frac)));
		for (int i = 0; i < w; i += seg + 1) {
			int block = Math.min(seg, w - i);
			int vis = Math.min(block, fw - i);
			if (vis > 0) OrdealDraw.rect(g, x + i, y, vis, h, fill);
		}
	}

	/** Right-column value with a 1px shadow so it survives sky and snow. */
	private static void num(GuiGraphics g, String t, int tx, int ty, float scale, int colour) {
		g.pose().pushPose();
		g.pose().translate(tx, ty, 0);
		g.pose().scale(scale, scale, 1f);
		OrdealDraw.text(g, t, 1, 1, 0xC7000000);
		OrdealDraw.text(g, t, 0, 0, colour);
		g.pose().popPose();
	}

	/** Over the HUD: the guard-break vignette. Never tints the vitals. */
	public static void drawFlash(GuiGraphics g, float partial, int w, int h) {
		if (flashTicks <= 0) return;
		float life = 1f - (flashTicks - partial) / FLASH_TICKS;
		float strength = life < 0.08f ? life / 0.08f : (1f - (life - 0.08f) / 0.92f);
		strength = Math.max(0f, Math.min(1f, strength));
		int peak = (int) (174 * strength);
		if (peak <= 2) return;

		// four bands, thickest at the edge, clear through the middle third
		int bandW = w / 5, bandH = h / 5;
		for (int i = 0; i < 5; i++) {
			int a = peak * (5 - i) / 6;
			int col = OrdealDraw.alpha(0xFFFF2828, a);
			int t = bandW * (i + 1) / 5, b = bandH * (i + 1) / 5;
			OrdealDraw.rect(g, bandW * i / 5, 0, t - bandW * i / 5, h, col);
			OrdealDraw.rect(g, w - t, 0, t - bandW * i / 5, h, col);
			OrdealDraw.rect(g, 0, bandH * i / 5, w, b - bandH * i / 5, col);
			OrdealDraw.rect(g, 0, h - b, w, b - bandH * i / 5, col);
		}
	}

	/** Chi charge creeping in from the screen edge, in the player's chi colour. */
	private static void chargeEdge(GuiGraphics g, Player p, int w, int h) {
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (v.chiCharging <= 0) return;

		double pct = Math.min(1, v.chi / Math.max(1, v.chiMax));
		double breathe = 1 + Math.sin(System.currentTimeMillis() / 240.0) * 0.14;
		int base = (int) (150 * pct * breathe);
		if (base <= 2) return;

		int colour = OrdealChiColor.argb(p);
		int bandW = (int) (w * (0.10 + pct * 0.06));
		int bandH = (int) (h * (0.10 + pct * 0.06));
		int steps = 6;
		for (int i = 0; i < steps; i++) {
			int a = base * (steps - i) / (steps * 3);
			int col = OrdealDraw.alpha(colour, a);
			int tw = bandW * (i + 1) / steps, th = bandH * (i + 1) / steps;
			int pw = bandW * i / steps, ph = bandH * i / steps;
			OrdealDraw.rect(g, pw, 0, tw - pw, h, col);
			OrdealDraw.rect(g, w - tw, 0, tw - pw, h, col);
			OrdealDraw.rect(g, 0, ph, w, th - ph, col);
			OrdealDraw.rect(g, 0, h - th, w, th - ph, col);
		}
	}

	// ---- world -> gui -------------------------------------------------------

	/** Null when the point is behind the camera or off screen. */
	public static float[] project(Vec3 world) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gameRenderer == null || mc.getWindow() == null) return null;

		var cam = mc.gameRenderer.getMainCamera();
		Vec3 rel = world.subtract(cam.getPosition());
		org.joml.Vector3f v = new org.joml.Vector3f((float) rel.x, (float) rel.y, (float) rel.z);

		org.joml.Vector3f fwd = new org.joml.Vector3f(cam.getLookVector());
		org.joml.Vector3f up = new org.joml.Vector3f(cam.getUpVector());
		org.joml.Vector3f right = new org.joml.Vector3f(cam.getLeftVector()).negate();

		float depth = v.dot(fwd);
		if (depth < 0.2f) return null;

		int gw = mc.getWindow().getGuiScaledWidth();
		int gh = mc.getWindow().getGuiScaledHeight();
		float aspect = (float) gw / Math.max(1, gh);
		float tanHalf = (float) Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);

		float ndcX = v.dot(right) / (depth * tanHalf * aspect);
		float ndcY = v.dot(up) / (depth * tanHalf);

		float x = gw * 0.5f * (1f + ndcX);
		float y = gh * 0.5f * (1f - ndcY);
		if (x < -100 || x > gw + 100 || y < -100 || y > gh + 100) return null;
		return new float[] { x, y };
	}
}