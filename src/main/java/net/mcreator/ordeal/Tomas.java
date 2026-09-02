package net.mcreator.ordeal;

import net.mcreator.ordeal.core.client.OrdealTalents;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.mcreator.ordeal.network.PlayPlayerAnimationMessage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "ordeal")
public final class Tomas {

	/** Prints in the game log at startup so a stale file is instantly visible. */
	public static final int VERSION = 3;
	static { System.out.println("[ordeal] Tomas v" + VERSION + " loaded (projectile + staged anims)"); }

	public static String ABILITY_ID = "tomas";
	public static String TALENT_ID  = "ilios";

	// ---- the two you'll tune most, kept at the top ----
	/** Blocks per tick the sun travels once shot (4 = 80 blocks a second). */
	public static double PROJECTILE_SPEED = OrdealTuning.d("tomas.projectile_speed", 4);
	/** Ticks the sun flies before it detonates on its own (100 = 5 seconds). */
	public static int    PROJECTILE_LIFE  = OrdealTuning.i("tomas.projectile_life_ticks", 100);

	/** Explosions only break blocks when this gamerule is on: /gamerule OrdealBlockDestruction true */
	public static final GameRules.Key<GameRules.BooleanValue> BLOCK_DESTRUCTION =
			GameRules.register("OrdealBlockDestruction", GameRules.Category.MISC,
					GameRules.BooleanValue.create(false));

	public static int[] COOLDOWN_BY_STAGE = {
			OrdealTuning.i("tomas.cooldown_stage_0", 60),
			OrdealTuning.i("tomas.cooldown_stage_1", 120),
			OrdealTuning.i("tomas.cooldown_stage_2", 220),
			OrdealTuning.i("tomas.cooldown_stage_3", 400),
			OrdealTuning.i("tomas.cooldown_stage_4", 700),
			OrdealTuning.i("tomas.cooldown_stage_5", 1200) };

	public static double[] RADIUS_BY_STAGE = {
			OrdealTuning.d("tomas.radius_0", 4),
			OrdealTuning.d("tomas.radius_1", 5),
			OrdealTuning.d("tomas.radius_2", 6.5),
			OrdealTuning.d("tomas.radius_3", 9),
			OrdealTuning.d("tomas.radius_4", 12),
			OrdealTuning.d("tomas.radius_5", 18) };

	/** Visual size of the orb itself at each stage, in blocks. Grows smoothly between stages. */
	public static double[] ORB_RADIUS = {
			OrdealTuning.d("tomas.orb_radius_0", 0.35),
			OrdealTuning.d("tomas.orb_radius_1", 0.5),
			OrdealTuning.d("tomas.orb_radius_2", 0.7),
			OrdealTuning.d("tomas.orb_radius_3", 1.4),
			OrdealTuning.d("tomas.orb_radius_4", 2.2),
			OrdealTuning.d("tomas.orb_radius_5", 3.2) };

	public static double BASE_DMG            = OrdealTuning.d("tomas.base_dmg", 15);
	public static double EXTRA_DMG_PER_STR   = OrdealTuning.d("tomas.extra_dmg_per_str", 0.4);
	public static double EDGE_DAMAGE_FRACTION = OrdealTuning.d("tomas.edge_damage_fraction", 0.4);
	public static int    IGNITE_SECONDS      = OrdealTuning.i("tomas.ignite_seconds", 5);

	public static int    GRAVITY_FROM_STAGE  = OrdealTuning.i("tomas.gravity_from_stage", 3);
	public static int    GRAVITY_TICKS       = OrdealTuning.i("tomas.gravity_ticks", 15);
	public static int    GRAVITY_TICKS_FULL  = OrdealTuning.i("tomas.gravity_ticks_full_charge", 30);
	public static double GRAVITY_STRENGTH    = OrdealTuning.d("tomas.gravity_strength", 0.09);
	public static double GRAVITY_RADIUS_MULT = OrdealTuning.d("tomas.gravity_radius_mult", 1.0);
	public static double GRAVITY_LIFT        = OrdealTuning.d("tomas.gravity_lift", 0.04);

	public static double CHARGE_SECONDS      = OrdealTuning.d("tomas.charge_seconds", 30);
	public static double CHI_CONTROL_MAX     = OrdealTuning.d("tomas.chi_control_max", 0.5);

	public static int    BREAK_MAX_BLOCKS    = OrdealTuning.i("tomas.break_max_blocks", 900);
	public static int    BREAK_MAX_FALLING   = OrdealTuning.i("tomas.break_max_falling", 40);
	public static double BREAK_RADIUS_CAP    = OrdealTuning.d("tomas.break_radius_cap", 10);

	public static int    BROADCAST_EVERY     = OrdealTuning.i("tomas.charge_broadcast_ticks", 3);
	public static String FX_BURST            = "photon:ilios_explosion";

	public static String ANIM_START  = "tomas_start";
	public static String ANIM_START2 = "tomas_start_2";
	public static String ANIM_END    = "tomas_end";
	public static String ANIM_END2   = "tomas_end2";

	private static final List<Pending> PENDING = new ArrayList<>();
	private static final List<Sun> SUNS = new ArrayList<>();
	private static final Map<UUID, Long> CHARGING = new HashMap<>();
	private static final Map<UUID, Integer> STAGE = new HashMap<>();
	private static final Map<UUID, Long> JUST_FIRED = new HashMap<>();
	private static int NEXT_SUN_ID = 1;

	private Tomas() {}

	/** The sun in flight. Straight line, no gravity, explodes on whatever it touches first. */
	private static final class Sun {
		ServerLevel level;
		UUID caster;
		int id;
		double x, y, z;
		double vx, vy, vz;
		double radius;
		float dispRadius;
		double damage;
		int stage;
		int life;
	}

	private static final class Pending {
		ServerLevel level;
		UUID caster;
		double x, y, z;
		double radius;
		double damage;
		int stage;
		int ticksLeft;
	}

	public static String abilityName() {
		OrdealTalents.Ability ab = OrdealTalents.ability(ABILITY_ID);
		return ab == null || ab.name == null || ab.name.isEmpty() ? "Tomas" : ab.name;
	}

	/** The name this ability sits under in the loadout, for the cooldown effect lookup. */
	private static String holdName(Player p) {
		String held = AbilityHold.heldAbility(p);
		return held == null || held.isEmpty() ? abilityName() : held;
	}

	/** True whatever string the loadout carries - the id or the display name. */
	private static boolean isTomas(String name) {
		if (name == null || name.isEmpty()) return false;
		if (name.equalsIgnoreCase(ABILITY_ID) || name.equalsIgnoreCase(abilityName())) return true;
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(name);
		return ab != null && ABILITY_ID.equalsIgnoreCase(ab.id);
	}

	public static int cooldownFor(int stage) {
		int lv = Math.max(0, Math.min(COOLDOWN_BY_STAGE.length - 1, stage));
		return COOLDOWN_BY_STAGE[lv];
	}

	public static double chargeSeconds(Entity e) {
		if (!(e instanceof Player p)) return CHARGE_SECONDS;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		return CHARGE_SECONDS * (1.0 - Math.min(CHI_CONTROL_MAX, v.statChiControl * 0.004));
	}

	public static double secondsPerStage(Entity e) {
		return chargeSeconds(e) / 5.0;
	}

	/** Orb size for a 0..1 charge, sliding smoothly through the per-stage tunables. */
	public static float displayRadius(double fraction) {
		double f = Math.max(0, Math.min(1, fraction)) * 5.0;
		int i = (int) Math.min(4, Math.floor(f));
		double t = f - i;
		return (float) (ORB_RADIUS[i] + (ORB_RADIUS[i + 1] - ORB_RADIUS[i]) * t);
	}

	public static void fire(Entity e) {
		fire(e, AbilityHold.level(e));
	}

	public static void fire(Entity e, double chargeStage) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return;
		if (!(p.level() instanceof ServerLevel sl)) return;

		// AbilityHold dispatches the procedure even on a press while on
		// cooldown - without this guard that press would detonate for free.
		String cdName = holdName(p);
		if (AbilityHold.onCooldown(p, cdName)) {
			p.displayClientMessage(Component.literal("§7ON COOLDOWN"), true);
			return;
		}

		int stage = (int) Math.max(0, Math.min(5, Math.round(chargeStage)));
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		double strength = TALENT_ID.equals(v.talent1_id) ? v.talent1_strength
				: TALENT_ID.equals(v.talent2_id) ? v.talent2_strength : 0;

		double state = v.talentState <= 0 ? 1 : v.talentState;
		double power = v.chargePower <= 0 ? 1 : v.chargePower;
		double damage = (BASE_DMG + strength * EXTRA_DMG_PER_STR) * power * state;
		double radius = RADIUS_BY_STAGE[stage];

		AbilityHold.applyCooldown(p, cdName, cooldownFor(stage));
		CHARGING.remove(p.getUUID());
		STAGE.remove(p.getUUID());
		JUST_FIRED.put(p.getUUID(), p.level().getGameTime());
		SolarCorePayload.held(p, -1f);
		anim(p, stage >= GRAVITY_FROM_STAGE ? ANIM_END2 : ANIM_END);

		// the exact held fraction is still readable during dispatch, so the sun
		// leaves at precisely the size it was charged to
		double fraction = AbilityHold.chargeFraction(p);
		if (fraction <= 0) fraction = chargeStage / 5.0;
		float disp = displayRadius(Math.max(0, Math.min(1, fraction)));
		Vec3 look = p.getLookAngle();

		// Small suns leave the hand; the big ones launch from above the head,
		// where they were hanging while charging.
		Vec3 from = stage >= GRAVITY_FROM_STAGE
				? p.position().add(0, p.getBbHeight() + disp * 0.6 + 0.4, 0)
				: p.getEyePosition(1f).add(look.scale(0.8)).add(0, -0.2, 0);

		Sun s = new Sun();
		s.level = sl;
		s.caster = p.getUUID();
		s.id = NEXT_SUN_ID++;
		s.x = from.x; s.y = from.y; s.z = from.z;
		s.vx = look.x * PROJECTILE_SPEED;
		s.vy = look.y * PROJECTILE_SPEED;
		s.vz = look.z * PROJECTILE_SPEED;
		s.radius = radius;
		s.dispRadius = disp;
		s.damage = damage;
		s.stage = stage;
		s.life = Math.max(1, PROJECTILE_LIFE);
		SUNS.add(s);
		SolarCorePayload.fly(sl, s.id, from, flyGlow(stage), disp);
	}

	private static float flyGlow(int stage) {
		return Math.min(1f, 0.4f + 0.12f * stage);
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (!SUNS.isEmpty()) {
			for (Sun s : new ArrayList<>(SUNS)) {
				Vec3 from = new Vec3(s.x, s.y, s.z);
				Vec3 to = from.add(s.vx, s.vy, s.vz);
				Player caster = s.level.getPlayerByUUID(s.caster);

				Vec3 stop = null;

				// the world first...
				if (caster != null) {
					var hit = s.level.clip(new ClipContext(from, to,
							ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
					if (hit.getType() != HitResult.Type.MISS) stop = hit.getLocation();
				}

				// ...then anything alive along the path, nearest first
				double pad = s.dispRadius * 0.6 + 0.3;
				AABB seg = new AABB(from, stop != null ? stop : to).inflate(pad);
				double best = Double.MAX_VALUE;
				for (LivingEntity le : s.level.getEntitiesOfClass(LivingEntity.class, seg)) {
					if (caster != null && le == caster) continue;
					var clip = le.getBoundingBox().inflate(pad).clip(from, stop != null ? stop : to);
					if (clip.isEmpty()) continue;
					double d = clip.get().distanceToSqr(from);
					if (d < best) { best = d; stop = clip.get(); }
				}

				if (stop != null) {
					SUNS.remove(s);
					explode(s, stop);
					continue;
				}

				s.x = to.x; s.y = to.y; s.z = to.z;
				if (--s.life <= 0) {
					SUNS.remove(s);
					explode(s, to);
					continue;
				}
				SolarCorePayload.fly(s.level, s.id, to, flyGlow(s.stage), s.dispRadius);
			}
		}

		if (PENDING.isEmpty()) return;
		for (Pending pd : new ArrayList<>(PENDING)) {
			pd.ticksLeft--;
			if (pd.ticksLeft > 0) continue;
			PENDING.remove(pd);
			detonate(pd.level, pd.caster, new Vec3(pd.x, pd.y, pd.z), pd.radius, pd.damage, pd.stage);
		}
	}

	private static void explode(Sun s, Vec3 at) {
		SolarCorePayload.fly(s.level, s.id, at, -1f, 0);

		if (s.stage >= GRAVITY_FROM_STAGE) {
			Player caster = s.level.getPlayerByUUID(s.caster);
			int ticks = s.stage >= 5 ? GRAVITY_TICKS_FULL : GRAVITY_TICKS;
			if (caster != null) {
				GravityPull.Zone well = GravityPull.open(caster, at,
						s.radius * GRAVITY_RADIUS_MULT, GRAVITY_STRENGTH, ticks);
				if (well != null) well.lift(GRAVITY_LIFT);
			}
			Pending pd = new Pending();
			pd.level = s.level;
			pd.caster = s.caster;
			pd.x = at.x; pd.y = at.y; pd.z = at.z;
			pd.radius = s.radius;
			pd.damage = s.damage;
			pd.stage = s.stage;
			pd.ticksLeft = ticks;
			PENDING.add(pd);
			SolarCorePayload.hang(s.level, at, s.stage, ticks, s.dispRadius);
			return;
		}

		detonate(s.level, s.caster, at, s.radius, s.damage, s.stage);
	}

	private static void detonate(ServerLevel sl, UUID caster, Vec3 at,
			double radius, double damage, int stage) {
		Player p = sl.getPlayerByUUID(caster);
		var holder = sl.holderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,
				ResourceLocation.parse("ordeal:talent")));
		DamageSource src = p != null ? new DamageSource(holder, p) : new DamageSource(holder);

		AABB box = new AABB(at.x - radius, at.y - radius, at.z - radius,
				at.x + radius, at.y + radius, at.z + radius);
		for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, box)) {
			if (p != null && le == p) continue;
			double d = le.position().distanceTo(at);
			if (d > radius) continue;
			double near = 1.0 - (d / radius) * (1.0 - EDGE_DAMAGE_FRACTION);
			le.hurt(src, (float) (damage * near));
			if (!le.fireImmune() && IGNITE_SECONDS > 0)
				le.setRemainingFireTicks(Math.max(le.getRemainingFireTicks(), IGNITE_SECONDS * 20));
		}

		breakBlocks(sl, at, radius);

		SolarCorePayload.burst(sl, at, stage, radius);
		if (!FX_BURST.isEmpty()) Fx.world(sl, at, FX_BURST);
	}

	/**
	 * Blocks only break when the OrdealBlockDestruction gamerule is on. A hard
	 * budget keeps the big stages from stalling the tick, and only a handful of
	 * surface blocks become real falling entities - the rest just vanish, which
	 * is what keeps this lag-free.
	 */
	private static void breakBlocks(ServerLevel sl, Vec3 at, double radius) {
		if (!sl.getGameRules().getBoolean(BLOCK_DESTRUCTION)) return;

		double r = Math.min(radius, BREAK_RADIUS_CAP);
		int span = (int) Math.ceil(r);
		int budget = Math.max(0, BREAK_MAX_BLOCKS);
		int flying = 0;
		BlockPos centre = BlockPos.containing(at.x, at.y, at.z);

		for (int dy = span; dy >= -span && budget > 0; dy--) {
			for (int dx = -span; dx <= span && budget > 0; dx++) {
				for (int dz = -span; dz <= span && budget > 0; dz++) {
					if (dx * dx + dy * dy + dz * dz > r * r) continue;
					BlockPos bp = centre.offset(dx, dy, dz);
					BlockState st = sl.getBlockState(bp);
					if (st.isAir()) continue;
					if (st.getDestroySpeed(sl, bp) < 0) continue;      // bedrock and friends
					if (!st.getFluidState().isEmpty()) continue;        // never dig out fluids
					budget--;

					if (flying < BREAK_MAX_FALLING && sl.isEmptyBlock(bp.above())
							&& sl.getRandom().nextFloat() < 0.3f) {
						FallingBlockEntity fb = FallingBlockEntity.fall(sl, bp, st);
						fb.dropItem = false;
						fb.time = 1;
						fb.setDeltaMovement(
								(sl.getRandom().nextDouble() - 0.5) * 0.5,
								0.4 + sl.getRandom().nextDouble() * 0.5,
								(sl.getRandom().nextDouble() - 0.5) * 0.5);
						flying++;
					} else {
						sl.setBlock(bp, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (!(p instanceof ServerPlayer sp)) return;

		// spent = pressed while on cooldown (or already fired) - the server is
		// not charging, so no orb, no animation, nothing
		boolean holding = isTomas(AbilityHold.heldAbility(p)) && !AbilityHold.spent(p);
		Long last = CHARGING.get(p.getUUID());

		if (!holding) {
			if (last != null) {
				CHARGING.remove(p.getUUID());
				STAGE.remove(p.getUUID());
				SolarCorePayload.held(sp, -1f);
				// a cancelled charge drops the hold pose; a real shot has an
				// end animation playing that must not be stomped
				Long fired = JUST_FIRED.remove(p.getUUID());
				if (fired == null || p.level().getGameTime() - fired > 5) anim(p, "");
			}
			return;
		}

		double fraction = AbilityHold.chargeFraction(p);
		int stage = (int) Math.min(5, Math.floor(fraction * 5.0));

		Integer prev = STAGE.get(p.getUUID());
		if (prev == null) anim(p, ANIM_START);
		if ((prev == null || prev < GRAVITY_FROM_STAGE) && stage >= GRAVITY_FROM_STAGE)
			anim(p, ANIM_START2);
		STAGE.put(p.getUUID(), stage);

		// while tomas_start_2 holds the sun overhead you are rooted in place
		if (stage >= GRAVITY_FROM_STAGE) stun(p, 5);

		long now = p.level().getGameTime();
		if (last != null && now - last < Math.max(1, BROADCAST_EVERY)) return;
		CHARGING.put(p.getUUID(), now);
		SolarCorePayload.held(sp, (float) (0.35 + 0.65 * fraction), displayRadius(fraction), stage);
	}

	private static void anim(Player p, String name) {
		if (!(p instanceof ServerPlayer sp) || !(p.level() instanceof ServerLevel sl)) return;
		String id = name.isEmpty() || name.indexOf(':') >= 0 ? name : "ordeal:" + name;
		PacketDistributor.sendToPlayersInDimension(sl,
				new PlayPlayerAnimationMessage(sp.getId(), id, true, false));
	}

	private static net.minecraft.world.effect.MobEffect STUN_FX;
	private static boolean STUN_LOOKED = false;

	private static net.minecraft.world.effect.MobEffect stunEffect() {
		if (STUN_FX != null) return STUN_FX;
		if (STUN_LOOKED) return null;
		STUN_LOOKED = true;
		for (var en : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.entrySet()) {
			if (!en.getKey().location().getNamespace().equals("ordeal")) continue;
			String path = en.getKey().location().getPath().replace("_", "").toLowerCase(java.util.Locale.ROOT);
			if (path.startsWith("movementstun")) {
				STUN_FX = en.getValue();
				break;
			}
		}
		return STUN_FX;
	}

	private static void stun(Player p, int ticks) {
		if (ticks <= 0 || p.level().isClientSide()) return;
		var fx = stunEffect();
		if (fx == null) return;
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(fx),
				ticks, 0, false, false));
	}

	@SubscribeEvent
	public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
		UUID id = event.getEntity().getUUID();
		CHARGING.remove(id);
		STAGE.remove(id);
		JUST_FIRED.remove(id);
	}

	@SubscribeEvent
	public static void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
		PENDING.clear();
		SUNS.clear();
		CHARGING.clear();
		STAGE.clear();
		JUST_FIRED.clear();
	}
}