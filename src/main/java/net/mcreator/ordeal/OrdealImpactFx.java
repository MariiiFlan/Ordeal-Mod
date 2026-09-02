package net.mcreator.ordeal.core;

import net.mcreator.ordeal.OrdealImpactPayload;
import net.mcreator.ordeal.OrdealMobStats;
import net.mcreator.ordeal.OrdealTuning;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Photon effects on hits that are actually worth an effect.
 *
 * Deliberately NOT every hit: a jab that ticks somebody for 2 gets nothing, and
 * an effect that fires on every swing stops reading as impact within a minute.
 * The gate is the same weight scale the impact words use, so a big word and a
 * big effect always turn up together rather than disagreeing.
 *
 * Works for the heavy punch, ability hits, projectiles - anything that credits
 * a player. Nothing has to call it.
 */
@EventBusSubscriber(modid = "ordeal")
public final class OrdealImpactFx {

	private OrdealImpactFx() {}

	// ---- the effects --------------------------------------------------------
	// Blank means "no effect for this tier". Make them in the ordeal fx project
	// and put the names here.

	/** Solid hits. Left blank on purpose - this tier fires constantly. */
	public static String FX_SOLID = "";
	/** A real heavy blow. */
	public static String FX_HEAVY = "photon:misc_heavyattack";
	/** The big ones. Falls back to FX_HEAVY while this is blank. */
	public static String FX_MASSIVE = "";
	/** Taking somebody's guard to zero. Fires on top of the tier effect. */
	public static String FX_BREAK = "";

	/** Ticks a victim is immune to another impact effect, so a flurry cannot stack them. */
	public static int GAP_TICKS = OrdealTuning.i("fx.impact_gap_ticks", 6);

	private static final String GUARD_SNAP = "ordeal_fxGuardSnap";
	private static final String LAST_FX = "ordeal_fxLast";

	/** Before anything touches the damage: remember the guard. */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void snapshot(LivingIncomingDamageEvent event) {
		LivingEntity victim = event.getEntity();
		if (victim.level().isClientSide()) return;
		if (!(event.getSource().getEntity() instanceof Player)) return;
		victim.getPersistentData().putDouble(GUARD_SNAP, guardOf(victim));
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onHit(LivingIncomingDamageEvent event) {
		if (event.isCanceled()) return;
		LivingEntity victim = event.getEntity();
		if (victim.level().isClientSide()) return;
		if (!(event.getSource().getEntity() instanceof Player attacker) || attacker == victim) return;

		long now = victim.level().getGameTime();
		if (now - victim.getPersistentData().getLong(LAST_FX) < GAP_TICKS) return;

		double before = victim.getPersistentData().getDouble(GUARD_SNAP);
		double after = guardOf(victim);
		double absorbed = Math.max(0, before - after);

		// what the blow actually did: what the guard ate plus what got through.
		// A heavy hit swallowed whole by a guard is still a heavy hit and should
		// still look like one.
		double landed = absorbed + Math.max(0, event.getAmount());
		int tier = OrdealImpactPayload.tierFor(victim, (float) landed);

		String fx = switch (tier) {
			case OrdealImpactPayload.MASSIVE -> FX_MASSIVE.isEmpty() ? FX_HEAVY : FX_MASSIVE;
			case OrdealImpactPayload.HEAVY -> FX_HEAVY;
			case OrdealImpactPayload.SOLID -> FX_SOLID;
			default -> "";
		};

		// an ability that authored its own impact effect wins over the tier
		// default, so a Phoenix Flame does not burst like a fist
		String own = abilityFx(attacker);
		if (!own.isEmpty() && tier != OrdealImpactPayload.NONE) fx = own;

		boolean broke = before > 0 && after <= 0;
		if (fx.isEmpty() && !(broke && !FX_BREAK.isEmpty())) return;

		// the thing that actually touched them - a projectile if there was one,
		// otherwise the player - is what the burst should point away from
		Entity source = event.getSource().getDirectEntity();
		if (source == null || source == victim) source = attacker;

		victim.getPersistentData().putLong(LAST_FX, now);
		if (!fx.isEmpty()) play(victim, source, fx);
		if (broke && !FX_BREAK.isEmpty()) play(victim, source, FX_BREAK);
	}

	/** No blow direction known - falls back to the victim's own facing. */
	public static void play(Entity at, String fx) {
		play(at, null, fx);
	}

	/**
	 * Run a photon effect on the victim, pointed along the blow.
	 *
	 * The old version ended the command with "xrot", and photon's xrot means
	 * "yaw-align to the entity this is playing on" - the victim. That is why a
	 * punch burst looked like the person being hit was the one throwing it: the
	 * effect faced wherever they happened to be looking.
	 *
	 * So the rotation is passed explicitly and auto-rotate is switched off.
	 * Photon builds it as rotationXYZ(x, y, z) in degrees, and its xrot mode is
	 * exactly rotateXYZ(0, -90 - entityYaw, 0), so the same yaw taken from the
	 * ATTACKER reduces to -atan2(dz, dx) - that is the whole trick. With
	 * auto-rotate off, photon leaves the rotation alone for the rest of the
	 * effect's life while still tracking the victim's position, so the burst
	 * stays pointed the way the punch travelled even as they stumble.
	 *
	 * The offset also walks back toward the attacker by half the victim's width,
	 * so it sits on the surface that got hit instead of inside their chest.
	 *
	 * @param at   who got hit - the effect rides them
	 * @param from where the blow came from (attacker, or the projectile), may be null
	 */
	public static void play(Entity at, Entity from, String fx) {
		if (at == null || fx == null || fx.isEmpty()) return;
		if (at.level().isClientSide() || at.getServer() == null) return;

		double yaw = -90 - at.getVisualRotationYInDegrees();   // photon's own xrot, as a fallback
		double ox = 0, oz = 0;
		if (from != null) {
			double dx = at.getX() - from.getX();
			double dz = at.getZ() - from.getZ();
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len > 1.0e-4) {
				yaw = -Math.toDegrees(Math.atan2(dz, dx));
				double push = at.getBbWidth() * 0.5;
				ox = -dx / len * push;
				oz = -dz / len * push;
			}
		}

		String cmd = String.format(java.util.Locale.ROOT,
				"photon fx %s entity @s %.3f %.3f %.3f 0 %.3f 0 1 1 1 0 false false none",
				fx, ox, HEIGHT, oz, yaw);

		at.getServer().getCommands().performPrefixedCommand(
				new CommandSourceStack(CommandSource.NULL, at.position(), at.getRotationVector(),
						at.level() instanceof ServerLevel sl ? sl : null, 4,
						at.getName().getString(), at.getDisplayName(), at.level().getServer(), at),
				cmd);
	}

	/**
	 * Vertical offset from the victim's EYES - photon anchors entity effects
	 * there, not at their feet. Negative drops it to chest height.
	 */
	public static double HEIGHT = OrdealTuning.d("fx.impact_height", -0.2);

	/**
	 * The impactFx the ability that just went off declared, if any. Set it as an
	 * ImpactFX local in the procedure and the sync carries it into the json.
	 */
	private static String abilityFx(Player attacker) {
		try {
			String name = attacker.getData(OrdealModVariables.PLAYER_VARIABLES)
					.abilityName.replace("\"", "");
			if (name.isEmpty()) return "";
			var ab = net.mcreator.ordeal.core.client.OrdealTalents.abilityByName(name);
			if (ab == null || ab.projectile == null) return "";
			return ab.projectile.impactFx == null ? "" : ab.projectile.impactFx;
		} catch (Throwable t) {
			return "";
		}
	}

	/** Fire one by hand from a procedure, ignoring the tier gate. */
	public static void burst(Entity at, String fx) { play(at, null, fx); }

	/** Same, but aimed along the line from {@code from} to {@code at}. */
	public static void burst(Entity at, Entity from, String fx) { play(at, from, fx); }

	private static double guardOf(LivingEntity e) {
		if (e instanceof Player p)
			return p.getData(OrdealModVariables.PLAYER_VARIABLES).guard;
		double dur = e.getPersistentData().getDouble(OrdealMobStats.DUR);
		return dur > 0 ? OrdealCombat.mobGuard(e, dur) : 0;
	}
}