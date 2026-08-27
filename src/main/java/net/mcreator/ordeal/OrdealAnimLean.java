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

	public static boolean ENABLED = true;
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

		float scale = (player.isSprinting() || !player.onGround() ? 1f : WALK_SCALE) * lvl;
		int fb = (fwd ? 1 : 0) - (bak ? 1 : 0);
		int side = (rgt ? 1 : 0) - (lft ? 1 : 0);

		float rollTarget = side * BANK * scale * (INVERT_ROLL ? -1f : 1f);
		float pitchTarget = fb * PITCH * scale * (INVERT_PITCH ? -1f : 1f);

		float[] s = STATE.computeIfAbsent(player.getUUID(), k -> new float[2]);
		s[0] += (rollTarget - s[0]) * SMOOTH;
		s[1] += (pitchTarget - s[1]) * SMOOTH;
		if (Math.abs(s[0]) < 0.05f && Math.abs(s[1]) < 0.05f) return null;

		OrdealAnimData.Pose lean = new OrdealAnimData.Pose();
		if (SWAP_AXES) { lean.rx = s[0]; lean.rz = s[1]; }
		else { lean.rz = s[0]; lean.rx = s[1]; }
		return lean;
	}

	public static void forget(UUID id) { STATE.remove(id); }
}