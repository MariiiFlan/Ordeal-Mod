package net.mcreator.ordeal.core;

import net.mcreator.ordeal.core.client.OrdealTalents;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = "ordeal")
public final class OrdealProjectile {

	private OrdealProjectile() {}

	private static final String FLAT = "ordeal_flatDamage";
	private static final String HOMING = "ordeal_homing";
	private static final String HOMING_RANGE = "ordeal_homingRange";
	private static final String HOME_TARGET = "ordeal_homeTarget";

	/** Widest angle off the current heading a projectile will accept a target at. */
	public static double HOMING_CONE = net.mcreator.ordeal.OrdealTuning.d("projectile.homing_cone", 60.0);
	/** Ticks of straight flight before steering starts, so point blank stays honest. */
	public static int HOMING_DELAY = net.mcreator.ordeal.OrdealTuning.i("projectile.homing_delay", 4);
	/** Only this mod's own projectiles - a vanilla bow must keep vanilla rules. */
	private static final String OWN_PACKAGE = "net.mcreator.ordeal.entity.";

	/** Stamp a projectile by hand, when the damage is not the one in `damage`. */
	public static void arm(Entity projectile, double damage) {
		if (projectile != null && damage > 0)
			projectile.getPersistentData().putDouble(FLAT, damage);
	}

	/** Read back what a projectile is carrying. 0 when it is not armed. */
	public static double armed(Entity projectile) {
		return projectile == null ? 0 : projectile.getPersistentData().getDouble(FLAT);
	}

	@SubscribeEvent
	public static void onSpawn(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide()) return;
		if (!(event.getEntity() instanceof Projectile pr)) return;
		if (!pr.getClass().getName().startsWith(OWN_PACKAGE)) return;
		if (pr.getPersistentData().contains(FLAT)) return;      // armed by hand already
		if (!(pr.getOwner() instanceof Player p)) return;

		double d = p.getData(OrdealModVariables.PLAYER_VARIABLES).damage;
		if (d > 0) pr.getPersistentData().putDouble(FLAT, d);

		// Stamp the flight config too, so tick() never needs a talent lookup.
		// OrdealTalents reads through the client resource manager, which a
		// dedicated server does not have - a stamped number always works.
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(
				p.getData(OrdealModVariables.PLAYER_VARIABLES).abilityName.replace("\"", ""));
		if (ab != null && ab.projectile != null) {
			pr.getPersistentData().putDouble(HOMING, ab.projectile.homing);
			pr.getPersistentData().putDouble(HOMING_RANGE, ab.projectile.homingRange);
		}
	}

	// ---- homing -------------------------------------------------------------

	/**
	 * Call once per tick from the projectile's "while flying" procedure.
	 *
	 * Steering is a LEAN, not a lock: the heading is blended toward the target
	 * by `homing` each tick and the speed is preserved exactly, so a homing shot
	 * can never outrun a straight one - and, with the damage pinned separately,
	 * can never hit harder either.
	 */
	public static void tick(Entity e) {
		if (e == null || e.level().isClientSide()) return;
		if (!(e instanceof Projectile pr)) return;

		double strength = pr.getPersistentData().getDouble(HOMING);
		if (strength <= 0) return;
		if (pr.tickCount < HOMING_DELAY) return;

		LivingEntity target = target(pr);
		if (target == null) return;

		Vec3 vel = pr.getDeltaMovement();
		double speed = vel.length();
		if (speed < 1.0e-4) return;

		Vec3 want = target.position()
				.add(0, target.getBbHeight() * 0.5, 0)
				.subtract(pr.position());
		if (want.lengthSqr() < 1.0e-6) return;
		want = want.normalize();

		double h = Math.max(0, Math.min(1, strength));
		Vec3 dir = vel.normalize().scale(1 - h).add(want.scale(h));
		if (dir.lengthSqr() < 1.0e-6) return;
		dir = dir.normalize();

		pr.setDeltaMovement(dir.scale(speed));      // heading changes, speed does not
		pr.setYRot((float) (Math.atan2(dir.x, dir.z) * (180 / Math.PI)));
		pr.setXRot((float) (Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))
				* (180 / Math.PI)));
	}

	/** Keeps its target while that target stays valid, so the flight does not jitter. */
	private static LivingEntity target(Projectile pr) {
		int held = pr.getPersistentData().getInt(HOME_TARGET);
		if (held > 0 && pr.level().getEntity(held) instanceof LivingEntity le && valid(pr, le))
			return le;

		double range = Math.max(1, pr.getPersistentData().getDouble(HOMING_RANGE));
		Vec3 heading = pr.getDeltaMovement().normalize();
		double cosLimit = Math.cos(Math.toRadians(HOMING_CONE));

		LivingEntity best = null;
		double bestDot = cosLimit;
		for (LivingEntity le : pr.level().getEntitiesOfClass(LivingEntity.class,
				new AABB(pr.position(), pr.position()).inflate(range), x -> valid(pr, x))) {
			Vec3 to = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(pr.position());
			if (to.lengthSqr() < 1.0e-6) continue;
			double dot = heading.dot(to.normalize());   // 1 = dead ahead
			if (dot > bestDot) { bestDot = dot; best = le; }
		}
		if (best != null) pr.getPersistentData().putInt(HOME_TARGET, best.getId());
		return best;
	}

	private static boolean valid(Projectile pr, LivingEntity le) {
		return le.isAlive() && le != pr.getOwner() && le.isPickable();
	}

	/**
	 * HIGHEST, so the number is corrected BEFORE OrdealCombat runs it through
	 * gate, guard, chip and the combo multiplier. Everything downstream then
	 * works from the damage the ability actually meant to do.
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onHit(LivingIncomingDamageEvent event) {
		Entity direct = event.getSource().getDirectEntity();
		if (direct == null || direct == event.getSource().getEntity()) return;
		double flat = armed(direct);
		if (flat <= 0) return;
		event.setAmount((float) flat);
	}
}