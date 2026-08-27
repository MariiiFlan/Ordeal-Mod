package net.mcreator.ordeal;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Applies playback poses to player models. Rotations are added on top of
 * vanilla's animation and scaled by the blend weight, so a clip fading in
 * eases over the walk cycle instead of snapping.
 *
 * Two procedural layers ride on top of the keyframes: living-motion noise, so
 * a held pose breathes instead of freezing, and a WASD lean, so moving reads
 * as weight. Both are additive and neither writes to the clip.
 */
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class OrdealAnimRender {

	private OrdealAnimRender() {}

	private static final float DEG = (float) (Math.PI / 180.0);

	@SubscribeEvent
	public static void onPre(RenderPlayerEvent.Pre event) {
		Player p = event.getEntity();
		boolean clip = OrdealAnimPlayback.isAnimating(p);
		OrdealAnimData.Pose lean = OrdealAnimLean.compute(p, OrdealAnimPlayback.wobbleLevel(p));
		if (!clip && lean == null) return;

		PlayerModel<?> m = event.getRenderer().getModel();
		float w = OrdealAnimPlayback.weight(p);
		int noise = OrdealAnimPlayback.noiseLevel(p);

		if (clip) {
			OrdealAnimData.Pose head = swayed(p, "head", noise);
			apply(m.head, head, w);
			apply(m.hat, head, w);
			apply(m.body, swayed(p, "body", noise), w);
			apply(m.rightArm, swayed(p, "right_arm", noise), w);
			apply(m.leftArm, swayed(p, "left_arm", noise), w);
			apply(m.rightLeg, swayed(p, "right_leg", noise), w);
			apply(m.leftLeg, swayed(p, "left_leg", noise), w);
		}

		// lean is its own layer - it runs whether or not a clip is playing
		if (lean != null) {
			apply(m.body, lean, 1f);
			OrdealAnimData.Pose counter = new OrdealAnimData.Pose();
			counter.rx = -lean.rx * OrdealAnimLean.HEAD_COUNTER;
			counter.rz = -lean.rz * OrdealAnimLean.HEAD_COUNTER;
			apply(m.head, counter, 1f);
			apply(m.hat, counter, 1f);
		}

		// sleeves and trousers follow their limbs
		copy(m.rightSleeve, m.rightArm);
		copy(m.leftSleeve, m.leftArm);
		copy(m.rightPants, m.rightLeg);
		copy(m.leftPants, m.leftLeg);
		copy(m.jacket, m.body);
	}

	/**
	 * The sampled pose, straight.
	 *
	 * Noise used to be added here too. It is first-person only now - wobble is
	 * the third-person setting and noise is the first-person one, one each, so
	 * changing a level has exactly one visible effect instead of two.
	 */
	private static OrdealAnimData.Pose swayed(Player p, String bone, int noise) {
		return OrdealAnimPlayback.pose(p, bone);
	}

	private static void apply(ModelPart part, OrdealAnimData.Pose pose, float w) {
		if (part == null || pose == null) return;
		part.xRot += pose.rx * DEG * w;
		part.yRot += pose.ry * DEG * w;
		part.zRot += pose.rz * DEG * w;
		part.x += pose.x * w;
		part.y += pose.y * w;
		part.z += pose.z * w;
	}

	private static void copy(ModelPart to, ModelPart from) {
		if (to == null || from == null) return;
		to.copyFrom(from);
	}
}