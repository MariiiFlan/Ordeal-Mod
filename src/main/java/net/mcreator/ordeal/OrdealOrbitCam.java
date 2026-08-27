package net.mcreator.ordeal;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import org.lwjgl.glfw.GLFW;

/**
 * Blockbench-style orbit camera for the animator.
 *
 * The dummy never moves — it stays exactly where the editor put it. Only the
 * camera travels: it orbits a target point, and the player it detached from
 * stands still the whole time.
 *
 * drag = orbit · right-drag = free look · middle-drag = pan
 * scroll = zoom · WASD/QE = fly · C = re-centre on the model
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class OrdealOrbitCam {

	private OrdealOrbitCam() {}

	public static boolean active = false;
	public static Vec3 target = Vec3.ZERO;
	public static float yaw, pitch;
	public static float dist = 2.8f;

	/** Fraction of the screen the shot lifts by so the panels don't cover the model. */
	public static float frameShift = 0f;

	private static final float DEFAULT_DIST = 2.8f;
	/** Roughly half a player's height — the point the camera aims at. */
	private static final double MODEL_MID = 0.95;
	/** Where a player's eyes sit above their feet. */
	private static final double EYE = 1.62;

	private static CameraType prevView = null;
	private static long keyNanos = 0;
	private static boolean blocked = false;

	/**
	 * The camera rides this. Writing Camera.position during the viewport event
	 * does not survive to the render, so instead the game is handed an entity
	 * to look through and that entity is moved — the same trick every freecam
	 * mod uses. It is never added to the level, so it never renders or ticks.
	 */
	private static net.minecraft.world.entity.decoration.ArmorStand rig;

	/**
	 * First person is not an orbit view. The camera stays in your own head,
	 * where the game puts it, and OrdealFirstPerson draws the arms through the
	 * real hand renderer - so the preview is the same view you fight in.
	 * The rig is released while this is on.
	 */
	private static boolean fp() {
		return OrdealAnimatorClient.firstPerson;
	}

	private static void releaseRig() {
		Minecraft mc = Minecraft.getInstance();
		if (rig != null) {
			if (mc.player != null) mc.setCameraEntity(mc.player);
			rig = null;
		}
	}

	// ---- lifecycle ----------------------------------------------------------

	public static void begin() {
		Minecraft mc = Minecraft.getInstance();
		keyNanos = System.nanoTime();
		blocked = false;
		if (prevView == null) {
			prevView = mc.options.getCameraType();
			mc.options.setCameraType(CameraType.FIRST_PERSON); // no body, no shadow, no zoom collision
		}
		active = true;
		center();
		Minecraft m = Minecraft.getInstance();
		if (rig != null && m.player != null) m.setCameraEntity(rig);
	}

	public static void end() {
		active = false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.setCameraEntity(mc.player);
		rig = null;
		if (prevView != null) {
			mc.options.setCameraType(prevView);
			prevView = null;
		}
	}

	/** Move the eye. Called straight from every control, so there is no lag. */
	private static void place() {
		Minecraft mc = Minecraft.getInstance();
		if (!active || mc.level == null) return;
		if (fp()) { releaseRig(); return; }
		if (rig == null) {
			try {
				rig = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, 0, 0, 0);
				rig.setInvisible(true);
				rig.setNoGravity(true);
				rig.setSilent(true);
				rig.noPhysics = true;
			} catch (Throwable t) {
				rig = null;
				return;
			}
			if (mc.player != null) mc.setCameraEntity(rig);
		}
		Vec3 p = camPos();
		rig.setPos(p.x, p.y - rig.getEyeHeight(), p.z);
		rig.setYRot(yaw);
		rig.setXRot(pitch);
		rig.yHeadRot = yaw;
		rig.setOldPosAndRot();
	}

	/**
	 * C key / toolbar: snap to the front view, the way Blockbench opens a model —
	 * face on, level, centred, at a distance that frames the whole body.
	 *
	 * The dummy renders spun by modelYaw and faces away at modelYaw 0, so the
	 * camera has to stand on the far side to be looking at its face.
	 */
	public static void center() {
		if (fp()) {
			// nothing to centre - the view is your own eyes. Level the head so
			// the arms sit where they will sit in a fight.
			releaseRig();
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) mc.player.setXRot(0);
			return;
		}
		target = OrdealAnimatorClient.dummyPos.add(0, MODEL_MID, 0);
		dist = DEFAULT_DIST;
		yaw = -OrdealAnimatorClient.modelYaw;
		pitch = 0f;
		place();
	}

	// ---- controls (called by OrdealAnimatorScreen) --------------------------

	/** Left-drag: swing the camera around the model. The model does not move. */
	public static void drag(double dx, double dy) {
		if (fp()) { turnSelf(dx, dy); return; }
		yaw += (float) dx * 0.5f;
		pitch = clamp(pitch + (float) dy * 0.5f);
		place();
	}

	/** First person: dragging looks around, because there is nothing to orbit. */
	private static void turnSelf(double dx, double dy) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		mc.player.setYRot(mc.player.getYRot() + (float) dx * 0.4f);
		mc.player.setXRot(clamp(mc.player.getXRot() + (float) dy * 0.4f));
		mc.player.yHeadRot = mc.player.getYRot();
	}

	/** Right-drag: turn the camera in place, carrying the orbit point with it. */
	public static void look(double dx, double dy) {
		if (fp()) { turnSelf(dx, dy); return; }
		Vec3 pos = camPos();
		yaw += (float) dx * 0.3f;
		pitch = clamp(pitch + (float) dy * 0.3f);
		target = pos.add(dir().scale(dist));
		place();
	}

	public static void pan(double dx, double dy) {
		if (fp()) return;
		double s = dist * 0.0032;
		target = target.subtract(right().scale(dx * s)).add(up().scale(dy * s));
		place();
	}

	public static void zoom(double sy) {
		if (fp()) return;
		dist = (float) Math.max(1.0, Math.min(40, dist * Math.pow(0.88, sy)));
		place();
	}

	/** WASD flies the camera, Q/E sinks and rises. Polled so held keys repeat. */
	public static void pollKeys() {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - keyNanos) / 1_000_000_000f);
		keyNanos = now;
		if (!active || fp()) return;
		long win = Minecraft.getInstance().getWindow().getWindow();
		if (key(win, GLFW.GLFW_KEY_LEFT_CONTROL) || key(win, GLFW.GLFW_KEY_RIGHT_CONTROL)) return;
		float fwd = (key(win, GLFW.GLFW_KEY_W) ? 1 : 0) - (key(win, GLFW.GLFW_KEY_S) ? 1 : 0);
		float strafe = (key(win, GLFW.GLFW_KEY_D) ? 1 : 0) - (key(win, GLFW.GLFW_KEY_A) ? 1 : 0);
		float rise = (key(win, GLFW.GLFW_KEY_E) ? 1 : 0) - (key(win, GLFW.GLFW_KEY_Q) ? 1 : 0);
		if (fwd == 0 && strafe == 0 && rise == 0) return;
		Vec3 f = dir(), r = right();
		float sp = Math.max(2f, dist) * 0.9f * dt;
		target = target.add(f.scale(fwd * sp)).add(r.scale(strafe * sp)).add(0, rise * sp, 0);
		place();
	}

	private static boolean key(long win, int k) {
		return GLFW.glfwGetKey(win, k) == GLFW.GLFW_PRESS;
	}

	private static float clamp(float p) {
		return Math.max(-89.5f, Math.min(89.5f, p));
	}

	// ---- view basis ---------------------------------------------------------

	private static Vec3 dir() {
		double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
		return new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
	}

	private static Vec3 right() {
		Vec3 f = dir();
		Vec3 r = new Vec3(-f.z, 0, f.x);
		return r.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : r.normalize();
	}

	private static Vec3 up() {
		return right().cross(dir());
	}

	public static Vec3 camPos() {
		// straight down in world space, so lifting the shot never slides it sideways
		// barely lift the first-person shot, or the eye ends up in the chest
		float lift = dist * 1.4f * frameShift * (OrdealAnimatorClient.firstPerson ? 0.3f : 1f);
		return target.subtract(dir().scale(dist)).subtract(0, lift, 0);
	}

	public static String status() {
		return blocked ? "CAMERA IS STUCK ON THE PLAYER - tell Claude the rig failed" : null;
	}

	// ---- hooks --------------------------------------------------------------

	private static boolean editing() {
		return active && Minecraft.getInstance().screen instanceof OrdealAnimatorScreen;
	}

	@SubscribeEvent
	public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
		if (!editing()) return;
		// first person: the game's own camera, plus whatever shove
		// OrdealFirstPerson adds from the head and root channels
		if (fp()) { releaseRig(); blocked = false; return; }
		e.setYaw(yaw);
		e.setPitch(pitch);
		e.setRoll(0);
		Vec3 pos = camPos();
		place();
		apply(e.getCamera(), pos);
		blocked = e.getCamera().getPosition().distanceToSqr(pos) > 0.5;
	}

	/**
	 * Third person would draw your held item across the viewport for no reason.
	 * First person is the whole point of the mode, so the hands stay.
	 */
	@SubscribeEvent
	public static void onHand(RenderHandEvent e) {
		if (editing() && !fp()) e.setCanceled(true);
	}

	/** Belt and braces if the view ever ends up third person. */
	@SubscribeEvent
	public static void onRenderPlayer(RenderPlayerEvent.Pre e) {
		if (editing() && e.getEntity() == Minecraft.getInstance().player)
			e.setCanceled(true);
	}

	// Camera's position setter is protected, so it is driven by reflection —
	// method first, then the raw fields, and both are written every frame so a
	// silent failure in one still leaves the camera where it belongs.
	private static java.lang.reflect.Method mSet;
	private static boolean mXyz;
	private static java.lang.reflect.Field fPos, fBlock;
	private static boolean resolved = false;

	private static void resolve() {
		resolved = true;
		try {
			for (var m : net.minecraft.client.Camera.class.getDeclaredMethods()) {
				if (!m.getName().equals("setPosition")) continue;
				if (m.getParameterCount() == 3) { mSet = m; mXyz = true; break; }
				if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Vec3.class && mSet == null) {
					mSet = m;
					mXyz = false;
				}
			}
			if (mSet != null) mSet.setAccessible(true);
		} catch (Throwable ignored) {
			mSet = null;
		}
		try {
			for (var f : net.minecraft.client.Camera.class.getDeclaredFields()) {
				if (f.getType() == Vec3.class && fPos == null) fPos = f;
				if (f.getType() == net.minecraft.core.BlockPos.MutableBlockPos.class && fBlock == null) fBlock = f;
			}
			if (fPos != null) fPos.setAccessible(true);
			if (fBlock != null) fBlock.setAccessible(true);
		} catch (Throwable ignored) {}
	}

	private static void apply(net.minecraft.client.Camera cam, Vec3 pos) {
		if (!resolved) resolve();
		try {
			if (mSet != null) {
				if (mXyz) mSet.invoke(cam, pos.x, pos.y, pos.z);
				else mSet.invoke(cam, pos);
			}
		} catch (Throwable ignored) {
			mSet = null;
		}
		try {
			if (fPos != null) fPos.set(cam, pos);
			if (fBlock != null)
				((net.minecraft.core.BlockPos.MutableBlockPos) fBlock.get(cam)).set(pos.x, pos.y, pos.z);
		} catch (Throwable ignored) {}
	}
}