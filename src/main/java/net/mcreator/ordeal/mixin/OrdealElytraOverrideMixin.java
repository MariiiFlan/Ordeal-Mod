package net.mcreator.ordeal.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

import net.mcreator.ordeal.OrdealAnimData;
import net.mcreator.ordeal.OrdealAnimPlayback;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Invincible's TpElytraOverrideMixin, ported.
 *
 * In 1.21.1 the elytra's nose-down glide rotation lives in
 * PlayerRenderer.setupRotations, not in the base LivingEntityRenderer. It runs
 * AFTER OrdealRootRender has already tilted the body, so with an authored
 * flight pose the two fight and vanilla wins.
 *
 * While a clip is playing that actually authored a "root" rotation AND the
 * player is fall-flying, this skips that whole method and applies only the
 * normal body-facing yaw. Your authored tilt then defines the pose completely.
 *
 * The threshold is what keeps it honest: if the clip's root rotation is ~0
 * (nothing authored, no style equipped), the override switches OFF and you get
 * the real vanilla elytra glide, with OrdealFlightLean's turn bank on top. So
 * there is always a lean, authored or not.
 *
 * RENDER ONLY. The actual fall-flying state, physics and movement are untouched.
 * CLIENT mixin - goes in the "client" array.
 */
@Mixin(PlayerRenderer.class)
public class OrdealElytraOverrideMixin {

	/** Min authored root rotation (degrees, any axis) before the override kicks in. */
	private static final float ROT_THRESHOLD = 0.05f;

	@Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
	private void ordeal$bypassElytraGlide(AbstractClientPlayer player, PoseStack poseStack,
			float bob, float yBodyRot, float partialTick, float scale, CallbackInfo ci) {
		if (!player.isFallFlying()) return;
		if (OrdealAnimPlayback.weight(player) <= 0.01f) return;

		OrdealAnimData.Pose root = OrdealAnimPlayback.pose(player, "root");
		if (root == null) return;

		boolean authored = Math.abs(root.rx) > ROT_THRESHOLD
				|| Math.abs(root.ry) > ROT_THRESHOLD
				|| Math.abs(root.rz) > ROT_THRESHOLD;
		if (!authored) return; // nothing authored -> let the real elytra glide happen

		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yBodyRot));
		ci.cancel();
	}
}