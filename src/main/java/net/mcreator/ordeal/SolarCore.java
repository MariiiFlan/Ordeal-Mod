package net.mcreator.ordeal;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class SolarCore {

	public static boolean ENABLED = true;

	public static double RADIUS_MIN_BLOCKS   = OrdealTuning.d("solarcore.radius_min_blocks", 0.11);
	public static double RADIUS_MAX_BLOCKS   = OrdealTuning.d("solarcore.radius_max_blocks", 1.05);
	public static double HAND_FORWARD        = OrdealTuning.d("solarcore.hand_forward_blocks", 0.55);
	public static double HAND_SIDE           = OrdealTuning.d("solarcore.hand_side_blocks", 0.34);
	public static double HAND_BELOW_EYE      = OrdealTuning.d("solarcore.hand_below_eye_blocks", -0.05);
	public static double FIRST_PERSON_PUSH   = OrdealTuning.d("solarcore.first_person_push", 1.35);

	public static double CORONA_REACH_BASE   = OrdealTuning.d("solarcore.corona_reach_base", 1.9);
	public static double CORONA_REACH_GROWTH = OrdealTuning.d("solarcore.corona_reach_growth", 1.0);
	public static double CORONA_ALPHA        = OrdealTuning.d("solarcore.corona_alpha", 0.34);
	public static double SPILL_ALPHA         = OrdealTuning.d("solarcore.spill_alpha", 0.10);
	public static double LIMB_ALPHA          = OrdealTuning.d("solarcore.limb_alpha", 0.30);

	public static int    SPIKE_COUNT         = OrdealTuning.i("solarcore.spike_count", 22);
	public static double SPIKE_ALPHA         = OrdealTuning.d("solarcore.spike_alpha", 0.22);
	public static int    RIBBON_MIN          = OrdealTuning.i("solarcore.ribbon_min", 2);
	public static int    RIBBON_MAX          = OrdealTuning.i("solarcore.ribbon_max", 4);
	public static int    RIBBON_SEGMENTS     = OrdealTuning.i("solarcore.ribbon_segments", 26);
	public static double RIBBON_WIDTH        = OrdealTuning.d("solarcore.ribbon_width", 0.075);
	public static int    EMBER_MAX           = OrdealTuning.i("solarcore.ember_max", 44);
	public static double EMBER_RATE          = OrdealTuning.d("solarcore.ember_rate_per_second", 26);

	public static int    BURST_TICKS         = OrdealTuning.i("solarcore.burst_ticks", 16);
	public static double BURST_REACH         = OrdealTuning.d("solarcore.burst_reach_fraction", 0.85);
	public static int    HELD_EXPIRE_TICKS   = OrdealTuning.i("solarcore.held_expire_ticks", 15);

	public static int    SHELL_TEXTURE_SIZE  = OrdealTuning.i("solarcore.shell_texture_size", 96);
	public static int    SHELL_REPAINT_MS    = OrdealTuning.i("solarcore.shell_repaint_ms", 50);
	public static int    GLOW_TEXTURE_SIZE   = OrdealTuning.i("solarcore.glow_texture_size", 64);

	private static final int[][] RAMP = {
			{ 0x0d, 0x02, 0x01 }, { 0x2e, 0x08, 0x03 }, { 0x6b, 0x16, 0x04 }, { 0xb8, 0x33, 0x08 },
			{ 0xf2, 0x69, 0x0d }, { 0xff, 0x9a, 0x1e }, { 0xff, 0xd0, 0x5a }, { 0xff, 0xf6, 0xd8 } };
	private static final float[] GLOW_RGB = { 255 / 255f, 122 / 255f, 16 / 255f };
	private static final float[] RIM_RGB  = { 255 / 255f, 216 / 255f, 138 / 255f };

	private static final int STAGES = 6;
	private static final int FULL_BRIGHT = 0xF000F0;

	private SolarCore() {}

	private static final class Ember {
		float x, y, vx, vy, age, life, size;
	}

	private static final class Orb {
		int entityId = -1;
		double x, y, z;
		double tx, ty, tz;          // where the server last put a flying sun
		float shown;
		float target;
		float dispRadius;           // server-driven size in blocks; 0 = derive from intensity
		float shownR;               // smoothed size actually painted
		float anchor;               // 0 = right hand, 1 = above the head
		int stage;
		int idleTicks;
		int holdTicks;
		final List<Ember> embers = new ArrayList<>();
		float emberDebt;
	}

	private static final class Burst {
		double x, y, z;
		float intensity;
		float radius;
		int age;
	}

	private static final Map<Integer, Orb> HELD = new HashMap<>();
	private static final Map<Integer, Orb> FLYING = new HashMap<>();
	private static final List<Orb> HANGING = new ArrayList<>();
	private static final List<Burst> BURSTS = new ArrayList<>();

	/** The stage the sun moves from the hand to above the head - same tunable the server uses. */
	public static int GROW_FROM = OrdealTuning.i("tomas.gravity_from_stage", 3);

	public static void accept(SolarCorePayload p) {
		if (!ENABLED) return;
		if (p.kind() == SolarCorePayload.HELD) {
			if (p.intensity() < 0) { HELD.remove(p.entityId()); return; }
			Orb o = HELD.computeIfAbsent(p.entityId(), k -> new Orb());
			o.entityId = p.entityId();
			o.target = Math.max(0f, Math.min(1f, p.intensity()));
			o.dispRadius = Math.max(0f, p.radius());
			o.stage = p.ticks();
			o.idleTicks = 0;
			return;
		}
		if (p.kind() == SolarCorePayload.FLY) {
			if (p.intensity() < 0) { FLYING.remove(p.entityId()); return; }
			Orb o = FLYING.get(p.entityId());
			if (o == null) {
				o = new Orb();
				o.x = p.x(); o.y = p.y(); o.z = p.z();
				o.shown = Math.max(0f, Math.min(1f, p.intensity()));
				FLYING.put(p.entityId(), o);
			}
			o.tx = p.x(); o.ty = p.y(); o.tz = p.z();
			o.target = Math.max(0f, Math.min(1f, p.intensity()));
			o.dispRadius = Math.max(0f, p.radius());
			o.idleTicks = 0;
			return;
		}
		if (p.kind() == SolarCorePayload.HANG) {
			Orb o = new Orb();
			o.x = p.x(); o.y = p.y(); o.z = p.z();
			o.target = Math.max(0f, Math.min(1f, p.intensity()));
			o.shown = o.target;
			o.dispRadius = Math.max(0f, p.radius());
			o.shownR = o.dispRadius;
			o.holdTicks = Math.max(1, p.ticks());
			HANGING.add(o);
			return;
		}
		Burst b = new Burst();
		b.x = p.x(); b.y = p.y(); b.z = p.z();
		b.intensity = Math.max(0f, Math.min(1f, p.intensity()));
		b.radius = Math.max(1f, p.radius());
		BURSTS.add(b);
		if (BURSTS.size() > 12) BURSTS.remove(0);
	}

	public static void clear() {
		HELD.clear();
		FLYING.clear();
		HANGING.clear();
		BURSTS.clear();
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (!event.getLevel().isClientSide()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) { clear(); return; }
		if (mc.isPaused()) return;

		for (Iterator<Map.Entry<Integer, Orb>> it = HELD.entrySet().iterator(); it.hasNext(); ) {
			Orb o = it.next().getValue();
			o.idleTicks++;
			if (o.idleTicks > HELD_EXPIRE_TICKS || mc.level.getEntity(o.entityId) == null) it.remove();
		}
		for (Iterator<Map.Entry<Integer, Orb>> it = FLYING.entrySet().iterator(); it.hasNext(); ) {
			if (++it.next().getValue().idleTicks > HELD_EXPIRE_TICKS) it.remove();
		}
		for (Iterator<Orb> it = HANGING.iterator(); it.hasNext(); ) {
			Orb o = it.next();
			if (--o.holdTicks <= 0) it.remove();
		}
		for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext(); ) {
			if (++it.next().age > BURST_TICKS) it.remove();
		}
	}

	@SubscribeEvent
	public static void onRender(RenderLevelStageEvent event) {
		if (!ENABLED) return;
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
		if (HELD.isEmpty() && FLYING.isEmpty() && HANGING.isEmpty() && BURSTS.isEmpty()) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;

		float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		float seconds = (mc.level.getGameTime() + partial) / 20f;
		float dt = event.getPartialTick().getRealtimeDeltaTicks() / 20f;
		if (dt > 0.05f) dt = 0.05f;

		Vec3 cam = event.getCamera().getPosition();
		Quaternionf rot = event.getCamera().rotation();
		Vector3f right = rot.transform(new Vector3f(1, 0, 0), new Vector3f());
		Vector3f up = rot.transform(new Vector3f(0, 1, 0), new Vector3f());

		PoseStack pose = event.getPoseStack();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

		for (Orb o : HELD.values()) {
			Entity e = mc.level.getEntity(o.entityId);
			if (e == null) continue;
			o.shown += (o.target - o.shown) * Math.min(1f, dt * 4f);
			float rTarget = o.dispRadius > 0 ? o.dispRadius : (float) radiusOf(o.shown);
			if (o.shownR <= 0) o.shownR = rTarget;
			o.shownR += (rTarget - o.shownR) * Math.min(1f, dt * 4f);
			// stage 0-2 the sun sits in the right hand; from stage 3 it drifts
			// up above the head as it grows
			o.anchor += ((o.stage >= GROW_FROM ? 1f : 0f) - o.anchor) * Math.min(1f, dt * 2.5f);
			Vec3 at = handPoint(mc, e, partial, o.shownR)
					.lerp(overheadPoint(e, partial, o.shownR), o.anchor);
			paint(pose, buffers, cam, right, up, at, o, o.shown, o.shownR, seconds, dt, 1f);
		}

		for (Orb o : FLYING.values()) {
			o.x += (o.tx - o.x) * Math.min(1f, dt * 14f);
			o.y += (o.ty - o.y) * Math.min(1f, dt * 14f);
			o.z += (o.tz - o.z) * Math.min(1f, dt * 14f);
			o.shown += (o.target - o.shown) * Math.min(1f, dt * 4f);
			float r = o.dispRadius > 0 ? o.dispRadius : (float) radiusOf(o.shown);
			paint(pose, buffers, cam, right, up, new Vec3(o.x, o.y, o.z), o, o.shown, r, seconds, dt, 1f);
		}

		for (Orb o : HANGING) {
			Vec3 at = new Vec3(o.x, o.y, o.z);
			float r = o.dispRadius > 0 ? o.dispRadius : (float) radiusOf(o.shown);
			paint(pose, buffers, cam, right, up, at, o, o.shown, r, seconds, dt, 1f);
		}

		for (Burst b : BURSTS) {
			float f = Math.min(1f, (b.age + partial) / Math.max(1, BURST_TICKS));
			float fade = 1f - f * f;
			if (fade <= 0.02f) continue;
			double r = radiusOf(b.intensity) + (b.radius * BURST_REACH - radiusOf(b.intensity)) * Math.sqrt(f);
			quad(pose, buffers, glowTexture(), cam, right, up, new Vec3(b.x, b.y, b.z),
					r * 2.6, GLOW_RGB, (float) (CORONA_ALPHA * fade * 1.6));
			quad(pose, buffers, shellTexture(5, seconds), cam, right, up, new Vec3(b.x, b.y, b.z),
					r * 2, new float[] { 1f, 1f, 1f }, fade);
			quad(pose, buffers, glowTexture(), cam, right, up, new Vec3(b.x, b.y, b.z),
					r * 2.15, RIM_RGB, (float) (LIMB_ALPHA * fade));
		}

		buffers.endBatch();
	}

	private static double radiusOf(float intensity) {
		return RADIUS_MIN_BLOCKS + (RADIUS_MAX_BLOCKS - RADIUS_MIN_BLOCKS) * Math.max(0, Math.min(1, intensity));
	}

	private static Vec3 handPoint(Minecraft mc, Entity e, float partial, double radius) {
		double px = e.xOld + (e.getX() - e.xOld) * partial;
		double py = e.yOld + (e.getY() - e.yOld) * partial;
		double pz = e.zOld + (e.getZ() - e.zOld) * partial;

		float yaw = e instanceof net.minecraft.world.entity.LivingEntity le
				? le.yBodyRotO + (le.yBodyRot - le.yBodyRotO) * partial
				: e.getYRot();
		double a = Math.toRadians(yaw);
		double fx = -Math.sin(a), fz = Math.cos(a);
		double rx = Math.cos(a), rz = Math.sin(a);

		// tomas_start raises the arm, so the palm sits near eye height - and a
		// growing orb rides further out in front so it never sinks into the body
		double forward = HAND_FORWARD + radius * 0.55;
		boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
		if (self) forward += radius * FIRST_PERSON_PUSH;

		double side = HAND_SIDE;
		if (e instanceof Player pl && pl.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT) side = -side;

		return new Vec3(px + fx * forward + rx * side,
				py + e.getEyeHeight() - HAND_BELOW_EYE,
				pz + fz * forward + rz * side);
	}

	private static Vec3 overheadPoint(Entity e, float partial, double radius) {
		double px = e.xOld + (e.getX() - e.xOld) * partial;
		double py = e.yOld + (e.getY() - e.yOld) * partial;
		double pz = e.zOld + (e.getZ() - e.zOld) * partial;
		return new Vec3(px, py + e.getBbHeight() + radius + 0.35, pz);
	}

	private static void paint(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam,
			Vector3f right, Vector3f up, Vec3 at, Orb orb, float intensity, double r,
			float seconds, float dt, float alpha) {

		float flick = (float) (0.86 + 0.14 * Math.sin(seconds * 5.3) * Math.sin(seconds * 2.1));

		quad(pose, buffers, glowTexture(), cam, right, up, at,
				r * 12, GLOW_RGB, (float) (SPILL_ALPHA + 0.06 * intensity) * alpha);

		double reach = r * (CORONA_REACH_BASE + CORONA_REACH_GROWTH * intensity);
		quad(pose, buffers, glowTexture(), cam, right, up, at,
				reach * 2, GLOW_RGB, (float) (CORONA_ALPHA * flick * (0.5 + 0.5 * intensity)) * alpha);

		spikes(pose, buffers, cam, right, up, at, r, seconds, intensity, alpha);
		ribbons(pose, buffers, cam, right, up, at, r, seconds, intensity, alpha);

		quad(pose, buffers, shellTexture(stageOf(intensity), seconds), cam, right, up, at,
				r * 2, new float[] { 1f, 1f, 1f }, alpha);

		quad(pose, buffers, glowTexture(), cam, right, up, at,
				r * 2.28, RIM_RGB, (float) LIMB_ALPHA * alpha);

		embers(pose, buffers, cam, right, up, at, orb, r, dt, intensity, alpha);
	}

	private static int stageOf(float intensity) {
		int s = Math.round(intensity * (STAGES - 1));
		return Math.max(0, Math.min(STAGES - 1, s));
	}

	private static void spikes(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam,
			Vector3f right, Vector3f up, Vec3 at, double r, float seconds, float intensity, float alpha) {
		int n = Math.max(1, SPIKE_COUNT);
		VertexConsumer vc = buffers.getBuffer(typeFor(glowTexture()));
		Matrix4f m = pose.last().pose();
		float a = (float) (SPIKE_ALPHA * (0.6 + 0.7 * intensity)) * alpha;
		for (int i = 0; i < n; i++) {
			float ang = (float) (i / (double) n * Math.PI * 2 + seconds * 0.09 + vn(i * 5.1, 0) * 0.3);
			float f = (float) vn(i * 3.7, seconds * 1.4);
			if (f < 0.42f) continue;
			double len = r * (0.06 + 0.5 * (f - 0.42) * (0.4 + 0.6 * intensity));
			double w = r * 0.05 * (0.5 + f * 0.7);
			double c = Math.cos(ang), s = Math.sin(ang);

			Vector3f dir = new Vector3f(right).mul((float) c).add(new Vector3f(up).mul((float) s));
			Vector3f perp = new Vector3f(right).mul((float) -s).add(new Vector3f(up).mul((float) c));
			Vec3 base = at.add(dir.x * r * 0.94, dir.y * r * 0.94, dir.z * r * 0.94);
			Vec3 tip = at.add(dir.x * (r * 0.94 + len), dir.y * (r * 0.94 + len), dir.z * (r * 0.94 + len));

			strip(vc, m, cam, base, tip, perp, w, w * 0.18, GLOW_RGB, a);
		}
	}

	private static void ribbons(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam,
			Vector3f right, Vector3f up, Vec3 at, double r, float seconds, float intensity, float alpha) {
		int n = RIBBON_MIN + Math.round(intensity * Math.max(0, RIBBON_MAX - RIBBON_MIN));
		VertexConsumer vc = buffers.getBuffer(typeFor(glowTexture()));
		Matrix4f m = pose.last().pose();
		int steps = Math.max(6, RIBBON_SEGMENTS);

		for (int i = 0; i < n; i++) {
			double seed = i * 2.31 + 0.6;
			double th = 0.55 + Math.sin(seed * 1.7) * 0.75;
			double ph = seed * 1.9 + seconds * (0.11 + 0.03 * i);
			double nx = Math.sin(th) * Math.cos(ph), ny = Math.sin(th) * Math.sin(ph), nz = Math.cos(th);
			double ux = -ny, uy = nx, uz = 0;
			double ul = Math.hypot(ux, uy);
			if (ul < 1.0e-5) { ux = 1; uy = 0; ul = 1; }
			ux /= ul; uy /= ul;
			double vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;

			double rad = r * (1.04 + 0.13 * i);
			double span = 2.5 + 0.6 * ((i * 7) % 3);
			double a0 = seed * 2.7 + seconds * (0.95 + 0.3 * i);
			double maxW = r * RIBBON_WIDTH * (0.6 + 0.6 * intensity);

			Vec3 prev = null;
			double prevTaper = 0;
			for (int s = 0; s <= steps; s++) {
				double u = s / (double) steps;
				double ang = a0 + u * span;
				double c = Math.cos(ang), si = Math.sin(ang);
				Vec3 pt = at.add(rad * (c * ux + si * vx), rad * (c * uy + si * vy), rad * (c * uz + si * vz));
				double taper = Math.sin(Math.PI * u);
				taper = taper * taper * taper;
				if (prev != null) {
					Vector3f seg = new Vector3f((float) (pt.x - prev.x), (float) (pt.y - prev.y), (float) (pt.z - prev.z));
					Vector3f toCam = new Vector3f((float) (prev.x - cam.x), (float) (prev.y - cam.y), (float) (prev.z - cam.z));
					Vector3f perp = new Vector3f(seg).cross(toCam);
					if (perp.lengthSquared() > 1.0e-8f) {
						perp.normalize();
						float fa = (float) (0.85 * Math.max(prevTaper, taper)) * alpha;
						strip(vc, m, cam, prev, pt, perp, maxW * prevTaper, maxW * taper,
								new float[] { 1f, 1f, 1f }, fa);
					}
				}
				prev = pt;
				prevTaper = taper;
			}
		}
	}

	private static void embers(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam,
			Vector3f right, Vector3f up, Vec3 at, Orb orb, double r, float dt, float intensity, float alpha) {
		orb.emberDebt += dt * (float) (EMBER_RATE * (0.2 + 0.8 * intensity));
		while (orb.emberDebt >= 1 && orb.embers.size() < EMBER_MAX) {
			orb.emberDebt -= 1;
			double a = Math.random() * Math.PI * 2;
			double out = 0.3 + Math.random() * 0.75;
			Ember em = new Ember();
			em.x = (float) (Math.cos(a) * 0.97);
			em.y = (float) (Math.sin(a) * 0.97);
			em.vx = (float) (Math.cos(a) * out);
			em.vy = (float) (Math.sin(a) * out + 0.3);
			em.life = (float) (0.5 + Math.random() * 0.95);
			em.size = (float) (0.5 + Math.random());
			orb.embers.add(em);
		}
		if (orb.emberDebt > 1) orb.emberDebt = 1;

		VertexConsumer vc = buffers.getBuffer(typeFor(glowTexture()));
		Matrix4f m = pose.last().pose();

		for (Iterator<Ember> it = orb.embers.iterator(); it.hasNext(); ) {
			Ember em = it.next();
			em.age += dt;
			if (em.age >= em.life) { it.remove(); continue; }
			em.vy += 0.55f * dt;
			em.x += em.vx * dt;
			em.y += em.vy * dt;
			float k = 1f - em.age / em.life;
			double size = r * 0.13 * em.size * k;
			Vec3 p = at.add(right.x * em.x * r + up.x * em.y * r,
					right.y * em.x * r + up.y * em.y * r,
					right.z * em.x * r + up.z * em.y * r);
			billboard(vc, m, cam, right, up, p, size, RIM_RGB, 0.85f * k * alpha);
		}
	}

	private static void quad(PoseStack pose, MultiBufferSource.BufferSource buffers, ResourceLocation tex,
			Vec3 cam, Vector3f right, Vector3f up, Vec3 at, double size, float[] rgb, float alpha) {
		if (alpha <= 0.004f || size <= 0) return;
		VertexConsumer vc = buffers.getBuffer(typeFor(tex));
		billboard(vc, pose.last().pose(), cam, right, up, at, size * 0.5, rgb, alpha);
	}

	private static void billboard(VertexConsumer vc, Matrix4f m, Vec3 cam,
			Vector3f right, Vector3f up, Vec3 at, double half, float[] rgb, float alpha) {
		if (alpha <= 0.004f || half <= 0) return;
		float cx = (float) (at.x - cam.x), cy = (float) (at.y - cam.y), cz = (float) (at.z - cam.z);
		float h = (float) half;
		float rx = right.x * h, ry = right.y * h, rz = right.z * h;
		float ux = up.x * h, uy = up.y * h, uz = up.z * h;
		float a = Math.min(1f, alpha);

		vertex(vc, m, cx - rx - ux, cy - ry - uy, cz - rz - uz, 0f, 1f, rgb, a);
		vertex(vc, m, cx + rx - ux, cy + ry - uy, cz + rz - uz, 1f, 1f, rgb, a);
		vertex(vc, m, cx + rx + ux, cy + ry + uy, cz + rz + uz, 1f, 0f, rgb, a);
		vertex(vc, m, cx - rx + ux, cy - ry + uy, cz - rz + uz, 0f, 0f, rgb, a);
	}

	private static void strip(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 from, Vec3 to,
			Vector3f perp, double wFrom, double wTo, float[] rgb, float alpha) {
		if (alpha <= 0.004f) return;
		float ax = (float) (from.x - cam.x), ay = (float) (from.y - cam.y), az = (float) (from.z - cam.z);
		float bx = (float) (to.x - cam.x), by = (float) (to.y - cam.y), bz = (float) (to.z - cam.z);
		float w0 = (float) wFrom, w1 = (float) wTo;
		float a = Math.min(1f, alpha);

		vertex(vc, m, ax - perp.x * w0, ay - perp.y * w0, az - perp.z * w0, 0f, 1f, rgb, a);
		vertex(vc, m, bx - perp.x * w1, by - perp.y * w1, bz - perp.z * w1, 1f, 1f, rgb, a);
		vertex(vc, m, bx + perp.x * w1, by + perp.y * w1, bz + perp.z * w1, 1f, 0f, rgb, a);
		vertex(vc, m, ax + perp.x * w0, ay + perp.y * w0, az + perp.z * w0, 0f, 0f, rgb, a);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z,
			float u, float v, float[] rgb, float a) {
		vc.addVertex(m, x, y, z)
				.setColor(rgb[0], rgb[1], rgb[2], a)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(FULL_BRIGHT)
				.setNormal(0f, 1f, 0f);
	}

	private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();

	private static RenderType typeFor(ResourceLocation tex) {
		// NOT eyes() - that one blends additively, which washes out into flat
		// yellow squares in daylight and adds nothing for the dark shell pixels.
		// Emissive translucent keeps the texture's alpha falloff and the dark
		// body of the sun, in any light.
		return TYPES.computeIfAbsent(tex, t -> RenderType.entityTranslucentEmissive(t));
	}

	private static ResourceLocation glowTex;

	private static ResourceLocation glowTexture() {
		if (glowTex != null) return glowTex;
		int s = Math.max(8, GLOW_TEXTURE_SIZE);
		DynamicTexture tex = new DynamicTexture(s, s, false);
		NativeImage img = tex.getPixels();
		if (img != null) {
			for (int y = 0; y < s; y++) {
				for (int x = 0; x < s; x++) {
					double nx = (x + 0.5) / s * 2 - 1, ny = (y + 0.5) / s * 2 - 1;
					double d = Math.sqrt(nx * nx + ny * ny);
					double f = d >= 1 ? 0 : Math.pow(1 - d, 2.6);
					int a = (int) Math.round(255 * f);
					img.setPixelRGBA(x, y, (a << 24) | 0x00FFFFFF);
				}
			}
			tex.upload();
		}
		glowTex = Minecraft.getInstance().getTextureManager().register("ordeal_solar_glow", tex);
		return glowTex;
	}

	private static final ResourceLocation[] shellTex = new ResourceLocation[STAGES];
	private static final DynamicTexture[] shellData = new DynamicTexture[STAGES];
	private static final long[] shellPainted = new long[STAGES];

	private static ResourceLocation shellTexture(int stage, float seconds) {
		int i = Math.max(0, Math.min(STAGES - 1, stage));
		int s = Math.max(24, SHELL_TEXTURE_SIZE);
		if (shellData[i] == null) {
			shellData[i] = new DynamicTexture(s, s, false);
			shellTex[i] = Minecraft.getInstance().getTextureManager()
					.register("ordeal_solar_shell_" + i, shellData[i]);
			shellPainted[i] = 0;
		}
		long now = System.currentTimeMillis();
		if (now - shellPainted[i] >= Math.max(16, SHELL_REPAINT_MS)) {
			shellPainted[i] = now;
			paintShell(shellData[i], seconds, i / (float) (STAGES - 1));
		}
		return shellTex[i];
	}

	private static void paintShell(DynamicTexture tex, float t, float intensity) {
		NativeImage img = tex.getPixels();
		if (img == null) return;
		int s = img.getWidth();
		double inv = 2.0 / s;
		double rot = t * 0.15, ca = Math.cos(rot), sa = Math.sin(rot);

		for (int py = 0; py < s; py++) {
			double ny = (py + 0.5) * inv - 1;
			for (int px = 0; px < s; px++) {
				double nx = (px + 0.5) * inv - 1;
				double r2 = nx * nx + ny * ny;
				if (r2 > 1) { img.setPixelRGBA(px, py, 0); continue; }
				double r = Math.sqrt(r2), z = Math.sqrt(1 - r2);

				double comp = 2.35 / (0.30 + z * 0.95);
				double sx = (nx * ca - ny * sa) * comp, sy = (nx * sa + ny * ca) * comp;
				double w = vn(sx * 0.65 + t * 0.13, sy * 0.65 - t * 0.09) - 0.5;
				double base = fbm3(sx + w * 1.6, sy + w * 1.6 - t * 0.5);
				double rg = 1 - Math.abs(fbm2(sx * 1.3 + t * 0.24, sy * 1.3 - t * 0.72) * 2 - 1);
				double cr = Math.pow(rg, 6);
				double rg2 = 1 - Math.abs(vn(sx * 3.6 - t * 0.4, sy * 3.6 - t * 1.15) * 2 - 1);
				double cr2 = Math.pow(rg2, 8);
				double heat = base * 0.44 + cr * (0.46 + 0.46 * intensity) + cr2 * (0.3 + 0.25 * intensity)
						+ Math.pow(1 - z, 1.7) * 0.6 - 0.07;
				heat = heat < 0 ? 0 : heat > 1 ? 1 : heat;

				int[] col = ramp(heat);
				int a = r > 0.982 ? (int) (255 * (1 - (r - 0.982) / 0.018)) : 255;
				if (a < 0) a = 0;
				img.setPixelRGBA(px, py, (a << 24) | (col[2] << 16) | (col[1] << 8) | col[0]);
			}
		}
		tex.upload();
	}

	private static int[] ramp(double heat) {
		double f = Math.max(0, Math.min(1, heat)) * (RAMP.length - 1);
		int k = Math.min(RAMP.length - 2, (int) f);
		double u = f - k;
		return new int[] {
				(int) (RAMP[k][0] + (RAMP[k + 1][0] - RAMP[k][0]) * u),
				(int) (RAMP[k][1] + (RAMP[k + 1][1] - RAMP[k][1]) * u),
				(int) (RAMP[k][2] + (RAMP[k + 1][2] - RAMP[k][2]) * u) };
	}

	private static final int[] PERM = new int[512];
	static {
		int[] p = new int[256];
		for (int i = 0; i < 256; i++) p[i] = i;
		long seed = 20261;
		for (int i = 255; i > 0; i--) {
			seed = seed * 16807 % 2147483647L;
			int j = (int) (seed / 2147483647.0 * (i + 1));
			if (j > i) j = i;
			int t = p[i]; p[i] = p[j]; p[j] = t;
		}
		for (int i = 0; i < 512; i++) PERM[i] = p[i & 255];
	}

	private static double vn(double x, double y) {
		int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
		double xf = x - xi, yf = y - yi;
		double u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
		int A = PERM[xi & 255], B = PERM[(xi + 1) & 255];
		double a = PERM[(A + yi) & 511] / 255.0, b = PERM[(B + yi) & 511] / 255.0;
		double c = PERM[(A + yi + 1) & 511] / 255.0, d = PERM[(B + yi + 1) & 511] / 255.0;
		return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
	}

	private static double fbm2(double x, double y) {
		return vn(x, y) * 0.667 + vn(x * 2.03, y * 2.03) * 0.333;
	}

	private static double fbm3(double x, double y) {
		return vn(x, y) * 0.572 + vn(x * 2.03, y * 2.03) * 0.286 + vn(x * 4.09, y * 4.09) * 0.142;
	}
}