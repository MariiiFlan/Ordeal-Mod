package net.mcreator.ordeal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Procedural lean from WASD — the wobble that makes movement read as weight
 * rather than a slide. Hold a direction and the body banks into it; let go and
 * it eases back.
 *
 *   W -> lean forward   S -> lean back   A -> bank left   D -> bank right
 *
 * Where the input comes from matters. Ordeal's key flags are filled in on the
 * server, so on YOUR OWN client they read empty — the local player's keys are
 * read live instead, and the synced flags are used for everyone else.
 *
 * If a direction leans the wrong way, flip the matching switch below.
 */
@OnlyIn(Dist.CLIENT)
public final class OrdealAnimLean {

	private OrdealAnimLean() {}

	/**
	 * OFF by default.
	 *
	 * This is the GROUND wobble - it banks and pitches your body from WASD while
	 * you walk, strafe and jump. It reads as the body swaying on its own for no
	 * reason, which is what it was doing to you. Flight has its own lean
	 * (OrdealFlightLean, whole-body, on the root) and does not use this at all.
	 *
	 * Set true if you ever want the walking sway back.
	 */
	public static boolean ENABLED = false;
	/** Bank angle (degrees) while holding A or D. */
	public static float BANK = 14f;
	/** Pitch angle (degrees) while holding W or S. */
	public static float PITCH = 9f;
	/** Ease toward the target each frame. Lower = floatier. */
	public static float SMOOTH = 0.18f;
	/** How much of the body lean the head cancels out, so the face stays up. */
	public static float HEAD_COUNTER = 0.5f;

	/** Sprinting and airborne lean hard; walking barely at all. */
	public static float WALK_SCALE = 0.35f;

	/**
	 * Flight gets its own, much bigger pair. Walking angles read as a shrug at
	 * flight speed - you are banking a body through the air, not shifting weight
	 * on your feet. These REPLACE bank/pitch while flying rather than scaling
	 * them, so the two can be tuned without dragging each other around.
	 */
	public static float FLIGHT_BANK  = 34f;
	public static float FLIGHT_PITCH = 20f;
	/** Flight lean eases slower, so a bank sweeps instead of snapping. */
	public static float FLIGHT_SMOOTH = 0.11f;

	/** Off / Low / Med / High, matching the noise dial. */
	private static final String[] NAMES = { "Off", "Low", "Med", "High" };
	private static final float[] STRENGTH = { 0f, 0.6f, 1.0f, 1.6f };

	public static String name(int level) { return NAMES[Math.floorMod(level, NAMES.length)]; }

	public static int next(int level) { return Math.floorMod(level + 1, NAMES.length); }

	/** Level used when no clip is playing. */
	public static int DEFAULT_LEVEL = 1;

	public static boolean SWAP_AXES = false;
	public static boolean INVERT_ROLL = false;
	public static boolean INVERT_PITCH = false;

	private static final Map<UUID, float[]> STATE = new ConcurrentHashMap<>(); // {roll, pitch}

	public static OrdealAnimData.Pose compute(Player player) {
		return compute(player, DEFAULT_LEVEL);
	}

	/**
	 * Body lean for this player right now, or null when it is doing nothing.
	 * The level comes from the playing clip, so a clip can dial its own wobble
	 * up for a heavy swing or off entirely for something rigid.
	 */
	public static OrdealAnimData.Pose compute(Player player, int level) {
		if (!ENABLED || player == null) return null;
		if (level < 0) level = DEFAULT_LEVEL;
		if (level >= STRENGTH.length) level = STRENGTH.length - 1;
		float lvl = STRENGTH[level];
		if (lvl <= 0f) { STATE.remove(player.getUUID()); return null; }

		boolean fwd, bak, lft, rgt;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && player.getUUID().equals(mc.player.getUUID())) {
			Options o = mc.options;
			fwd = o.keyUp.isDown();
			bak = o.keyDown.isDown();
			lft = o.keyLeft.isDown();
			rgt = o.keyRight.isDown();
		} else {
			fwd = net.mcreator.ordeal.core.OrdealInput.forward(player);
			bak = net.mcreator.ordeal.core.OrdealInput.back(player);
			lft = net.mcreator.ordeal.core.OrdealInput.left(player);
			rgt = net.mcreator.ordeal.core.OrdealInput.right(player);
		}

		// flightOn/Idle/Boost are synced variables, so this reads true on every
		// client that can see the player, not just the one flying
		boolean flying = net.mcreator.ordeal.Flight.flying(player);

		float scale = (player.isSprinting() || !player.onGround() ? 1f : WALK_SCALE) * lvl;
		int fb = (fwd ? 1 : 0) - (bak ? 1 : 0);
		int side = (rgt ? 1 : 0) - (lft ? 1 : 0);

		float bank = flying ? FLIGHT_BANK : BANK;
		float pitch = flying ? FLIGHT_PITCH : PITCH;
		float ease = flying ? FLIGHT_SMOOTH : SMOOTH;

		float rollTarget = side * bank * scale * (INVERT_ROLL ? -1f : 1f);
		float pitchTarget = fb * pitch * scale * (INVERT_PITCH ? -1f : 1f);

		float[] s = STATE.computeIfAbsent(player.getUUID(), k -> new float[2]);
		s[0] += (rollTarget - s[0]) * ease;
		s[1] += (pitchTarget - s[1]) * ease;
		if (Math.abs(s[0]) < 0.05f && Math.abs(s[1]) < 0.05f) return null;

		OrdealAnimData.Pose lean = new OrdealAnimData.Pose();
		if (SWAP_AXES) { lean.rx = s[0]; lean.rz = s[1]; }
		else { lean.rz = s[0]; lean.rx = s[1]; }
		return lean;
	}

	public static void forget(UUID id) { STATE.remove(id); }

	/**
	 * The same lean, driven by a scripted input instead of the keyboard.
	 *
	 * In the animator there is nobody walking - WASD drives the orbit camera -
	 * so the real compute() always returns null and the Wobble dial looks dead.
	 * This runs the identical maths off a slow figure-of-eight "input", so the
	 * dummy banks and pitches exactly as far as the chosen level will bank and
	 * pitch it in game. It is a demo of the setting, not a second system: change
	 * BANK or PITCH and both move together.
	 */
	public static OrdealAnimData.Pose preview(int level) {
		if (!ENABLED) return null;
		if (level < 0) level = DEFAULT_LEVEL;
		if (level >= STRENGTH.length) level = STRENGTH.length - 1;
		float lvl = STRENGTH[level];
		if (lvl <= 0f) return null;

		double t = System.nanoTime() * 1.0e-9;
		float side = (float) Math.sin(t * 0.9);
		float fb = (float) Math.sin(t * 0.55 + 1.3);

		float roll = side * BANK * lvl * (INVERT_ROLL ? -1f : 1f);
		float pitch = fb * PITCH * lvl * (INVERT_PITCH ? -1f : 1f);

		OrdealAnimData.Pose lean = new OrdealAnimData.Pose();
		if (SWAP_AXES) { lean.rx = roll; lean.rz = pitch; }
		else { lean.rz = roll; lean.rx = pitch; }
		return lean;
	}
}