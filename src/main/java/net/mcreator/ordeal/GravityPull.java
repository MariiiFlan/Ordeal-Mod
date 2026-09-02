package net.mcreator.ordeal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = "ordeal")
public final class GravityPull {

	public static double MAX_SPEED = OrdealTuning.d("pull.max_horizontal_speed", 0.55);
	public static double DEAD_ZONE = OrdealTuning.d("pull.dead_zone_blocks", 0.6);
	public static double EDGE_BIAS = OrdealTuning.d("pull.edge_bias", 0.5);
	public static double LIFT_CAP  = OrdealTuning.d("pull.max_lift_speed", 0.30);
	public static int    EVERY     = OrdealTuning.i("pull.every_ticks", 1);

	private static final List<Zone> ZONES = new ArrayList<>();

	private GravityPull() {}

	public static final class Zone {
		ServerLevel level;
		double x, y, z;
		double radius;
		double strength;
		double lift;
		int ticksLeft;
		UUID exempt;
		boolean includePlayers = true;

		public Zone lift(double v) { this.lift = v; return this; }
		public Zone mobsOnly() { this.includePlayers = false; return this; }
		public Zone radius(double v) { this.radius = Math.max(0.5, v); return this; }
		public Zone strength(double v) { this.strength = v; return this; }
		public void close() { ZONES.remove(this); }
	}

	public static Zone open(Entity caster, Vec3 centre, double radius, double strength, int ticks) {
		if (centre == null || caster == null) return null;
		if (!(caster.level() instanceof ServerLevel sl)) return null;
		Zone z = new Zone();
		z.level = sl;
		z.x = centre.x;
		z.y = centre.y;
		z.z = centre.z;
		z.radius = Math.max(0.5, radius);
		z.strength = strength;
		z.ticksLeft = Math.max(1, ticks);
		z.exempt = caster.getUUID();
		ZONES.add(z);
		return z;
	}

	public static Zone openAtFeet(Entity caster, double radius, double strength, int ticks) {
		return caster == null ? null : open(caster, caster.position(), radius, strength, ticks);
	}

	public static Zone openAtLook(Entity caster, double range, double radius, double strength, int ticks) {
		if (caster == null) return null;
		Vec3 eye = caster.getEyePosition(1f);
		Vec3 end = eye.add(caster.getLookAngle().scale(Math.max(1, range)));
		if (!(caster.level() instanceof ServerLevel sl)) return null;
		var hit = sl.clip(new net.minecraft.world.level.ClipContext(eye, end,
				net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
		Vec3 at = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : hit.getLocation();
		return open(caster, at, radius, strength, ticks);
	}

	public static void closeAll(Entity caster) {
		if (caster == null) return;
		UUID id = caster.getUUID();
		ZONES.removeIf(z -> id.equals(z.exempt));
	}

	public static boolean active(Entity caster) {
		if (caster == null) return false;
		UUID id = caster.getUUID();
		for (Zone z : ZONES) if (id.equals(z.exempt)) return true;
		return false;
	}

	public static void apply(ServerLevel level, double cx, double cy, double cz, double radius,
			double strength, double lift, UUID exempt, boolean includePlayers) {
		if (level == null || strength <= 0 || radius <= 0) return;
		AABB box = new AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius);
		for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (exempt != null && exempt.equals(le.getUUID())) continue;
			if (!includePlayers && le instanceof Player) continue;

			double dx = cx - le.getX();
			double dy = cy - (le.getY() + le.getBbHeight() * 0.5);
			double dz = cz - le.getZ();
			double flat = Math.sqrt(dx * dx + dz * dz);
			if (Math.sqrt(flat * flat + dy * dy) > radius) continue;
			if (flat < Math.max(1.0e-4, DEAD_ZONE)) continue;

			double reach = Math.min(1.0, flat / radius);
			double scale = strength * (1.0 - EDGE_BIAS + EDGE_BIAS * reach);

			Vec3 m = le.getDeltaMovement();
			double nx = m.x + dx / flat * scale;
			double nz = m.z + dz / flat * scale;
			double flatSpeed = Math.sqrt(nx * nx + nz * nz);
			if (flatSpeed > MAX_SPEED) {
				nx = nx / flatSpeed * MAX_SPEED;
				nz = nz / flatSpeed * MAX_SPEED;
			}

			double ny = m.y;
			if (lift != 0) {
				ny += Math.signum(dy) * Math.min(Math.abs(lift), LIFT_CAP);
				if (Math.abs(ny) > LIFT_CAP * 3) ny = Math.signum(ny) * LIFT_CAP * 3;
			}

			le.setDeltaMovement(nx, ny, nz);
			le.hurtMarked = true;
		}
	}

	public static void once(Entity caster, Vec3 centre, double radius, double strength) {
		if (caster == null || centre == null) return;
		if (!(caster.level() instanceof ServerLevel sl)) return;
		apply(sl, centre.x, centre.y, centre.z, radius, strength, 0, caster.getUUID(), true);
	}

	@SubscribeEvent
	public static void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
		ZONES.clear();
	}

	@SubscribeEvent
	public static void onTick(ServerTickEvent.Post event) {
		if (ZONES.isEmpty()) return;
		Iterator<Zone> it = ZONES.iterator();
		while (it.hasNext()) {
			Zone z = it.next();
			z.ticksLeft--;
			if (z.ticksLeft <= 0) { it.remove(); continue; }
			if (z.level.getGameTime() % Math.max(1, EVERY) != 0) continue;
			apply(z.level, z.x, z.y, z.z, z.radius, z.strength, z.lift, z.exempt, z.includePlayers);
		}
	}
}