package net.mcreator.ordeal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * First-person arms and camera — a straight port of Invincible's FpAnimRenderer,
 * FpNoise and FpCameraHook.
 *
 * The important thing about that system is how little it does. There is no base
 * pose, no scaling, no axis flipping. A pose is translated in BLOCKS, rotated
 * X then Y then Z, and handed to the game's own hand renderer. Whatever numbers
 * are in the clip are the numbers that get used.
 *
 * That means a clip of all zeros puts the arm at the camera, which looks wrong -
 * and it is meant to. You never author from zero. You start from an idle clip
 * (fs_standard_idle) whose values put the hands where they belong, and animate
 * away from that. Trying to bake those values into a hidden "rest" underneath
 * is what kept breaking this.
 *
 * Clips with no fp_* tracks still fall back to driving the hands from the body
 * arms, so older animations keep working.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class OrdealFirstPerson {

	private OrdealFirstPerson() {}

	public static boolean ENABLED = true;

	/**
	 * What a fresh first-person keyframe looks like.
	 *
	 * These are the numbers Invincible's editor puts in a new frame - TX 0.25,
	 * TY -0.45, TZ -0.43, pitch -46, roll 12 - which is why its arms are up and
	 * readable the moment you open it. Zeros put the arm at the camera, so a
	 * new frame is never zeros there and is not here either.
	 *
	 * tx, ty, tz then pitch, yaw, roll. tx and roll mirror for the left hand.
	 */
	public static float[] OPEN_POS = { 0.97f, -0.45f, -0.43f };
	public static float[] OPEN_ROT = { -38.6f, 0f, 12f };

	/** Global multiplier for the camera move. 0 = off, 1 = full. */
	public static float INTENSITY = 1.0f;
	/**
	 * Ceiling on the camera kick, in degrees. Only used by the OLD path, where
	 * head and root shove the view on a clip with no fp_cam track - an fp_cam
	 * channel is applied straight, exactly as Invincible does it.
	 */
	public static float CAM_CLAMP = 25f;

	// ---- fallback for clips with no first-person tracks ---------------------
	// These only matter when a clip has body arms but no fp_* channels: the
	// third-person swing is model space and far too big, so it gets converted.

	public static float ARM_STRENGTH = 0.45f;
	public static float ARM_SHIFT = 0.35f;
	public static float CAM_STRENGTH = 0.45f;
	public static float[] PIVOT = { 0.0f, 0.15f, -0.25f };
	public static float[] ROT_SIGN = { -1f, -1f, 1f };
	public static float[] POS_SIGN = { -1f, -1f, 1f };

	private static boolean previewing() {
		return OrdealAnimatorClient.active && OrdealAnimatorClient.firstPerson
				&& Minecraft.getInstance().screen instanceof OrdealAnimatorScreen;
	}

	// ---- arms ---------------------------------------------------------------

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void onRenderHand(RenderHandEvent event) {
		if (!ENABLED) return;
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.player instanceof AbstractClientPlayer player)) return;

		boolean preview = previewing();
		if (event.isCanceled() && !preview) return;
		if (!preview && !OrdealFpPlayback.isActive() && !OrdealAnimPlayback.isAnimating(player))
			return;

		boolean main = event.getHand() == InteractionHand.MAIN_HAND;
		HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
		boolean isRight = arm == HumanoidArm.RIGHT;

		// one ease per frame, driven off the main hand like Invincible does
		if (main && !preview) OrdealFpPlayback.advanceVisual(OrdealFpPlayback.EASE);

		String fpChannel = isRight ? "fp_right" : "fp_left";
		OrdealAnimData.Pose fp;
		if (preview) {
			fp = OrdealAnimatorClient.poseFor(fpChannel);
		} else if (OrdealFpPlayback.isActive()) {
			// the eased, cross-faded pose - never the raw clip sample
			fp = isRight ? OrdealFpPlayback.dispR : OrdealFpPlayback.dispL;
		} else {
			fp = null;
		}

		PoseStack pose = event.getPoseStack();

		if (fp != null) {
			OrdealAnimData.Pose p = copy(fp);
			noise(p, isRight, preview ? clipNoise() : OrdealFpPlayback.activeNoise);

			pose.pushPose();
			pose.translate(p.x, p.y, p.z);
			pose.mulPose(Axis.XP.rotationDegrees(p.rx));
			pose.mulPose(Axis.YP.rotationDegrees(p.ry));
			pose.mulPose(Axis.ZP.rotationDegrees(p.rz));
			hand(pose, event.getMultiBufferSource(), event.getPackedLight(), player, isRight);
			pose.popPose();

			event.setCanceled(true);
			return;
		}

		// no first-person tracks on this clip - drive the hands off the body
		String bone = isRight ? "right_arm" : "left_arm";
		OrdealAnimData.Pose src = preview ? OrdealAnimatorClient.poseFor(bone)
				: OrdealAnimPlayback.pose(player, bone);
		if (src == null) return;

		OrdealAnimData.Pose p = copy(src);
		float w = (preview ? 1f : OrdealAnimPlayback.weight(player)) * ARM_STRENGTH;
		float side = isRight ? 1f : -1f;

		pose.pushPose();
		pose.translate(p.x / 16f * ARM_SHIFT * POS_SIGN[0],
				p.y / 16f * ARM_SHIFT * POS_SIGN[1],
				p.z / 16f * ARM_SHIFT * POS_SIGN[2]);
		pose.translate(PIVOT[0] * side, PIVOT[1], PIVOT[2]);
		pose.mulPose(Axis.XP.rotationDegrees(p.rx * w * ROT_SIGN[0]));
		pose.mulPose(Axis.YP.rotationDegrees(p.ry * w * ROT_SIGN[1]));
		pose.mulPose(Axis.ZP.rotationDegrees(p.rz * w * ROT_SIGN[2]));
		pose.translate(-PIVOT[0] * side, -PIVOT[1], -PIVOT[2]);
		hand(pose, event.getMultiBufferSource(), event.getPackedLight(), player, isRight);
		pose.popPose();

		event.setCanceled(true);
	}

	/** Straight to the game's hand renderer - the pose already placed the arm. */
	private static void hand(PoseStack pose, MultiBufferSource buffers, int light,
			AbstractClientPlayer player, boolean isRight) {
		Minecraft mc = Minecraft.getInstance();
		PlayerRenderer pr = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
		if (isRight) pr.renderRightHand(pose, buffers, light, player);
		else pr.renderLeftHand(pose, buffers, light, player);
	}

	// ---- camera -------------------------------------------------------------

	@SubscribeEvent
	public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
		if (!ENABLED || INTENSITY <= 0f) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		boolean preview = previewing();
		if (!preview && mc.screen instanceof OrdealAnimatorScreen) return;
		if (!preview && !OrdealFpPlayback.isActive() && !OrdealAnimPlayback.isAnimating(mc.player))
			return;

		OrdealAnimData.Pose cam = preview ? OrdealAnimatorClient.poseFor("fp_cam")
				: (OrdealFpPlayback.isActive() ? OrdealFpPlayback.dispCam : null);

		if (cam != null) {
			// Invincible's FpCameraHook adds the offset straight, with no ceiling -
			// the clip is trusted. INTENSITY is the only dial.
			float w = INTENSITY;
			event.setPitch(event.getPitch() + cam.rx * w);
			event.setYaw(event.getYaw() + cam.ry * w);
			event.setRoll(event.getRoll() + cam.rz * w);
			return;
		}

		// older clips: head and root shove the view instead
		if (preview) return;
		OrdealAnimData.Pose head = OrdealAnimPlayback.pose(mc.player, "head");
		OrdealAnimData.Pose root = OrdealAnimPlayback.pose(mc.player, "root");
		if (head == null && root == null) return;
		float w = OrdealAnimPlayback.weight(mc.player) * CAM_STRENGTH;
		event.setPitch(event.getPitch()
				+ clamp(((head == null ? 0 : head.rx) + (root == null ? 0 : root.rx)) * w));
		event.setYaw(event.getYaw()
				+ clamp(((head == null ? 0 : head.ry) + (root == null ? 0 : root.ry)) * w));
		event.setRoll(event.getRoll()
				+ clamp(((head == null ? 0 : head.rz) + (root == null ? 0 : root.rz)) * w));
	}

	// ---- living motion ------------------------------------------------------
	// Invincible's FpNoise, values and all.

	private static final float[] STRENGTH = { 0f, 0.5f, 1.0f, 1.8f };
	private static final float T_AMP = 0.006f;   // blocks
	private static final float R_AMP = 0.9f;     // degrees

	private static float wave(double t, float freq, float phase) {
		return (float) Math.sin(t * freq + phase);
	}

	private static float wobbleAt(double t, int seed) {
		float p = seed * 1.7f;
		return wave(t, 1.3f, p) * 0.60f
				+ wave(t, 2.7f, p * 1.9f + 1.1f) * 0.30f
				+ wave(t, 5.1f, p * 0.7f + 2.3f) * 0.10f;
	}

	private static void noise(OrdealAnimData.Pose p, boolean rightArm, int level) {
		if (level <= 0) return;
		if (level >= STRENGTH.length) level = STRENGTH.length - 1;
		double t = System.nanoTime() * 1.0e-9;
		float s = STRENGTH[level];
		int b = rightArm ? 0 : 50;
		p.x += wobbleAt(t, b) * T_AMP * s;
		p.y += wobbleAt(t, b + 1) * T_AMP * s;
		p.z += wobbleAt(t, b + 2) * T_AMP * s;
		p.rx += wobbleAt(t, b + 3) * R_AMP * s;
		p.ry += wobbleAt(t, b + 4) * R_AMP * s;
		p.rz += wobbleAt(t, b + 5) * R_AMP * s;
	}

	private static int clipNoise() {
		return OrdealAnimatorClient.data == null ? 0 : OrdealAnimatorClient.data.noise;
	}

	private static float clamp(float v) {
		return Math.max(-CAM_CLAMP, Math.min(CAM_CLAMP, v));
	}

	private static OrdealAnimData.Pose copy(OrdealAnimData.Pose s) {
		OrdealAnimData.Pose p = new OrdealAnimData.Pose();
		p.rx = s.rx; p.ry = s.ry; p.rz = s.rz;
		p.x = s.x; p.y = s.y; p.z = s.z;
		return p;
	}
}