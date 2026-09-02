package net.mcreator.ordeal;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "ordeal")
public final class AnimFx {

	public static double ARM_LENGTH   = OrdealTuning.d("animfx.arm_length_px", 10);
	public static double SHOULDER_UP  = OrdealTuning.d("animfx.shoulder_up_px", 2);
	public static double SHOULDER_OUT = OrdealTuning.d("animfx.shoulder_out_px", 5);
	public static double MODEL_TOP    = OrdealTuning.d("animfx.model_top_blocks", 1.5);
	public static double BODY_PIVOT   = OrdealTuning.d("animfx.body_pivot_px", 12);
	public static int    EVERY        = OrdealTuning.i("animfx.every_ticks", 1);
	public static int    HOLD_GRACE   = OrdealTuning.i("animfx.hold_grace_ticks", 3);

	private static final float[][] AKONITO_LEFT_ARM = {
			{ 0.000f,   0.0000f,   0.0000f,   0.0000f },
			{ 0.125f, -79.1522f,  20.9071f,  15.6644f },
			{ 0.250f, -79.1522f,  20.9071f,  15.6644f },
			{ 0.375f,   1.1071f, -25.0391f, -30.0269f },
	};

	private static final float[][] AKONITO_RIGHT_ARM = {
			{ 0.000f,   0.0000f,   0.0000f,   0.0000f },
			{ 0.125f, -70.7763f, -26.7338f, -30.0552f },
			{ 0.250f, -70.7763f, -26.7338f, -30.0552f },
			{ 0.375f,  -5.0930f,  39.1948f,  14.1783f },
	};

	private static final float[][] BREATH_LEFT_ARM = {
			{ 0.000f,  0.0000f, 0.0000f,   0.0000f },
			{ 0.250f, -1.1047f, 0.2507f, -22.3724f },
	};

	private static final float[][] BREATH_RIGHT_ARM = {
			{ 0.000f, 0.0000f, 0.0000f,  0.0000f },
			{ 0.250f, 0.0000f, 0.0000f, 32.5000f },
	};

	private static final float[][] BREATH_BODY = {
			{ 0.000f,  0.0f },
			{ 0.250f, 22.5f },
	};

	private static final Map<String, Track> TRACKS = new HashMap<>();
	static {
		TRACKS.put("akonito_left", new Track("photon:ilios_akonitoflames", 0.0f, 0.42f, false, null,
				new Arm(false, AKONITO_LEFT_ARM)));
		TRACKS.put("akonito_right", new Track("photon:ilios_akonitoflames", 0.0f, 0.42f, false, null,
				new Arm(true, AKONITO_RIGHT_ARM)));
		TRACKS.put("breath_of_phoenix", new Track("photon:ilios_akonitoflames", 0.0f, 0.30f, true, BREATH_BODY,
				new Arm(false, BREATH_LEFT_ARM), new Arm(true, BREATH_RIGHT_ARM)));
	}

	private static final class Arm {
		final boolean rightSide;
		final float[][] keys;
		Arm(boolean rightSide, float[][] keys) {
			this.rightSide = rightSide; this.keys = keys;
		}
	}

	private static final class Track {
		final String fx;
		final float from, to;
		final boolean hold;
		final float[][] body;
		final Arm[] arms;
		Track(String fx, float from, float to, boolean hold, float[][] body, Arm... arms) {
			this.fx = fx; this.from = from; this.to = to; this.hold = hold; this.body = body; this.arms = arms;
		}
	}

	private static final class Run {
		Track t;
		int tick;
		long lastPlay;
	}

	private static final Map<UUID, Run> RUNNING = new HashMap<>();

	private AnimFx() {}

	public static void play(Entity e, String clip) {
		if (!(e instanceof Player p) || p.level().isClientSide() || clip == null) return;
		String key = clip.contains(":") ? clip.substring(clip.indexOf(':') + 1) : clip;
		Track t = TRACKS.get(key);
		if (t == null) return;
		long now = p.level().getGameTime();
		Run r = RUNNING.get(p.getUUID());
		if (r != null && r.t == t && t.hold) {
			r.lastPlay = now;
			return;
		}
		r = new Run();
		r.t = t;
		r.tick = 0;
		r.lastPlay = now;
		RUNNING.put(p.getUUID(), r);
		emit(p, t, 0);
	}

	public static void cancel(Entity e) {
		if (e instanceof Player p) RUNNING.remove(p.getUUID());
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (p.level().isClientSide()) return;
		Run r = RUNNING.get(p.getUUID());
		if (r == null) return;

		r.tick++;
		float t = r.tick / 20f;
		if (r.t.hold) {
			if (p.level().getGameTime() - r.lastPlay > Math.max(1, HOLD_GRACE)) {
				RUNNING.remove(p.getUUID());
				return;
			}
			if (t > r.t.to) t = r.t.to;
		} else if (t > r.t.to) {
			RUNNING.remove(p.getUUID());
			return;
		}
		if (t < r.t.from) return;
		if (r.tick % Math.max(1, EVERY) != 0) return;
		emit(p, r.t, t);
	}

	private static void emit(Player p, Track tr, float time) {
		if (p.getServer() == null) return;
		float bodyPitch = tr.body == null ? 0f : sampleBody(tr.body, time);
		for (Arm a : tr.arms) {
			float[] rot = sample(a.keys, time);
			Vec3 off = handOffset(p, a.rightSide, rot, bodyPitch);
			p.getServer().getCommands().performPrefixedCommand(
					new CommandSourceStack(CommandSource.NULL, p.position(), p.getRotationVector(),
							p.level() instanceof ServerLevel sl ? sl : null, 4,
							p.getName().getString(), p.getDisplayName(), p.level().getServer(), p),
					String.format(java.util.Locale.ROOT,
							"photon fx %s entity @s %.3f %.3f %.3f 0 0 0 1 1 1 0 false true none",
							tr.fx, off.x, off.y, off.z));
		}
	}

	private static float[] sample(float[][] k, float t) {
		if (k.length == 0) return new float[] { 0, 0, 0 };
		if (t <= k[0][0]) return new float[] { k[0][1], k[0][2], k[0][3] };
		for (int i = 0; i < k.length - 1; i++) {
			if (t <= k[i + 1][0]) {
				float span = k[i + 1][0] - k[i][0];
				float f = span <= 0 ? 0 : (t - k[i][0]) / span;
				return new float[] {
						k[i][1] + (k[i + 1][1] - k[i][1]) * f,
						k[i][2] + (k[i + 1][2] - k[i][2]) * f,
						k[i][3] + (k[i + 1][3] - k[i][3]) * f };
			}
		}
		float[] e = k[k.length - 1];
		return new float[] { e[1], e[2], e[3] };
	}

	private static float sampleBody(float[][] k, float t) {
		if (k.length == 0) return 0f;
		if (t <= k[0][0]) return k[0][1];
		for (int i = 0; i < k.length - 1; i++) {
			if (t <= k[i + 1][0]) {
				float span = k[i + 1][0] - k[i][0];
				float f = span <= 0 ? 0 : (t - k[i][0]) / span;
				return k[i][1] + (k[i + 1][1] - k[i][1]) * f;
			}
		}
		return k[k.length - 1][1];
	}

	private static Vec3 handOffset(Player p, boolean rightSide, float[] rotDeg, float bodyPitchDeg) {
		double rx = Math.toRadians(rotDeg[0]);
		double ry = Math.toRadians(rotDeg[1]);
		double rz = Math.toRadians(rotDeg[2]);

		double vx = 0, vy = ARM_LENGTH, vz = 0;

		double y1 = vy * Math.cos(rx) - vz * Math.sin(rx);
		double z1 = vy * Math.sin(rx) + vz * Math.cos(rx);
		double x1 = vx;

		double x2 = x1 * Math.cos(ry) + z1 * Math.sin(ry);
		double z2 = -x1 * Math.sin(ry) + z1 * Math.cos(ry);
		double y2 = y1;

		double x3 = x2 * Math.cos(rz) - y2 * Math.sin(rz);
		double y3 = x2 * Math.sin(rz) + y2 * Math.cos(rz);
		double z3 = z2;

		double shoulderMx = rightSide ? -SHOULDER_OUT : SHOULDER_OUT;

		double mpx = shoulderMx + x3;
		double hpx = MODEL_TOP * 16.0 - (SHOULDER_UP + y3);
		double fpx = -z3;

		double bp = Math.toRadians(bodyPitchDeg);
		double dy = hpx - BODY_PIVOT;
		double f2 = fpx * Math.cos(bp) + dy * Math.sin(bp);
		double h2 = BODY_PIVOT + dy * Math.cos(bp) - fpx * Math.sin(bp);

		double right = -mpx / 16.0;
		double up    = h2 / 16.0;
		double fwd   = f2 / 16.0;

		double yaw = Math.toRadians(p.yBodyRot);
		double fX = -Math.sin(yaw), fZ = Math.cos(yaw);
		double rX = -Math.cos(yaw), rZ = -Math.sin(yaw);

		double wx = p.getX() + rX * right + fX * fwd;
		double wy = p.getY() + up;
		double wz = p.getZ() + rZ * right + fZ * fwd;

		Vec3 eye = p.getEyePosition(1f);
		return new Vec3(wx - eye.x, wy - eye.y, wz - eye.z);
	}
}