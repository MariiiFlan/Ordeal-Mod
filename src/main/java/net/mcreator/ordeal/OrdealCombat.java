package net.mcreator.ordeal.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.mcreator.ordeal.OrdealCombo;
import net.mcreator.ordeal.OrdealMobStats;
import net.mcreator.ordeal.OrdealVfxPayload;
import net.mcreator.ordeal.network.OrdealModVariables;

/**
 * Guard sits in front of health. A hit that cannot threaten the guard bounces off it;
 * a hit that can eats into it and only reaches health once the guard is gone.
 */
@EventBusSubscriber(modid = "ordeal")
public class OrdealCombat {

	public static double GUARD_BASE       = net.mcreator.ordeal.OrdealTuning.d("combat.guard_base", 25.0);
	public static double GUARD_PER_DUR    = net.mcreator.ordeal.OrdealTuning.d("combat.guard_per_dur", 4.0);
	public static double REDUCTION_PER_DUR = net.mcreator.ordeal.OrdealTuning.d("combat.reduction_per_dur", 0.0025);
	public static double REDUCTION_CAP    = net.mcreator.ordeal.OrdealTuning.d("combat.reduction_cap", 0.30);
	public static double AP_PER_STR       = net.mcreator.ordeal.OrdealTuning.d("combat.ap_per_str", 0.25);

	/** A hit under the target's gate cannot threaten their guard and glances off. */
	public static double GATE_BASE   = net.mcreator.ordeal.OrdealTuning.d("combat.gate_base", 2.0);
	public static double GATE_PER_DUR = net.mcreator.ordeal.OrdealTuning.d("combat.gate_per_dur", 0.18);
	public static double SOFT_FLOOR  = net.mcreator.ordeal.OrdealTuning.d("combat.bounce_floor", 0.4);
	public static double CHIP        = net.mcreator.ordeal.OrdealTuning.d("combat.chip_rate", 0.05);

	public static double gate(double durability) { return GATE_BASE + durability * GATE_PER_DUR; }

	/** Sound played when a Guard reaches zero. This is the registered element name. */
	public static final String BREAK_SOUND = "gaurd_break";

	public static int    LOCKOUT     = net.mcreator.ordeal.OrdealTuning.i("combat.regen_lockout_ticks", 100);
	public static double REGEN_RATE  = net.mcreator.ordeal.OrdealTuning.d("combat.guard_regen_rate", 0.05);
	public static double CHARGE_MULT = net.mcreator.ordeal.OrdealTuning.d("combat.charge_regen_mult", 3.0);

	public static void recalc(Player p) {
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		double gm = GUARD_BASE + v.statDurability * GUARD_PER_DUR;
		double dr = Math.min(REDUCTION_CAP, v.statDurability * REDUCTION_PER_DUR);
		double ap = v.statStrength * AP_PER_STR;
		if (v.guardMax == gm && v.damageReduction == dr && v.attackPower == ap) return;

		boolean first = v.guardMax <= 0;
		v.guardMax = gm;
		v.damageReduction = dr;
		v.attackPower = ap;
		v.guard = first ? gm : Math.min(v.guard, gm);
		v.markSyncDirty();
	}

	@SubscribeEvent
	public static void onIncoming(LivingIncomingDamageEvent event) {
		DamageSource src = event.getSource();
		if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

		if (!(event.getEntity() instanceof Player target)) {
			if (event.getEntity() instanceof LivingEntity mob && src.getEntity() instanceof Player pa)
				mobHit(event, mob, pa, src);
			return;
		}

		OrdealModVariables.PlayerVariables tv = target.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (tv.guardMax <= 0) return;

		double ap = event.getAmount();
		Player attackerP = src.getEntity() instanceof Player pp ? pp : null;
		double charge = 1;
		if (attackerP != null) {
			OrdealModVariables.PlayerVariables av = attackerP.getData(OrdealModVariables.PLAYER_VARIABLES);
			if (!isTalent(src)) {
				charge = swingCharge(attackerP, event.getAmount());
				ap += av.attackPower * charge * charge;
			}
			ap *= (1.0 - av.ChiConcealed);
			ap *= OrdealCombo.damageMult(attackerP);
		}

		double ratio = ap / gate(tv.statDurability);
		boolean bounced = ratio < SOFT_FLOOR;
		double dealt;
		if (ratio >= 1.0)            dealt = ap;
		else if (ratio >= SOFT_FLOOR) dealt = ap * ratio;
		else                          dealt = ap * CHIP;

		dealt *= (1.0 - tv.damageReduction);

		boolean hadGuard = tv.guard > 0;
		double absorbed = 0;
		if (tv.guard > 0) {
			absorbed = Math.min(tv.guard, dealt);
			tv.guard -= absorbed;
			dealt -= absorbed;
		}
		boolean broke = hadGuard && tv.guard <= 0;

		if (src.getEntity() instanceof LivingEntity attacker)
			OrdealXp.onHit(attacker, target, dealt, absorbed);
		if (attackerP != null) comboBeat(attackerP, target, charge);
		if (broke) OrdealCombo.drop(target);

		tv.guardRegenTick = LOCKOUT;
		tv.markSyncDirty();
		// a landed blow puts BOTH sides in combat with each other for ten
		// seconds, refreshed by every further hit - not a stance you switch on
		OrdealCombatState.engage(target, src.getEntity());

		report(target, dealt, absorbed, bounced, broke);
		event.setAmount((float) Math.max(0, dealt));
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (p.level().isClientSide()) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		// the lock lapses with the effect, not with the guard timer - the two
		// run on different clocks and the opponent name has to follow the effect
		if (p.tickCount % 10 == 0 && !"none".equals(v.inCombatWith)
				&& !OrdealCombatState.inCombat(p)) {
			v.inCombatWith = "none";
			v.markSyncDirty();
		}

		if (v.guardMax <= 0) return;

		if (v.guardRegenTick > 0) {
			v.guardRegenTick--;
			return;
		}
		if (v.guard >= v.guardMax) return;

		double rate = v.guardMax * REGEN_RATE / 20.0;
		if (v.chiCharging > 0) rate *= CHARGE_MULT;
		v.guard = Math.min(v.guardMax, v.guard + rate);
		if (p.tickCount % 5 == 0) v.markSyncDirty();
	}

	// Mobs carry their guard in persistentData; regen is computed lazily on the
	// next hit instead of ticking every mob every tick.
	private static final String MOB_GUARD      = "ordeal_guard";
	private static final String MOB_GUARD_TICK = "ordeal_guard_tick";
	private static final String MOB_GUARD_LOCK = "ordeal_guard_lock";

	public static double mobGuardMax(double dur) { return GUARD_BASE + dur * GUARD_PER_DUR; }

	/** Client mirror: the GUARD payload writes the server value onto the client entity. */
	public static void clientMobGuard(LivingEntity mob, double guard, int lock) {
		var tag = mob.getPersistentData();
		tag.putDouble(MOB_GUARD, guard);
		tag.putInt(MOB_GUARD_TICK, mob.tickCount);
		tag.putInt(MOB_GUARD_LOCK, lock);
	}

	/** Current guard of a mob with rolled durability, regen applied since last hit. */
	public static double mobGuard(LivingEntity mob, double dur) {
		var tag = mob.getPersistentData();
		double max = mobGuardMax(dur);
		if (!tag.contains(MOB_GUARD)) return max;
		double g = tag.getDouble(MOB_GUARD);
		int idle = Math.max(0, mob.tickCount - tag.getInt(MOB_GUARD_TICK)) - tag.getInt(MOB_GUARD_LOCK);
		if (idle > 0) g = Math.min(max, g + max * REGEN_RATE / 20.0 * idle);
		return g;
	}

	/** Mobs run the same gate, guard and reduction rules from their rolled stats. */
	private static void mobHit(LivingIncomingDamageEvent event, LivingEntity mob, Player attacker, DamageSource src) {
		OrdealModVariables.PlayerVariables av = attacker.getData(OrdealModVariables.PLAYER_VARIABLES);
		double ap = event.getAmount();
		double charge = 1;
		if (!isTalent(src)) {
			charge = swingCharge(attacker, event.getAmount());
			ap += av.attackPower * charge * charge;
		}
		ap *= (1.0 - av.ChiConcealed);
		ap *= OrdealCombo.damageMult(attacker);

		double dur = mob.getPersistentData().getDouble(OrdealMobStats.DUR);
		boolean bounced = false, broke = false;
		double absorbed = 0;
		if (dur > 0) {
			double ratio = ap / gate(dur);
			bounced = ratio < SOFT_FLOOR;
			if (ratio < 1.0) ap = ratio >= SOFT_FLOOR ? ap * ratio : ap * CHIP;
			ap *= (1.0 - Math.min(REDUCTION_CAP, dur * REDUCTION_PER_DUR));

			double guard = mobGuard(mob, dur);
			boolean hadGuard = guard > 0;
			if (hadGuard) {
				absorbed = Math.min(guard, ap);
				guard -= absorbed;
				ap -= absorbed;
			}
			broke = hadGuard && guard <= 0;
			var tag = mob.getPersistentData();
			int lock = broke ? LOCKOUT * 2 : LOCKOUT;
			tag.putDouble(MOB_GUARD, guard);
			tag.putInt(MOB_GUARD_TICK, mob.tickCount);
			tag.putInt(MOB_GUARD_LOCK, lock);
			// mirror to viewers so the over-mob guard bar shows the real value
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(mob,
					new OrdealVfxPayload(OrdealVfxPayload.GUARD, mob.getId(), lock, 0, (float) guard));
		}

		comboBeat(attacker, mob, charge);
		OrdealCombatState.engage(mob, attacker);
		report(mob, ap, absorbed, bounced, broke);
		event.setAmount((float) Math.max(0, ap));
	}

	/**
	 * How recovered the swing was, inferred from the damage vanilla let
	 * through against the attacker's full attack value. Spam clicks land
	 * around 0.2; a fully charged swing lands at 1.0 (crits clamp to 1).
	 */
	private static double swingCharge(Player attacker, double rawAmount) {
		double full = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
		return full <= 0 ? 1 : Math.min(1.0, rawAmount / full);
	}

	/**
	 * Combos are rhythm, not clicks. A recovered swing adds a link, a rushed
	 * one merely keeps the chain alive, and flailing drops it on the spot.
	 */
	private static void comboBeat(Player attacker, LivingEntity target, double charge) {
		if (charge >= 0.85) OrdealCombo.land(attacker, target);
		else if (charge < 0.55) OrdealCombo.drop(attacker);
	}

	/** Send the numbers, the bounce and the break to everyone who can see it. */
	private static void report(LivingEntity victim, double through, double absorbed,
			boolean bounced, boolean broke) {
		if (!(victim.level() instanceof ServerLevel level)) return;
		double x = victim.getX(), y = victim.getY() + victim.getBbHeight() * 0.7, z = victim.getZ();

		if (absorbed > 0.05)
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim,
					new OrdealVfxPayload(OrdealVfxPayload.ABSORBED, x, y, z, (float) absorbed));
		if (through > 0.05)
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim,
					new OrdealVfxPayload(bounced ? OrdealVfxPayload.CHIP : OrdealVfxPayload.THROUGH,
							x, y, z, (float) through));

		if (bounced) {
			level.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
					SoundSource.PLAYERS, 0.7f, 1.7f);
		}
		if (broke) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim,
					new OrdealVfxPayload(OrdealVfxPayload.BREAK, x, y, z, (float) through));
			SoundEvent snd = breakSound();
			if (snd != null) level.playSound(null, x, y, z, snd, SoundSource.PLAYERS, 1.0f, 1.0f);
			if (victim instanceof ServerPlayer sp) {
				PacketDistributor.sendToPlayer(sp, new OrdealVfxPayload(OrdealVfxPayload.FLASH, x, y, z, 0));
				shake(sp, 3, 14);
			}
		}
	}

	/** Drives the existing CameraShake, which reads the "screen_shake" effect. */
	private static void shake(LivingEntity e, int amplifier, int ticks) {
		var key = ResourceKey.create(Registries.MOB_EFFECT,
				ResourceLocation.fromNamespaceAndPath("ordeal", "screen_shake"));
		net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(key)
				.ifPresent(h -> e.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						h, ticks, amplifier, false, false)));
	}

	// ---- combat mode locks the hands ---------------------------------------
	// In combat mode you fight with what you're holding: no dropping items,
	// no placing or breaking blocks, no picking things up.

	private static boolean inCombat(Player p) {
		return p != null && p.getData(OrdealModVariables.PLAYER_VARIABLES).combatMode;
	}

	@SubscribeEvent
	public static void onToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
		if (!inCombat(event.getPlayer())) return;
		event.setCanceled(true);
		event.getPlayer().getInventory().add(event.getEntity().getItem());
	}

	@SubscribeEvent
	public static void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
		if (event.getPlayer() != null && inCombat(event.getPlayer())) event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
		if (event.getEntity() instanceof Player p && inCombat(p)) event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre event) {
		if (inCombat(event.getPlayer()))
			event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
	}


	private static SoundEvent breakSoundCache;
	private static boolean breakSoundLooked;

	private static SoundEvent breakSound() {
		if (breakSoundLooked) return breakSoundCache;
		breakSoundLooked = true;

		var key = ResourceKey.create(Registries.SOUND_EVENT,
				ResourceLocation.fromNamespaceAndPath("ordeal", BREAK_SOUND));
		breakSoundCache = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
				.getOptional(key).orElse(null);
		if (breakSoundCache != null) return breakSoundCache;

		for (var e : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.entrySet()) {
			if (!e.getKey().location().getNamespace().equals("ordeal")) continue;
			String path = e.getKey().location().getPath().toLowerCase(java.util.Locale.ROOT);
			String flat = path.replace("_", "").replace("-", "");
			boolean guardish = flat.contains("guard") || flat.contains("gaurd");
			if (guardish && flat.contains("break")) {
				breakSoundCache = e.getValue();
				org.slf4j.LoggerFactory.getLogger("ordeal").info(
						"[ordeal] guard break sound resolved to {}", e.getKey().location());
				return breakSoundCache;
			}
		}

		StringBuilder found = new StringBuilder();
		for (var e : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.entrySet())
			if (e.getKey().location().getNamespace().equals("ordeal"))
				found.append(found.length() == 0 ? "" : ", ").append(e.getKey().location().getPath());
		org.slf4j.LoggerFactory.getLogger("ordeal").warn(
				"[ordeal] no guard break sound registered. ordeal sounds present: {}",
				found.length() == 0 ? "(none)" : found);
		return null;
	}

	private static boolean isTalent(DamageSource src) {
		var key = src.typeHolder().unwrapKey().orElse(null);
		return key != null && key.location().getNamespace().equals("ordeal")
				&& key.location().getPath().equals("talent");
	}
}