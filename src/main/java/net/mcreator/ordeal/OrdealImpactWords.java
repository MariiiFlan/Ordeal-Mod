package net.mcreator.ordeal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.mcreator.ordeal.core.client.OrdealDraw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.List;

/**
 * Comic impact typography. A heavy hit prints a word over the victim — a
 * starburst behind it, a hard black outline, a punch-in that overshoots and
 * settles, then a lift and a fade.
 *
 * Drawn in screen space off OrdealVfx.project(), same as the damage numbers, so
 * there is no billboard or depth work and the word always faces you.
 *
 * Self contained: it registers its own GUI layer and its own tick. To fire one
 * by hand from anywhere on the server:
 *
 *     OrdealImpactPayload.send(victim, damage, OrdealImpactPayload.MASSIVE);
 */
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class OrdealImpactWords {

	private OrdealImpactWords() {}

	// ---- the words ----------------------------------------------------------
	// Edit freely. One row per weight class; the seed picks within a row.

	private static final String[] SOLID_WORDS = {
			"TAK!", "STAK!", "BOK!", "THWAK!", "PAK!", "TSSK!" };
	private static final String[] HEAVY_WORDS = {
			"KRAK!", "BOUK!", "WHAM!", "SHNK!", "DOOMF!", "KRUNCH!" };
	private static final String[] MASSIVE_WORDS = {
			"KA-BAM!", "KRAKOOM!", "BA-DOOM!", "SKRAAA!", "GA-BOOM!", "VWOOOM!" };

	// ---- look ---------------------------------------------------------------

	private static final int LIFE_TICKS = 26;

	private static final int INK   = 0xFF07090D;   // outline
	private static final int SOLID_COL   = 0xFFF4F7FA;
	private static final int HEAVY_COL   = 0xFFFFC24A;
	private static final int MASSIVE_COL = 0xFFFF5A3C;

	private static final float SOLID_SCALE   = 1.35f;
	private static final float HEAVY_SCALE   = 2.0f;
	private static final float MASSIVE_SCALE = 2.8f;

	/** How far past the resting size the punch-in overshoots. */
	private static final float OVERSHOOT = 0.55f;
	/** Ticks the punch-in takes. */
	private static final float PUNCH_TICKS = 3.5f;
	/** Screen pixels the word lifts over its whole life. */
	private static final float LIFT = 26f;

	private static final int MAX_LIVE = 8;

	// ---- state --------------------------------------------------------------

	private static final class Word {
		double x, y, z;
		String text;
		int colour;
		float rest, rot, drift;
		int spikes;
		int age;
	}

	private static final List<Word> LIVE = new ArrayList<>();

	public static void spawn(double x, double y, double z, float amount, int tier, int seed) {
		String[] table = switch (tier) {
			case OrdealImpactPayload.MASSIVE -> MASSIVE_WORDS;
			case OrdealImpactPayload.HEAVY   -> HEAVY_WORDS;
			case OrdealImpactPayload.SOLID   -> SOLID_WORDS;
			default -> null;
		};
		if (table == null || table.length == 0) return;
		int s = seed == Integer.MIN_VALUE ? 0 : Math.abs(seed);

		Word w = new Word();
		w.x = x; w.y = y; w.z = z;
		w.text = table[s % table.length];
		w.colour = switch (tier) {
			case OrdealImpactPayload.MASSIVE -> MASSIVE_COL;
			case OrdealImpactPayload.HEAVY -> HEAVY_COL;
			default -> SOLID_COL;
		};
		w.rest = switch (tier) {
			case OrdealImpactPayload.MASSIVE -> MASSIVE_SCALE;
			case OrdealImpactPayload.HEAVY -> HEAVY_SCALE;
			default -> SOLID_SCALE;
		};
		// a little tilt each way so two words never look stamped from one plate
		w.rot = ((s / 7) % 13) - 6f;
		w.drift = (((s / 3) % 5) - 2) * 9f;
		w.spikes = tier == OrdealImpactPayload.MASSIVE ? 14
				: tier == OrdealImpactPayload.HEAVY ? 11 : 0;

		LIVE.add(w);
		while (LIVE.size() > MAX_LIVE) LIVE.remove(0);
	}

	public static void clear() { LIVE.clear(); }

	@SubscribeEvent
	public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
		LIVE.removeIf(w -> ++w.age > LIFE_TICKS);
	}

	// ---- layer --------------------------------------------------------------

	@EventBusSubscriber(modid = "ordeal", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
	public static final class Layer {
		private Layer() {}

		@SubscribeEvent
		public static void register(RegisterGuiLayersEvent event) {
			event.registerBelow(VanillaGuiLayers.CHAT,
					ResourceLocation.fromNamespaceAndPath("ordeal", "impact_words"),
					LAYER);
		}
	}

	private static final LayeredDraw.Layer LAYER = OrdealImpactWords::draw;

	private static void draw(GuiGraphics g, DeltaTracker delta) {
		if (LIVE.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

		float partial = delta.getGameTimeDeltaPartialTick(false);
		RenderSystem.enableBlend();
		for (Word w : LIVE) drawOne(g, w, partial);
		RenderSystem.disableBlend();
	}

	private static void drawOne(GuiGraphics g, Word w, float partial) {
		float age = w.age + partial;
		float life = age / LIFE_TICKS;
		if (life > 1f) return;

		float[] p = OrdealVfx.project(new Vec3(w.x, w.y, w.z));
		if (p == null) return;

		// punch in past the resting size, then settle
		float scale;
		if (age < PUNCH_TICKS) {
			float t = age / PUNCH_TICKS;
			scale = w.rest * (0.25f + (1f + OVERSHOOT - 0.25f) * ease(t));
		} else {
			float t = Math.min(1f, (age - PUNCH_TICKS) / 5f);
			scale = w.rest * (1f + OVERSHOOT * (1f - ease(t)));
		}

		// solid for the first two thirds, then out
		int alpha = life < 0.62f ? 255 : (int) (255 * (1f - (life - 0.62f) / 0.38f));
		if (alpha <= 4) return;

		float rot = w.rot * (1f - life * 0.35f);
		int x = (int) (p[0] + w.drift);
		int y = (int) (p[1] - LIFT * ease(life));

		g.pose().pushPose();
		g.pose().translate(x, y, 0);
		g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
		g.pose().scale(scale, scale, 1f);

		int tw = OrdealDraw.width(w.text);
		int half = tw / 2;

		if (w.spikes > 0) starburst(g, half, age, alpha, w);

		// hard outline, then the fill, then a top highlight
		int ink = OrdealDraw.alpha(INK, alpha);
		for (int dx = -1; dx <= 1; dx++)
			for (int dy = -1; dy <= 1; dy++)
				if (dx != 0 || dy != 0)
					OrdealDraw.text(g, w.text, -half + dx, -4 + dy, ink);
		OrdealDraw.text(g, w.text, -half, -4, OrdealDraw.alpha(w.colour, alpha));
		OrdealDraw.text(g, w.text, -half, -5, OrdealDraw.alpha(lighten(w.colour), alpha / 3));

		g.pose().popPose();
	}

	/** Spikes radiating from behind the word. Cheap: rotated bars, no geometry. */
	private static void starburst(GuiGraphics g, int half, float age, int alpha, Word w) {
		float grow = Math.min(1f, age / 5f);
		int inner = half + 3;
		int col = OrdealDraw.alpha(w.colour, Math.max(0, alpha / 5));
		for (int i = 0; i < w.spikes; i++) {
			float a = i * (360f / w.spikes) + (i % 2) * 5f;
			int len = (int) ((half * (i % 2 == 0 ? 0.6f : 0.3f) + 5) * grow);
			if (len <= 1) continue;
			g.pose().pushPose();
			g.pose().mulPose(Axis.ZP.rotationDegrees(a));
			OrdealDraw.rect(g, -1, -inner - len, 3, len, col);
			g.pose().popPose();
		}
	}

	private static float ease(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	private static int lighten(int argb) {
		int r = Math.min(255, ((argb >> 16) & 0xFF) + 70);
		int gg = Math.min(255, ((argb >> 8) & 0xFF) + 70);
		int b = Math.min(255, (argb & 0xFF) + 70);
		return 0xFF000000 | (r << 16) | (gg << 8) | b;
	}
}