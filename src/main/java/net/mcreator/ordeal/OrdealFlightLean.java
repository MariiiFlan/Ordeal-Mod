package net.mcreator.ordeal;

import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FLIGHT LEAN — the angles. Nothing here touches a model or a camera;
 * OrdealRootRender takes these and rotates the whole body, OrdealFlightCamera
 * takes them and rolls the view.
 *
 *   W -> lean forward    S -> lean back    A -> bank left    D -> bank right
 *   turning the mouse while boosting -> bank into the turn
 *
 * WHY THIS TICKS INSTEAD OF EASING IN THE RENDER PASS.
 *
 * It used to smooth itself inside hover()/boost(), which OrdealRootRender calls
 * from RenderPlayerEvent.Pre. Two things were wrong with that. The ease rate
 * became framerate-dependent — 240fps banked twelve times faster than 20fps —
 * and in FIRST PERSON your own model is never rendered, so the state never
 * advanced at all and the camera had nothing to roll by.
 *
 * Fisk does it the other way and that is the model copied here: advance on the
 * client TICK (fixed 20Hz, always runs, perspective-independent), keep the
 * previous tick's value, and let whoever draws interpolate between the two by
 * partial tick. That is what makes it smooth rather than steppy.
 *
 * IF A DIRECTION GOES THE WRONG WAY, flip one switch and rebuild:
 *   W/S tilts you sideways   -> SWAP_AXES = true
 *   A/D banks the wrong way  -> INVERT_ROLL = true
 *   W leans backward         -> INVERT_PITCH = true
 */
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class OrdealFlightLean {

	private OrdealFlightLean() {}

	public static boolean ENABLED = true;

	/** Bank angle (degrees) while holding A or D, hovering. */
	public static float BANK  = 24f;
	/** Pitch angle (degrees) while holding W or S, hovering. */
	public static float PITCH = 16f;
	/** Approach rate per tick toward the hover target. Lower = floatier. */
	public static float SMOOTH = 0.18f;

	/**
	 * Boosting is steered with the MOUSE, not WASD, so a WASD bank would sit at
	 * zero the whole flight. How fast you swing the camera becomes how hard you
	 * roll. 0 switches it off and lets the authored clip own the pose.
	 */
	public static float TURN_BANK     = 2.2f;
	/** Cap on the turn bank, degrees. */
	public static float TURN_BANK_MAX = 40f;

	/**
	 * How much of the turn bank survives each tick when you stop turning. This
	 * is Fisk's rollTarget decay — it springs back to level on its own instead
	 * of being held. Higher = the bank hangs around longer.
	 */
	public static float TURN_DECAY  = 0.4f;
	/** Approach rate per tick toward the turn-bank target. */
	public static float TURN_SMOOTH = 0.35f;

	/**
	 * THE SMOOTH LANDING. Vanilla draws the elytra glide only while you are
	 * actually fall-flying, so the frame stopFallFlying() runs the body snaps
	 * from horizontal to upright with nothing in between.
	 *
	 * So we take it over: while gliding we track the angle vanilla is using,
	 * and when the glide ends we keep applying it and decay it to zero over
	 * LAND_TICKS. The body rotates upright instead of cutting.
	 *
	 * LAND_TICKS 0 turns it off. If the body eases the WRONG WAY - dipping
	 * further down before coming up - flip LAND_SIGN.
	 */
	public static float LAND_TICKS = 12f;
	public static float LAND_SIGN  = -1f;

	public static boolean SWAP_AXES    = false;
	public static boolean INVERT_ROLL  = false;
	public static boolean INVERT_PITCH = false;

	public static boolean DEBUG = false;

	private static final class Lean {
		float roll, pitch;          // current, end of last tick
		float prevRoll, prevPitch;  // start of last tick, for partial-tick interpolation
		float turn, prevTurn;       // boost bank from turn rate
		float turnTarget;           // Fisk's rollTarget: fed by mouse, decays
		float lastYaw;
		boolean seeded;
		float glideAngle;           // the elytra pitch vanilla was drawing
		float land, prevLand;       // 1 while gliding, decays to 0 after landing
	}

	private static final Map<UUID, Lean> STATE = new ConcurrentHashMap<>();

	// ---- the tick ----------------------------------------------------------

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) { STATE.clear(); return; }
		if (mc.isPaused()) return;
		for (Player p : mc.level.players()) advance(p);
		STATE.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
	}

	private static void advance(Player p) {
		Lean s = STATE.computeIfAbsent(p.getUUID(), k -> new Lean());
		if (!s.seeded) { s.lastYaw = p.getYRot(); s.seeded = true; }

		s.prevRoll = s.roll;
		s.prevPitch = s.pitch;
		s.prevTurn = s.turn;
		s.prevLand = s.land;

		boolean hovering = false, boosting = false;
		try {
			OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
			hovering = v.flightOn && v.flightIdle && !v.flightBoost;
			boosting = v.flightOn && v.flightBoost;
		} catch (Throwable ignored) {}

		// ---- hover: WASD ----
		float rollTarget = 0f, pitchTarget = 0f;
		if (ENABLED && hovering) {
			boolean fwd, bak, lft, rgt;
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && p.getUUID().equals(mc.player.getUUID())) {
				Options o = mc.options;              // local player: read the keys live
				fwd = o.keyUp.isDown();
				bak = o.keyDown.isDown();
				lft = o.keyLeft.isDown();
				rgt = o.keyRight.isDown();
			} else {                                  // everyone else: synced flags
				fwd = net.mcreator.ordeal.core.OrdealInput.forward(p);
				bak = net.mcreator.ordeal.core.OrdealInput.back(p);
				lft = net.mcreator.ordeal.core.OrdealInput.left(p);
				rgt = net.mcreator.ordeal.core.OrdealInput.right(p);
			}
			if (DEBUG && p.tickCount % 10 == 0)
				System.out.println("[ordeal lean] F=" + fwd + " B=" + bak + " L=" + lft + " R=" + rgt);

			int fb   = (fwd ? 1 : 0) - (bak ? 1 : 0);
			int side = (rgt ? 1 : 0) - (lft ? 1 : 0);
			rollTarget  = side * BANK  * (INVERT_ROLL  ? -1f : 1f);
			pitchTarget = fb   * PITCH * (INVERT_PITCH ? -1f : 1f);
		}
		s.roll  += (rollTarget  - s.roll)  * SMOOTH;
		s.pitch += (pitchTarget - s.pitch) * SMOOTH;

		// ---- boost: bank into the turn (Fisk's decaying rollTarget) ----
		float yaw = p.getYRot();
		float dYaw = Mth.wrapDegrees(yaw - s.lastYaw);
		s.lastYaw = yaw;

		s.turnTarget *= TURN_DECAY;                 // springs back to level on its own
		if (ENABLED && boosting && TURN_BANK != 0f) {
			s.turnTarget += -dYaw * TURN_BANK * (INVERT_ROLL ? -1f : 1f);
			s.turnTarget = Mth.clamp(s.turnTarget, -TURN_BANK_MAX, TURN_BANK_MAX);
		} else {
			s.turnTarget = 0f;
		}
		s.turn += (s.turnTarget - s.turn) * TURN_SMOOTH;

		// ---- landing: hold the glide angle, then ease it out ----
		if (p.isFallFlying()) {
			// vanilla's own formula, from LivingEntityRenderer.setupRotations
			float t = p.getFallFlyingTicks();
			float ramp = Mth.clamp(t * t / 100.0F, 0.0F, 1.0F);
			s.glideAngle = ramp * (-90.0F - p.getXRot());
			s.land = 1f;
		} else if (s.land > 0f) {
			float step = LAND_TICKS <= 0f ? 1f : 1f / LAND_TICKS;
			s.land = Math.max(0f, s.land - step);
			if (p.onGround() && s.land < 0.35f) s.land = Math.max(0f, s.land - step);
		}
	}

	/** The decaying elytra pitch after a glide ends, degrees. 0 when not landing. */
	public static float landPitch(Player p) {
		Lean s = p == null ? null : STATE.get(p.getUUID());
		if (s == null || p.isFallFlying()) return 0f;   // vanilla still owns it mid-glide
		float f = Mth.lerp(pt(), s.prevLand, s.land);
		if (f <= 0.001f) return 0f;
		return s.glideAngle * f * LAND_SIGN;
	}

	// ---- read-outs (partial-tick interpolated; safe to call per frame) ------

	private static float pt() {
		try {
			return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		} catch (Throwable t) {
			return 1f;
		}
	}

	/** Total roll this frame, degrees: hover bank + boost turn bank. */
	public static float roll(Player p) {
		Lean s = p == null ? null : STATE.get(p.getUUID());
		if (s == null) return 0f;
		float f = pt();
		return Mth.lerp(f, s.prevRoll, s.roll) + Mth.lerp(f, s.prevTurn, s.turn);
	}

	/** Body pitch this frame, degrees. Hover only — a boost gets its pitch from the glide. */
	public static float pitch(Player p) {
		Lean s = p == null ? null : STATE.get(p.getUUID());
		if (s == null) return 0f;
		return Mth.lerp(pt(), s.prevPitch, s.pitch);
	}

	/** Just the boost turn-bank part, degrees. */
	public static float turnBank(Player p) {
		Lean s = p == null ? null : STATE.get(p.getUUID());
		if (s == null) return 0f;
		return Mth.lerp(pt(), s.prevTurn, s.turn);
	}

	/**
	 * The lean as a pose for OrdealRootRender, or null when it is doing nothing.
	 * Pure read — the tick above is what advances it.
	 */
	public static OrdealAnimData.Pose pose(Player p) {
		if (p == null) return null;
		float r = ENABLED ? roll(p) : 0f;
		float x = (ENABLED ? pitch(p) : 0f) + landPitch(p);
		if (Math.abs(r) < 0.05f && Math.abs(x) < 0.05f) return null;
		OrdealAnimData.Pose lean = new OrdealAnimData.Pose();
		if (SWAP_AXES) { lean.rx = r; lean.rz = x; }
		else           { lean.rz = r; lean.rx = x; }
		return lean;
	}

	// kept for the debug overlay
	public static float lastRoll(Player p)  { return roll(p); }
	public static float lastPitch(Player p) { return pitch(p); }
	public static float lastTurnBank(Player p) { return turnBank(p); }

	public static void forget(UUID id) { STATE.remove(id); }
}