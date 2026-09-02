package net.mcreator.ordeal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * THE LEAN. Invincible's TpRootRender, ported.
 *
 * WHY THIS FILE EXISTS AT ALL. The vanilla player model has no bone that
 * parents head + torso + arms + legs. So a full-body tilt CANNOT be done by
 * setting ModelPart rotations - rotate m.body and you bend at the waist while
 * your head stays level and your legs stay planted. That is exactly what you
 * were looking at: limbs moving, nothing leaning.
 *
 * A body lean has to happen on the RENDER TRANSFORM, before the model draws.
 * That is what this does: rotate the whole rig around the waist, in
 * body-relative space (turn into the body's facing, tilt, turn back), so a
 * forward pitch lays you horizontal no matter which way you are pointed.
 *
 * WHAT IT ROTATES BY, added together:
 *   - the "root" channel of whatever clip is playing (author it in the 3rd
 *     person editor; the bone is already in OrdealAnimData.BONES)
 *   - OrdealFlightLean while hovering  (WASD bank)
 *   - OrdealFlightLean while boosting  (bank into the turn)
 *   - living-motion noise, so a held pose breathes
 *
 * ONE DELIBERATE DIFFERENCE FROM INVINCIBLE. TpRootRender gives up immediately
 * when no clip is playing, so over there the hover lean only exists if you have
 * a style authored. You asked for the lean to work with no FlightStyle
 * equipped, so this runs the lean on its own when there is no clip.
 *
 * Priority HIGHEST so the tilt is on the poseStack BEFORE anything else drawing
 * in this event - attached models inherit the lean instead of being baked
 * upright first. RenderPlayerEvent.Pre fires before the renderer pushes its own
 * transforms, and the dispatcher's push/pop cleans up after, so no Post handler.
 */
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class OrdealRootRender {

	private OrdealRootRender() {}

	/** Height above the feet (blocks) the whole body pivots around. ~0.9 = waist. */
	public static float PIVOT_Y = 0.9f;

	/** Hover bob: amplitude in blocks, speed in radians per tick. 0 amp = off. */
	public static float BOB_AMP   = 0.12f;
	public static float BOB_SPEED = 0.10f;

	/** Translations in the clip are model pixels; the render transform is blocks. */
	private static final float PX = 1f / 16f;

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
		Player player = event.getEntity();

		boolean hovering = false, boosting = false;
		try {
			OrdealModVariables.PlayerVariables v = player.getData(OrdealModVariables.PLAYER_VARIABLES);
			hovering = v.flightOn && v.flightIdle && !v.flightBoost;
			boosting = v.flightOn && v.flightBoost;
		} catch (Throwable ignored) {}

		// A Player Animator clip owns the WHOLE body while it plays - an ability
		// swing has its own torso angle, and a flight root tilt stacked under it
		// is what made abilities look wrong mid-flight. Stand down completely:
		// no root, no lean, no landing ease. OrdealAnimPlayback already yields
		// the limbs; this is the other half of the same handover.
		if (OrdealAnimPlayback.overriddenByPlugin(player)) return;

		OrdealAnimData.Pose root = OrdealAnimPlayback.pose(player, "root");
		float w = OrdealAnimPlayback.weight(player);

		// The lean is its own layer and runs at full weight whether or not a clip
		// is playing - that is the "works with no FlightStyle equipped" part.
		// OrdealFlightLean advances on the client tick now and this is a pure
		// read, interpolated by partial tick — so the body and the camera move
		// off the exact same number, at the same rate, at any framerate.
		// Always ask - pose() folds in the hover bank, the boost turn bank AND the
		// landing ease-out, and that last one has to keep running after flight is
		// already over or the body snaps upright the frame the glide ends.
		OrdealAnimData.Pose lean = OrdealFlightLean.pose(player);

		if (root == null && lean == null) return;

		float rx = 0f, ry = 0f, rz = 0f, tx = 0f, ty = 0f, tz = 0f;

		if (root != null && w > 0f) {
			// living-motion noise on the root, seed 0 - the limbs get theirs in
			// OrdealAnimRender, the root is drawn here so it gets it here
			OrdealAnimData.Pose n = new OrdealAnimData.Pose();
			OrdealAnimNoise.apply(n, 0, OrdealAnimPlayback.noiseLevel(player));
			rx += (root.rx + n.rx) * w;
			ry += (root.ry + n.ry) * w;
			rz += (root.rz + n.rz) * w;
			tx += (root.x + n.x) * w * PX;
			ty += (root.y + n.y) * w * PX;
			tz += (root.z + n.z) * w * PX;
		}
		if (lean != null) {
			rx += lean.rx;
			ry += lean.ry;
			rz += lean.rz;
		}

		float bobY = 0f;
		if (hovering && BOB_AMP != 0f) {
			float t = (player.tickCount + event.getPartialTick()) * BOB_SPEED;
			bobY = BOB_AMP * Mth.sin(t);
		}

		if (Math.abs(rx) < 0.05f && Math.abs(ry) < 0.05f && Math.abs(rz) < 0.05f
				&& tx == 0f && ty == 0f && tz == 0f && Math.abs(bobY) < 0.0005f) return;

		PoseStack pose = event.getPoseStack();
		float bodyYaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);

		if (bobY != 0f) pose.translate(0, bobY, 0);        // hover float (world vertical)
		pose.translate(0, PIVOT_Y, 0);
		pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));   // into body-relative space
		pose.translate(tx, ty, tz);                        // body-relative offset
		pose.mulPose(Axis.ZP.rotationDegrees(rz));         // roll  (+ = bank right)
		pose.mulPose(Axis.YP.rotationDegrees(ry));         // spin
		pose.mulPose(Axis.XP.rotationDegrees(rx));         // pitch (+ = lean forward)
		pose.mulPose(Axis.YP.rotationDegrees(bodyYaw));    // back to world orientation
		pose.translate(0, -PIVOT_Y, 0);
	}
}