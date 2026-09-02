package net.mcreator.ordeal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Ordeal Animator — client side.
 *
 * Holds the live editor session state, renders the posed dummy player
 * in the world (AFTER_ENTITIES), renders the rotate/move gizmo on the
 * selected bone, and caches world→screen projections so the Screen can
 * hit-test bones and gizmo handles in GUI space.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class OrdealAnimatorClient {

	// ---------------------------------------------------------------
	// Session state (read/written by OrdealAnimatorScreen)
	// ---------------------------------------------------------------

	public static boolean active = false;
	public static OrdealAnimData data = null;
	public static String clipName = "";
	public static float time = 0f;              // playhead, ticks
	public static Vec3 dummyPos = Vec3.ZERO;    // feet position of the dummy
	public static float modelYaw = 0f;          // drag-to-spin
	public static String selBone = "head";
	public static boolean rotateMode = true;    // true = rotate (R), false = move (T)
	/** Preview mode: false = third person, true = down your own eyes. */
	public static boolean firstPerson = false;

	/**
	 * Whether the dummy shows the procedural layers - living motion and wobble -
	 * on top of the clip.
	 *
	 * The screen switches this on while the timeline is PLAYING and off while it
	 * is parked. Keying a pose against a model that is breathing and swaying is
	 * miserable, but with the layers off entirely the Noise and Wobble dials look
	 * like they do nothing at all. Play = see what it will look like; stop = a
	 * clean pose to key against.
	 */
	public static boolean previewLayers = false;
	/** Pose override while the user is mid-drag on a bone (screen writes, renderer reads). */
	public static final Map<String, OrdealAnimData.Pose> livePose = new HashMap<>();

	// Projection cache for the Screen (GUI-scaled coordinates)
	public static final Map<String, float[]> boneScreen = new HashMap<>();
	/** axis (0=X,1=Y,2=Z) -> polyline of GUI points [x0,y0,x1,y1,...] for the gizmo circles/arrows. */
	public static final Map<Integer, float[]> gizmoScreen = new HashMap<>();
	public static float[] pivotScreen = null;

	private static PlayerModel<Player> model;
	private static Matrix4f lastMvp = null;

	public static final int AXIS_X = 0, AXIS_Y = 1, AXIS_Z = 2;
	public static final int[] AXIS_COLOR = {0xFFE05555, 0xFF55E055, 0xFF5580FF};

	// Vanilla part pivots (model px, y-down from top of a 32px-tall rig space)
	private static final Map<String, float[]> PIVOTS = new HashMap<>();
	static {
		PIVOTS.put("head", new float[]{0, 0, 0});
		PIVOTS.put("body", new float[]{0, 0, 0});
		PIVOTS.put("right_arm", new float[]{-5, 2, 0});
		PIVOTS.put("left_arm", new float[]{5, 2, 0});
		PIVOTS.put("right_leg", new float[]{-1.9f, 12, 0});
		PIVOTS.put("left_leg", new float[]{1.9f, 12, 0});
	}

	// Pivot heights above feet, in blocks, for gizmo placement
	private static final Map<String, Float> PIVOT_HEIGHT = new HashMap<>();
	static {
		PIVOT_HEIGHT.put("head", 1.5f);
		PIVOT_HEIGHT.put("body", 1.5f);
		PIVOT_HEIGHT.put("right_arm", 1.375f);
		PIVOT_HEIGHT.put("left_arm", 1.375f);
		PIVOT_HEIGHT.put("right_leg", 0.75f);
		PIVOT_HEIGHT.put("left_leg", 0.75f);
		PIVOT_HEIGHT.put("root", 0.9f);
	}

	// ---------------------------------------------------------------
	// Entry point (called by OrdealAnimatorCommand via reflection)
	// ---------------------------------------------------------------

	public static void open(String name) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			OrdealAnimData d = null;
			String clip = name == null ? "" : name;
			if (!clip.isEmpty())
				d = OrdealAnimStore.load(clip);
			if (d == null) {
				d = new OrdealAnimData();
				seed(d);
				if (clip.isEmpty())
					clip = "untitled";
			}
			data = d;
			clipName = clip;
			time = 0f;
			modelYaw = 0f;
			selBone = "head";
			rotateMode = true;
			firstPerson = false;
			livePose.clear();
			if (mc.player != null) {
				Vec3 look = mc.player.getLookAngle();
				dummyPos = mc.player.position().add(look.x * 3, 0, look.z * 3);
			}
			active = true;
			mc.setScreen(new OrdealAnimatorScreen());
		});
	}

	/** Seed key at t=0 on every bone so all timeline tracks are visible (IIC guard-pose lesson). */
	public static void seed(OrdealAnimData d) {
		for (String b : OrdealAnimData.BONES)
			d.putKey(b, new OrdealAnimData.Key(0f));
	}

	public static void close() {
		previewLayers = false;
		active = false;
		livePose.clear();
	}

	/** The channels the editor is showing: body in third, hands+camera in first. */
	public static String[] bones() {
		return firstPerson ? OrdealAnimData.FP_BONES : OrdealAnimData.BONES;
	}

	public static String[] boneLabels() {
		return firstPerson ? new String[] {"R Hand", "L Hand", "Camera"}
				: new String[] {"Head", "Body", "R Arm", "L Arm", "R Leg", "L Leg", "Root"};
	}

	/**
	 * Clip to copy the hands from when a clip has no first-person tracks yet.
	 *
	 * There is no hidden rest pose in first person - a pose of all zeros really
	 * does put the arm at the camera. Invincible solves that by always starting
	 * from a saved idle, so this does the same: the idle's first frame becomes
	 * your starting point, and you animate away from it.
	 */
	public static String FP_IDLE = "fs_standard_idle";

	/**
	 * Switching into first person for the first time on a clip lays the hands
	 * out for you: from the idle clip when you have one, otherwise from the
	 * body animation, scaled down to arm's length. Either way it is a starting
	 * point, not a link - edit it and nothing else moves.
	 */
	public static boolean seedFirstPerson() {
		if (data == null) return false;
		for (String b : OrdealAnimData.FP_BONES)
			if (!data.channel(b).isEmpty()) return false;      // already authored

		// Invincible's own new-frame pose: hands up and readable straight away.
		// This is what makes the arms visible the moment you switch to first
		// person, with nothing to import and nothing to set by hand.
		for (String b : OrdealAnimData.FP_BONES) {
			OrdealAnimData.Key k = new OrdealAnimData.Key(0f);
			if (!b.equals("fp_cam")) {
				float m = b.equals("fp_right") ? 1f : -1f;
				k.pos[0] = OrdealFirstPerson.OPEN_POS[0] * m;
				k.pos[1] = OrdealFirstPerson.OPEN_POS[1];
				k.pos[2] = OrdealFirstPerson.OPEN_POS[2];
				k.rot[0] = OrdealFirstPerson.OPEN_ROT[0];
				k.rot[1] = OrdealFirstPerson.OPEN_ROT[1] * m;
				k.rot[2] = OrdealFirstPerson.OPEN_ROT[2] * m;
			}
			data.putKey(b, k);
		}
		return true;
	}

	/** Kept for clips that would rather start from a saved idle than the default frame. */
	public static boolean seedFromIdle() {
		if (data == null) return false;
		OrdealAnimData idle = OrdealAnimStore.load(FP_IDLE);
		if (idle != null && !clipName.equals(FP_IDLE)) {
			boolean got = false;
			for (String b : OrdealAnimData.FP_BONES) {
				OrdealAnimData.Pose pose = idle.sample(b, 0f);
				if (pose == null) continue;
				OrdealAnimData.Key k = new OrdealAnimData.Key(0f);
				k.rot[0] = pose.rx; k.rot[1] = pose.ry; k.rot[2] = pose.rz;
				k.pos[0] = pose.x; k.pos[1] = pose.y; k.pos[2] = pose.z;
				data.putKey(b, k);
				got = true;
			}
			if (got) return true;
		}

		String[][] from = {{"right_arm", "fp_right"}, {"left_arm", "fp_left"}, {"head", "fp_cam"}};
		boolean any = false;
		for (String[] pair : from) {
			boolean cam = pair[1].equals("fp_cam");
			for (OrdealAnimData.Key k : new java.util.ArrayList<>(data.channel(pair[0]))) {
				OrdealAnimData.Key n = k.copy();
				for (int i = 0; i < 3; i++) {
					if (cam) {
						// the camera takes the head's angles straight, just softer
						n.rot[i] *= OrdealFirstPerson.CAM_STRENGTH;
						n.pos[i] = 0;
					} else {
						// model space -> view space, and down to arm's length,
						// because these tracks are now applied exactly as typed
						n.rot[i] *= OrdealFirstPerson.ARM_STRENGTH * OrdealFirstPerson.ROT_SIGN[i];
						// body offsets are model pixels; first person is blocks
						n.pos[i] *= OrdealFirstPerson.ARM_SHIFT
								* OrdealFirstPerson.POS_SIGN[i] / 16f;
					}
				}
				data.putKey(pair[1], n);
				any = true;
			}
		}
		if (!any)
			for (String b : OrdealAnimData.FP_BONES)
				data.putKey(b, new OrdealAnimData.Key(0f));
		return any;
	}

	/** Pose for a bone at the playhead: live drag override first, then sampled data. */
	public static OrdealAnimData.Pose poseFor(String bone) {
		OrdealAnimData.Pose p = livePose.get(bone);
		if (p != null)
			return p;
		return data == null ? null : data.sample(bone, time);
	}

	// ---------------------------------------------------------------
	// World render
	// ---------------------------------------------------------------

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (!active || data == null)
			return;
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES)
			return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null)
			return;

		// First person draws no dummy at all. The arms come from the game's own
		// hand renderer (OrdealFirstPerson), in view space, at the real scale -
		// a body model with its head switched off was never the same thing.
		if (firstPerson) {
			boneScreen.clear();
			gizmoScreen.clear();
			pivotScreen = null;
			return;
		}

		if (model == null)
			model = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);

		Vec3 cam = event.getCamera().getPosition();
		PoseStack ps = event.getPoseStack();

		// MVP for projection cache
		Matrix4f mvp = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
		lastMvp = mvp;

		ps.pushPose();
		ps.translate(dummyPos.x - cam.x, dummyPos.y - cam.y, dummyPos.z - cam.z);

		// root channel plus the wobble: both move the whole body, so both are a
		// stack transform here, exactly as OrdealAnimRender.applyRoot does it
		// in game
		OrdealAnimData.Pose root = poseFor("root");
		OrdealAnimData.Pose wob = previewLayers && data != null
				? OrdealAnimLean.preview(data.wobble) : null;
		if (root != null || wob != null) {
			float rx = root == null ? 0 : root.rx;
			float ry = root == null ? 0 : root.ry;
			float rz = root == null ? 0 : root.rz;
			if (wob != null) { rx += wob.rx; ry += wob.ry; rz += wob.rz; }
			if (root != null)
				ps.translate(root.x / 16f, -root.y / 16f, root.z / 16f);
			ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(ry));
			ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rx));
			ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rz));
		}

		ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(modelYaw));
		ps.translate(0, 1.501f, 0);
		ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));

		applyPose();

		ResourceLocation skin = ((AbstractClientPlayer) mc.player).getSkin().texture();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(skin));
		int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(dummyPos.add(0, 1, 0)));
		model.renderToBuffer(ps, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		buffers.endBatch();
		ps.popPose();

		renderGizmoAndProject(ps, buffers, cam);
	}

	private static void applyPose() {
		model.young = false;
		model.setAllVisible(true);
		// Wobble is NOT here. It moves the whole body and is applied to the
		// stack above, next to the root channel - putting it on the chest
		// fought every clip that posed the chest itself. Living motion is not
		// here either: noise is the first-person layer, and this is the
		// third-person dummy.
		pose(model.head, "head");
		pose(model.body, "body");
		pose(model.rightArm, "right_arm");
		pose(model.leftArm, "left_arm");
		pose(model.rightLeg, "right_leg");
		pose(model.leftLeg, "left_leg");
		// overlay layers follow their base parts
		model.hat.copyFrom(model.head);
		model.jacket.copyFrom(model.body);
		model.rightSleeve.copyFrom(model.rightArm);
		model.leftSleeve.copyFrom(model.leftArm);
		model.rightPants.copyFrom(model.rightLeg);
		model.leftPants.copyFrom(model.leftLeg);
	}

	private static void pose(ModelPart part, String bone) {
		float[] pivot = PIVOTS.get(bone);
		OrdealAnimData.Pose p = poseFor(bone);
		float rx = 0, ry = 0, rz = 0, ox = 0, oy = 0, oz = 0;
		if (p != null) {
			rx = p.rx;
			ry = p.ry;
			rz = p.rz;
			ox = p.x;
			oy = p.y;
			oz = p.z;
		}
		part.x = pivot[0] + ox;
		part.y = pivot[1] - oy; // model space is y-down; UI +Y means up
		part.z = pivot[2] + oz;
		part.xRot = (float) Math.toRadians(rx);
		part.yRot = (float) Math.toRadians(ry);
		part.zRot = (float) Math.toRadians(rz);
	}

	// ---------------------------------------------------------------
	// Gizmo render + projection cache
	// ---------------------------------------------------------------

	private static void renderGizmoAndProject(PoseStack ps, MultiBufferSource.BufferSource buffers, Vec3 cam) {
		boneScreen.clear();
		gizmoScreen.clear();
		pivotScreen = null;

		// project every bone pivot for click-select
		for (String bone : OrdealAnimData.BONES) {
			Float h = PIVOT_HEIGHT.get(bone);
			if (h == null)
				continue;
			Vec3 world = pivotWorld(bone, h);
			float[] scr = project(world);
			if (scr != null)
				boneScreen.put(bone, scr);
		}

		Float selH = PIVOT_HEIGHT.get(selBone);
		if (selH == null)
			return;
		Vec3 pivot = pivotWorld(selBone, selH);
		pivotScreen = project(pivot);

		float radius = "root".equals(selBone) ? 0.55f : 0.35f;

		for (int axis = 0; axis < 3; axis++) {
			VertexConsumer vc = buffers.getBuffer(RenderType.debugLineStrip(2.0));
			int color = AXIS_COLOR[axis];
			float r = ((color >> 16) & 0xFF) / 255f;
			float g = ((color >> 8) & 0xFF) / 255f;
			float b = (color & 0xFF) / 255f;

			int segs = 32;
			float[] guiPts = new float[(segs + 1) * 2];
			boolean anyOnScreen = false;

			ps.pushPose();
			ps.translate(pivot.x - cam.x, pivot.y - cam.y, pivot.z - cam.z);
			Matrix4f mat = ps.last().pose();

			if (rotateMode) {
				for (int i = 0; i <= segs; i++) {
					double a = (Math.PI * 2 * i) / segs;
					float px = 0, py = 0, pz = 0;
					if (axis == AXIS_X) {
						py = (float) (Math.cos(a) * radius);
						pz = (float) (Math.sin(a) * radius);
					} else if (axis == AXIS_Y) {
						px = (float) (Math.cos(a) * radius);
						pz = (float) (Math.sin(a) * radius);
					} else {
						px = (float) (Math.cos(a) * radius);
						py = (float) (Math.sin(a) * radius);
					}
					vc.addVertex(mat, px, py, pz).setColor(r, g, b, 1f);
					float[] scr = project(pivot.add(px, py, pz));
					if (scr != null) {
						guiPts[i * 2] = scr[0];
						guiPts[i * 2 + 1] = scr[1];
						anyOnScreen = true;
					} else {
						guiPts[i * 2] = Float.NaN;
						guiPts[i * 2 + 1] = Float.NaN;
					}
				}
			} else {
				// move mode: axis arrows
				float len = radius + 0.15f;
				float ex = axis == AXIS_X ? len : 0;
				float ey = axis == AXIS_Y ? len : 0;
				float ez = axis == AXIS_Z ? len : 0;
				vc.addVertex(mat, 0, 0, 0).setColor(r, g, b, 1f);
				vc.addVertex(mat, ex, ey, ez).setColor(r, g, b, 1f);
				float[] s0 = project(pivot);
				float[] s1 = project(pivot.add(ex, ey, ez));
				guiPts = new float[]{s0 == null ? Float.NaN : s0[0], s0 == null ? Float.NaN : s0[1],
						s1 == null ? Float.NaN : s1[0], s1 == null ? Float.NaN : s1[1]};
				anyOnScreen = s0 != null && s1 != null;
			}
			ps.popPose();
			buffers.endBatch();

			if (anyOnScreen)
				gizmoScreen.put(axis, guiPts);
		}
	}

	private static Vec3 pivotWorld(String bone, float height) {
		// yaw-rotate the pivot's horizontal offset around the dummy center
		float[] pv = PIVOTS.getOrDefault(bone, new float[]{0, 0, 0});
		double ox = -pv[0] / 16.0; // model x is mirrored by the 180° flip
		double oz = pv[2] / 16.0;
		double yawRad = Math.toRadians(-modelYaw);
		double rx = ox * Math.cos(yawRad) - oz * Math.sin(yawRad);
		double rz = ox * Math.sin(yawRad) + oz * Math.cos(yawRad);
		return new Vec3(dummyPos.x + rx, dummyPos.y + height, dummyPos.z + rz);
	}

	/** World point -> GUI-scaled screen coords, or null when behind the camera. */
	public static float[] project(Vec3 world) {
		if (lastMvp == null)
			return null;
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		Vector4f v = new Vector4f((float) (world.x - cam.x), (float) (world.y - cam.y), (float) (world.z - cam.z), 1f);
		lastMvp.transform(v);
		if (v.w <= 0.001f)
			return null;
		float ndcX = v.x / v.w;
		float ndcY = v.y / v.w;
		double scale = mc.getWindow().getGuiScale();
		float sx = (float) ((ndcX + 1f) / 2f * mc.getWindow().getWidth() / scale);
		float sy = (float) ((1f - ndcY) / 2f * mc.getWindow().getHeight() / scale);
		return new float[]{sx, sy};
	}
}