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

@EventBusSubscriber(modid = "ordeal")
public class OrdealCombat {

	public static double FIRE_INFUSION_STR     = net.mcreator.ordeal.OrdealTuning.d("ilios.infusion_str", 3);
	public static double FIRE_INFUSION_BASE    = net.mcreator.ordeal.OrdealTuning.d("ilios.infusion_base", 2.0);
	public static double FIRE_INFUSION_PER_STR = net.mcreator.ordeal.OrdealTuning.d("ilios.infusion_per_str", 0.05);
	public static double FIRE_INFUSION_MAX     = net.mcreator.ordeal.OrdealTuning.d("ilios.infusion_max", 12.0);
	public static int    FIRE_INFUSION_BURN    = net.mcreator.ordeal.OrdealTuning.i("ilios.infusion_burn_seconds", 4);
	public static String FIRE_INFUSION_FX      = "photon:ilios_fireinfusion";
	public static String FIRE_INFUSION_ID      = "fire_infusion";

	public static double GUARD_BASE       = net.mcreator.ordeal.OrdealTuning.d("combat.guard_base", 25.0);
	public static double GUARD_PER_DUR    = net.mcreator.ordeal.OrdealTuning.d("combat.guard_per_dur", 4.0);
	public static double REDUCTION_PER_DUR = net.mcreator.ordeal.OrdealTuning.d("combat.reduction_per_dur", 0.0025);
	public static double REDUCTION_CAP    = net.mcreator.ordeal.OrdealTuning.d("combat.reduction_cap", 0.30);
	public static double AP_PER_STR       = net.mcreator.ordeal.OrdealTuning.d("combat.ap_per_str", 0.25);

	public static double GATE_BASE   = net.mcreator.ordeal.OrdealTuning.d("combat.gate_base", 2.0);
	public static double GATE_PER_DUR = net.mcreator.ordeal.OrdealTuning.d("combat.gate_per_dur", 0.18);
	public static double SOFT_FLOOR  = net.mcreator.ordeal.OrdealTuning.d("combat.bounce_floor", 0.4);
	public static double CHIP        = net.mcreator.ordeal.OrdealTuning.d("combat.chip_rate", 0.05);

	public static double gate(double durability) { return GATE_BASE + durability * GATE_PER_DUR; }

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
			ap += fireInfusion(attackerP, target, src);
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

		OrdealCombatState.engage(target, src.getEntity());

		report(target, dealt, absorbed, bounced, broke);
		event.setAmount((float) Math.max(0, dealt));
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (p.level().isClientSide()) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);

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

	private static final String MOB_GUARD      = "ordeal_guard";
	private static final String MOB_GUARD_TICK = "ordeal_guard_tick";
	private static final String MOB_GUARD_LOCK = "ordeal_guard_lock";

	public static double mobGuardMax(double dur) { return GUARD_BASE + dur * GUARD_PER_DUR; }

	public static void clientMobGuard(LivingEntity mob, double guard, int lock) {
		var tag = mob.getPersistentData();
		tag.putDouble(MOB_GUARD, guard);
		tag.putInt(MOB_GUARD_TICK, mob.tickCount);
		tag.putInt(MOB_GUARD_LOCK, lock);
	}

	public static double mobGuard(LivingEntity mob, double dur) {
		var tag = mob.getPersistentData();
		double max = mobGuardMax(dur);
		if (!tag.contains(MOB_GUARD)) return max;
		double g = tag.getDouble(MOB_GUARD);
		int idle = Math.max(0, mob.tickCount - tag.getInt(MOB_GUARD_TICK)) - tag.getInt(MOB_GUARD_LOCK);
		if (idle > 0) g = Math.min(max, g + max * REGEN_RATE / 20.0 * idle);
		return g;
	}

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
		ap += fireInfusion(attacker, mob, src);

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

			PacketDistributor.sendToPlayersTrackingEntityAndSelf(mob,
					new OrdealVfxPayload(OrdealVfxPayload.GUARD, mob.getId(), lock, 0, (float) guard));
		}

		comboBeat(attacker, mob, charge);
		OrdealCombatState.engage(mob, attacker);
		report(mob, ap, absorbed, bounced, broke);
		event.setAmount((float) Math.max(0, ap));
	}

	private static double swingCharge(Player attacker, double rawAmount) {
		double full = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
		return full <= 0 ? 1 : Math.min(1.0, rawAmount / full);
	}

	private static void comboBeat(Player attacker, LivingEntity target, double charge) {
		if (charge >= 0.85) OrdealCombo.land(attacker, target);
		else if (charge < 0.55) OrdealCombo.drop(attacker);
	}

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

	private static void shake(LivingEntity e, int amplifier, int ticks) {
		var key = ResourceKey.create(Registries.MOB_EFFECT,
				ResourceLocation.fromNamespaceAndPath("ordeal", "screen_shake"));
		net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(key)
				.ifPresent(h -> e.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						h, ticks, amplifier, false, false)));
	}

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

	private static double iliosStrength(OrdealModVariables.PlayerVariables v) {
		if ("ilios".equals(v.talent1_id)) return v.talent1_strength;
		if ("ilios".equals(v.talent2_id)) return v.talent2_strength;
		return -1;
	}

	public static double fireInfusion(Player attacker, LivingEntity victim, DamageSource src) {
		if (attacker == null || isTalent(src)) return 0;
		double str = iliosStrength(attacker.getData(OrdealModVariables.PLAYER_VARIABLES));
		var ab = net.mcreator.ordeal.core.client.OrdealTalents.ability(FIRE_INFUSION_ID);
		double gate = ab != null && ab.req > 0 ? ab.req : FIRE_INFUSION_STR;
		if (str < gate) return 0;
		if (ab != null && !net.mcreator.ordeal.Passives.on(attacker, FIRE_INFUSION_ID)) return 0;
		double cost = ab != null ? ab.chi : 0;
		if (cost > 0 && !net.mcreator.ordeal.Passives.pay(attacker, ab.name, cost)) return 0;
		if (victim != null && !victim.fireImmune() && FIRE_INFUSION_BURN > 0)
			victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), FIRE_INFUSION_BURN * 20));
		slashFx(attacker, victim, FIRE_INFUSION_FX);
		double base = ab != null && (ab.base > 0 || ab.per > 0) ? ab.base : FIRE_INFUSION_BASE;
		double per = ab != null && (ab.base > 0 || ab.per > 0) ? ab.per : FIRE_INFUSION_PER_STR;
		return Math.min(FIRE_INFUSION_MAX, base + Math.max(0, str) * per);
	}

	private static void slashFx(Player attacker, LivingEntity victim, String fx) {
		if (fx == null || fx.isEmpty() || victim == null || attacker == null) return;
		if (victim.level().isClientSide() || victim.getServer() == null) return;
		double dx = victim.getX() - attacker.getX();
		double dz = victim.getZ() - attacker.getZ();
		double yaw = (dx * dx + dz * dz) > 1.0e-4 ? -Math.toDegrees(Math.atan2(dz, dx)) : 0;
		victim.getServer().getCommands().performPrefixedCommand(
				new net.minecraft.commands.CommandSourceStack(net.minecraft.commands.CommandSource.NULL,
						victim.position(), victim.getRotationVector(),
						victim.level() instanceof net.minecraft.server.level.ServerLevel sl ? sl : null, 4,
						victim.getName().getString(), victim.getDisplayName(), victim.level().getServer(), victim),
				String.format(java.util.Locale.ROOT,
						"photon fx %s entity @s 0 %.2f 0 0 %.2f 0 1 1 1 0 false false none",
						fx, victim.getBbHeight() * 0.55, yaw));
	}

	private static boolean isTalent(DamageSource src) {
		var key = src.typeHolder().unwrapKey().orElse(null);
		return key != null && key.location().getNamespace().equals("ordeal")
				&& key.location().getPath().equals("talent");
	}
}