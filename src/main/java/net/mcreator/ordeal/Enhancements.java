package net.mcreator.ordeal;

import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Locale;

@EventBusSubscriber(modid = "ordeal")
public final class Enhancements {

	public static double UNLOCK_STRENGTH = OrdealTuning.d("enhancement.unlock_strength", 150);
	public static boolean PROMPT_ON_REACH = OrdealTuning.i("enhancement.prompt_on_reach", 1) != 0;

	public static final String INVINCIBLE = "invincible";
	public static final String SEMI       = "semi_immortality";
	public static final String OVERDRIVE  = "overdrive";

	public static final String[] ALL = { INVINCIBLE, SEMI, OVERDRIVE };

	public static String[] NAMES = { "INVINCIBLE", "SEMI-IMMORTALITY", "OVERDRIVE" };

	public static String[] HEADLINES = {
			"NOTHING REACHES YOU",
			"CHI IS YOUR BLOOD",
			"BEYOND THE CEILING" };

	public static String[] BLURBS = {
			"Turn it on and nothing can touch you. Every blow passes straight through. It burns chi the whole time it is up and it ends when your chi runs out, not when you decide.",
			"Wounds stop taking your health and start taking your chi. A blow that would kill you is paid for out of your chi instead, one point for every point of damage. When there is nothing left to spend, you die like anyone else.",
			"A temporary form that pushes your talent past its own ceiling. Every ability you own scales harder for as long as it holds. It burns chi fast and locks out for a long while once it drops." };

	public static String[] COSTS = {
			"drains while active",
			"passive · your chi pays for every wound",
			"drains while active · long lockout after" };

	private static final String KEY = "ordeal_enhancement";
	private static String CLIENT = "";

	private Enhancements() {}

	public static String talentAt(Entity e, int slot) {
		if (!(e instanceof Player p)) return "";
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		String id = slot == 2 ? v.talent2_id : v.talent1_id;
		return id == null || id.isEmpty() || id.equals("none") ? "" : id;
	}

	public static double strengthAt(Entity e, int slot) {
		if (!(e instanceof Player p)) return 0;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		return slot == 2 ? v.talent2_strength : v.talent1_strength;
	}

	public static String picked(Entity e) {
		if (!(e instanceof Player p)) return "";
		if (p.level().isClientSide()) return CLIENT;
		String s = p.getPersistentData().getString(KEY);
		return s == null ? "" : s;
	}

	public static boolean any(Entity e) {
		return !picked(e).isEmpty();
	}

	public static boolean bonded(Entity e, String enhancementId) {
		return enhancementId != null
				&& enhancementId.toLowerCase(Locale.ROOT).trim().equals(picked(e));
	}

	public static boolean has(Entity e, String enhancementId) {
		return bonded(e, enhancementId);
	}

	public static double bestStrength(Entity e) {
		return Math.max(strengthAt(e, 1), strengthAt(e, 2));
	}

	public static boolean eligible(Entity e) {
		return bestStrength(e) >= UNLOCK_STRENGTH;
	}

	public static int slotNeedingPick(Entity e) {
		if (any(e)) return 0;
		if (!talentAt(e, 1).isEmpty() && strengthAt(e, 1) >= UNLOCK_STRENGTH) return 1;
		if (!talentAt(e, 2).isEmpty() && strengthAt(e, 2) >= UNLOCK_STRENGTH) return 2;
		return 0;
	}

	public static boolean valid(String id) {
		if (id == null) return false;
		String want = id.toLowerCase(Locale.ROOT).trim();
		for (String s : ALL) if (s.equals(want)) return true;
		return false;
	}

	public static int indexOf(String id) {
		if (id == null) return -1;
		String want = id.toLowerCase(Locale.ROOT).trim();
		for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(want)) return i;
		return -1;
	}

	public static boolean pick(Entity e, String enhancementId) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return false;
		if (!valid(enhancementId)) return false;

		if (any(p)) {
			StatusLine.hush(p);
			p.displayClientMessage(Component.literal("§4§lYour enhancement is already "
					+ label(picked(p)) + "."), true);
			return false;
		}
		if (!eligible(p)) {
			StatusLine.hush(p);
			p.displayClientMessage(Component.literal("§4§lTalent strength "
					+ (int) UNLOCK_STRENGTH + " needed."), true);
			return false;
		}

		String want = enhancementId.toLowerCase(Locale.ROOT).trim();
		p.getPersistentData().putString(KEY, want);
		p.sendSystemMessage(Component.literal("§6§lENHANCEMENT: §r§e" + label(want)
				+ " §7· this choice is permanent"));
		EnhancementPayload.sync(p);
		return true;
	}

	public static boolean pick(Entity e, String talentId, String enhancementId) {
		return pick(e, enhancementId);
	}

	public static void clear(Entity e) {
		if (!(e instanceof Player p)) return;
		p.getPersistentData().putString(KEY, "");
		if (!p.level().isClientSide()) EnhancementPayload.sync(p);
	}

	public static String label(String id) {
		int i = indexOf(id);
		return i < 0 ? "" : NAMES[i];
	}

	public static void applyClient(String value) {
		CLIENT = value == null ? "" : value;
	}

	public static String encode(Player p) {
		String s = p.getPersistentData().getString(KEY);
		return s == null ? "" : s;
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		EnhancementPayload.sync(event.getEntity());
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		EnhancementPayload.sync(event.getEntity());
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		String s = event.getOriginal().getPersistentData().getString(KEY);
		if (s != null && !s.isEmpty()) event.getEntity().getPersistentData().putString(KEY, s);
	}
}