package net.mcreator.ordeal;

import net.mcreator.ordeal.core.client.OrdealTalents;
import net.mcreator.ordeal.core.client.OrdealDraw;
import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The ability call-out. Fire something and its name flashes up over the health
 * bar in its talent's own colour, then fades.
 *
 * Nothing to wire per ability. Every cast already stamps a cooldown effect on
 * you, so this watches those timers and fires the moment one is freshly
 * applied — the ability's name and its talent's accent come straight out of
 * the talent JSON, so a new ability announces itself the day you add it.
 *
 * Denials ride the same rail. Anything a procedure sends to the ACTION BAR is
 * caught and drawn in this style instead of vanilla's flat white text, so
 * "not enough chi" and "still on cooldown" land in the same place, in the same
 * frame, as the cast that succeeded. Your gate blocks stay exactly as they
 * are — keep the § colour code and it picks the colour up from that.
 *
 * Send a message to CHAT instead of the action bar to opt out.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class AbilityCallout {

	private AbilityCallout() {}

	public static boolean ENABLED = true;
	/** Ticks at full strength before it starts leaving. */
	public static int HOLD = 26;
	/** Ticks to wipe in, and to fade out again. */
	public static int WIPE = 6;
	public static int FADE = 9;
	/**
	 * Clear space between the bottom of the call-out plate and the top of the
	 * chi bar. The plate is placed off the HUD's own stack rather than off a
	 * hard-coded height, so it can never land on the bar in either mode.
	 */
	public static int GAP = 6;

	private static final int SLOTS = 10;
	private static final double[] lastCd = new double[SLOTS + 1];

	private static String talent = "";
	private static String ability = "";
	private static int accent = OrdealDraw.CYAN;
	private static int age = -1;
	/** A denial reads as a warning: no talent line, and it sits a touch lower. */
	private static boolean denial = false;

	/** Vanilla colour codes, so the §4 already in your blocks still picks the colour. */
	private static final String CODES = "0123456789abcdef";
	private static final int[] CODE_RGB = {
			0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

	// ---- trigger ------------------------------------------------------------

	/** Show a call-out by hand, if you ever want one outside an ability cast. */
	public static void show(String talentName, String abilityName, int accentColor) {
		if (abilityName == null || abilityName.isEmpty()) return;
		talent = talentName == null ? "" : talentName;
		ability = abilityName;
		accent = accentColor;
		denial = false;
		age = 0;
	}

	/** A gate said no. One line, warning colour, same frame as a cast. */
	public static void deny(String reason, int colour) {
		if (reason == null || reason.isEmpty()) return;
		talent = "";
		ability = reason;
		accent = colour;
		denial = true;
		age = 0;
	}

	/**
	 * Every action-bar message becomes a notice. The procedure keeps sending
	 * plain text; only the way it is drawn changes.
	 */
	@SubscribeEvent
	public static void onOverlay(ClientChatReceivedEvent.System event) {
		if (!ENABLED || !event.isOverlay()) return;
		String raw = event.getMessage().getString();
		if (raw == null || raw.isBlank()) return;
		deny(strip(raw), colourOf(raw));
		event.setCanceled(true);
	}

	private static String strip(String s) {
		return s.replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "").trim();
	}

	/** First colour code in the message wins; no code falls back to a warning red. */
	private static int colourOf(String s) {
		for (int i = 0; i + 1 < s.length(); i++) {
			if (s.charAt(i) != '\u00A7') continue;
			int idx = CODES.indexOf(Character.toLowerCase(s.charAt(i + 1)));
			if (idx >= 0) return 0xFF000000 | CODE_RGB[idx];
		}
		return 0xFFFF5A5A;
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		if (!ENABLED) return;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) { java.util.Arrays.fill(lastCd, 0); age = -1; return; }
		if (mc.isPaused()) return;

		if (age >= 0) age++;
		if (age > WIPE + HOLD + FADE) age = -1;

		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		for (int slot = 1; slot <= SLOTS; slot++) {
			double now = cooldown(p, slot);
			double was = lastCd[slot];
			lastCd[slot] = now;
			// a cooldown only ever counts DOWN, so a jump upward means it was
			// just re-applied - that is the cast
			if (now > was + 1.0) announce(v, slot);
		}
	}

	private static void announce(OrdealModVariables.PlayerVariables v, int slot) {
		String bound = loadout(v, slot);
		if (bound == null || bound.isEmpty()) return;
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(bound);
		OrdealTalents.Talent owner = OrdealTalents.ownerOfName(bound);
		String name = ab != null && !ab.name.isEmpty() ? ab.name : bound;
		show(owner == null ? "" : owner.name, name, owner == null ? OrdealDraw.CYAN : owner.accent);
	}

	private static double cooldown(LocalPlayer p, int slot) {
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

	private static String loadout(OrdealModVariables.PlayerVariables v, int i) {
		return switch (i) {
			case 1 -> v.loadout_1; case 2 -> v.loadout_2; case 3 -> v.loadout_3;
			case 4 -> v.loadout_4; case 5 -> v.loadout_5; case 6 -> v.loadout_6;
			case 7 -> v.loadout_7; case 8 -> v.loadout_8; case 9 -> v.loadout_9;
			case 10 -> v.loadout_10; default -> "";
		};
	}

	// ---- draw ---------------------------------------------------------------

	public static void draw(GuiGraphics g, int w, int h, boolean combat) {
		if (!ENABLED || age < 0 || ability.isEmpty()) return;

		// wipe in, hold, fade out
		float in = WIPE <= 0 ? 1f : Math.min(1f, age / (float) WIPE);
		int outAge = age - WIPE - HOLD;
		float out = outAge <= 0 || FADE <= 0 ? 1f : Math.max(0f, 1f - outAge / (float) FADE);
		if (out <= 0f) return;
		int a = (int) (255 * out);

		String name = denial ? ability : ability.toUpperCase(java.util.Locale.ROOT);
		String tag = denial ? "" : talent.toUpperCase(java.util.Locale.ROOT);
		int nameW = OrdealDraw.width(name);
		int tagW = tag.isEmpty() ? 0 : OrdealDraw.width(tag);
		int cx = w / 2;

		// plate sits behind both lines so the text never fights the world, and
		// its bottom edge is parked GAP pixels above whatever the HUD stacks
		int plateW = Math.max(nameW, tagW) + 28;
		int plateH = tag.isEmpty() ? 18 : 28;
		int plateY = net.mcreator.ordeal.core.client.OrdealHud.chiBarTop(h, combat) - GAP - plateH;
		int y = plateY + (tag.isEmpty() ? 4 : 14);
		OrdealDraw.rect(g, cx - plateW / 2, plateY, plateW, plateH,
				OrdealDraw.alpha(0xFF060A10, (int) (0x8C * out)));

		if (!tag.isEmpty())
			OrdealDraw.text(g, tag, cx - tagW / 2, plateY + 3,
					OrdealDraw.alpha(accent, (int) (a * 0.55f)));

		OrdealDraw.text(g, name, cx - nameW / 2, y + 2, OrdealDraw.alpha(accent, a));

		// accent rule wipes out from the centre as it appears
		int ruleW = (int) ((nameW + 16) * in);
		if (ruleW > 1) {
			int ry = y + 11;
			OrdealDraw.rect(g, cx - ruleW / 2, ry, ruleW, 1, OrdealDraw.alpha(accent, a));
			OrdealDraw.rect(g, cx - ruleW / 2, ry + 1, ruleW, 1,
					OrdealDraw.alpha(accent, (int) (a * 0.25f)));
		}

		// end caps, drawn once the rule has reached them
		if (in >= 1f) {
			int capX = nameW / 2 + 12;
			OrdealDraw.rect(g, cx - capX, y + 1, 1, 11, OrdealDraw.alpha(accent, (int) (a * 0.8f)));
			OrdealDraw.rect(g, cx + capX, y + 1, 1, 11, OrdealDraw.alpha(accent, (int) (a * 0.8f)));
		}
	}
}