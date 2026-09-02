package net.mcreator.ordeal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;

/**
 * Applies playback poses to the player model.
 *
 * WHY THIS IS NO LONGER AN EVENT HANDLER.
 *
 * This used to pose the model from RenderPlayerEvent.Pre. That can never work.
 * LivingEntityRenderer.render() fires the Pre event FIRST and calls
 * model.setupAnim() AFTER it - so every rotation written here was overwritten
 * by vanilla's own posing a moment later, every frame, silently. Authored clips
 * looked like they were playing (weight ramped, the debug overlay said YES) and
 * nothing moved.
 *
 * Invincible hit the same wall and solved it with TpAnimModelMixin: pose at the
 * TAIL of setupAnim, after vanilla is done. Ordeal already has that mixin -
 * OrdealAnimPoseMixin - and it calls the apply() below. It had simply fallen
 * out of ordeal.mixins.json, so nothing was calling it and nothing rendered.
 *
 * The PoseStack is a different story: a transform pushed in RenderPlayerEvent.Pre
 * survives setupAnim untouched, which is why OrdealRootRender - the whole-body
 * lean - still lives on that event and works.
 *
 * WHAT LAYERS ON WHAT
 *   head, body        ADDITIVE - the head keeps tracking where you look
 *   arms, legs        ABSOLUTE - eased from the live vanilla rotation toward
 *                     the authored one by the blend weight, so a pose REPLACES
 *                     the walk cycle instead of stacking on top of it. This is
 *                     what Invincible does, and it is why its flight poses read
 *                     as poses rather than as a limp added to a run.
 *   root              not here - OrdealRootRender draws it as a render transform
 *
 * Flip ABSOLUTE_LIMBS to false to go back to purely additive limbs.
 */
public final class OrdealAnimRender {

	private OrdealAnimRender() {}

	private static final float DEG = (float) (Math.PI / 180.0);

	/**
	 * THE Y FLIP. The animator's UI treats +Y as UP, the way anyone authoring a
	 * pose expects. Minecraft's model space is Y-DOWN. OrdealAnimatorClient.pose()
	 * has always accounted for that - it writes "part.y = pivot[1] - oy" - but
	 * this renderer was doing "part.y += pose.y", so every authored vertical
	 * offset came out INVERTED in game.
	 *
	 * That is the legs. default_idle lifts the right leg by 1.96px; in game it
	 * was being pushed 1.96px DOWN instead, a ~4px error against the left leg,
	 * which is exactly the fused, offset legs. Editor and game now agree.
	 */
	private static final float Y_FLIP = -1f;

	/** Arms and legs replace the vanilla pose rather than adding to it. */
	public static boolean ABSOLUTE_LIMBS = true;

	/**
	 * Called from OrdealAnimPoseMixin at the tail of PlayerModel.setupAnim.
	 * Safe to call every frame for every player; returns immediately when there
	 * is nothing to draw.
	 */
	public static void apply(PlayerModel<?> m, Player p) {
		if (m == null || p == null) return;
		if (isFirstPersonHand(p)) return;

		boolean clip = OrdealAnimPlayback.isAnimating(p);
		OrdealAnimData.Pose lean = OrdealAnimLean.compute(p, OrdealAnimPlayback.wobbleLevel(p));
		if (!clip && lean == null) return;

		float w = OrdealAnimPlayback.weight(p);

		if (clip && w > 0f) {
			OrdealAnimData.Pose head = OrdealAnimPlayback.pose(p, "head");
			add(m.head, head, w);
			add(m.body, OrdealAnimPlayback.pose(p, "body"), w);
			limb(m.rightArm, OrdealAnimPlayback.pose(p, "right_arm"), w);
			limb(m.leftArm, OrdealAnimPlayback.pose(p, "left_arm"), w);
			limb(m.rightLeg, OrdealAnimPlayback.pose(p, "right_leg"), w);
			limb(m.leftLeg, OrdealAnimPlayback.pose(p, "left_leg"), w);
		}

		// the lean is its own layer - it runs whether or not a clip is playing.
		// While flying it stands down and OrdealRootRender leans the whole body
		// instead, because a body lean cannot be done on a ModelPart.
		if (lean != null) {
			add(m.body, lean, 1f);
			OrdealAnimData.Pose counter = new OrdealAnimData.Pose();
			counter.rx = -lean.rx * OrdealAnimLean.HEAD_COUNTER;
			counter.rz = -lean.rz * OrdealAnimLean.HEAD_COUNTER;
			add(m.head, counter, 1f);
		}

		// sleeves, trousers, jacket and hat follow the parts they sit on
		copy(m.hat, m.head);
		copy(m.jacket, m.body);
		copy(m.rightSleeve, m.rightArm);
		copy(m.leftSleeve, m.leftArm);
		copy(m.rightPants, m.rightLeg);
		copy(m.leftPants, m.leftLeg);
	}

	/**
	 * THIRD PERSON IS NOT FIRST PERSON.
	 *
	 * ItemInHandRenderer calls PlayerModel.setupAnim to draw the held hand, and
	 * OrdealAnimPoseMixin fires at the tail of every setupAnim - so a
	 * third-person clip was being painted onto the first-person hand as well.
	 * They are two systems with two commands; nothing here may touch the fp rig.
	 *
	 * Your own model is never drawn in third-person form while you are in first
	 * person, so skipping yourself whenever the camera is first person removes
	 * the hand leak and nothing else. Other players are untouched - they render
	 * in third person no matter which view you are in.
	 */
	private static boolean isFirstPersonHand(Player p) {
		try {
			Minecraft mc = Minecraft.getInstance();
			return mc.player == p && mc.options.getCameraType().isFirstPerson();
		} catch (Throwable t) {
			return false;
		}
	}

	/** Additive: the authored rotation is added to whatever vanilla posed. */
	private static void add(ModelPart part, OrdealAnimData.Pose pose, float w) {
		if (part == null || pose == null) return;
		part.xRot += pose.rx * DEG * w;
		part.yRot += pose.ry * DEG * w;
		part.zRot += pose.rz * DEG * w;
		part.x += pose.x * w;
		part.y += pose.y * Y_FLIP * w;
		part.z += pose.z * w;
	}

	/**
	 * Absolute: ease from the live vanilla rotation toward the authored one by
	 * the blend weight. w=1 is a full override, w=0 is untouched vanilla, so
	 * fading a clip in and out still blends smoothly over the walk cycle.
	 */
	private static void limb(ModelPart part, OrdealAnimData.Pose pose, float w) {
		if (part == null || pose == null) return;
		if (!ABSOLUTE_LIMBS) { add(part, pose, w); return; }
		float rx = pose.rx * DEG, ry = pose.ry * DEG, rz = pose.rz * DEG;
		part.xRot += (rx - part.xRot) * w;
		part.yRot += (ry - part.yRot) * w;
		part.zRot += (rz - part.zRot) * w;
		part.x += pose.x * w;
		part.y += pose.y * Y_FLIP * w;
		part.z += pose.z * w;
	}

	private static void copy(ModelPart to, ModelPart from) {
		if (to == null || from == null) return;
		to.copyFrom(from);
	}
}