package net.mcreator.ordeal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * DRAWS THE PANOPLY ON THE PLAYER.
 *
 * A render layer on the player model, so every item follows the bone it is
 * anchored to and moves with the animation - a sheath on BODY swings with the
 * torso, a bracer on RIGHT_ARM swings with the arm. Positions come from
 * PanoplyAnchors; this file only puts them there.
 *
 * ONLY ACTIVE POINTS DRAW. Carried is storage - the spare blade in your kit
 * that is not on your body yet - and a drawn point is in your hand, so neither
 * renders here.
 *
 * PLACING MODELS: set DEBUG_MARKERS true and every point draws a small coloured
 * cube whether or not it holds anything. Get in third person, look at where the
 * cubes sit, and move the numbers in PanoplyAnchors until they are where the
 * models belong. Then build the models to match and turn markers back off.
 *
 * The items render with ItemDisplayContext.FIXED, which is the flat "in an item
 * frame" pose - right for a custom model authored facing forward. Change
 * CONTEXT if your models are authored for a different display slot.
 */
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public class PanoplyLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

	/** Draw a marker at every anchor, filled or not, so you can place models. */
	public static boolean DEBUG_MARKERS = false;

	/** Marker cube half-size in model pixels. */
	public static float MARKER = 0.6f;

	/** Which display pose the item model renders in. */
	public static ItemDisplayContext CONTEXT = ItemDisplayContext.FIXED;

	/** Master switch - false draws nothing at all. */
	public static boolean ENABLED = true;

	/**
	 * Set while a GUI is rendering the player into a panel. Suspends the
	 * first-person bail below, which would otherwise hide everything in the
	 * panoply page's own preview.
	 */
	public static boolean IN_GUI = false;

	public PanoplyLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch) {
		if (!ENABLED) return;

		// your own body is not drawn in first person, but the hand is - and the
		// hand render runs setupAnim on this same model, so bail rather than
		// smear panoply items across your first-person view.
		//
		// IN_GUI is the exception, and it is why nothing showed in the panoply
		// preview: that preview draws YOUR OWN player while the camera is still
		// first person, so this guard threw the whole layer away. The page sets
		// the flag around its own render and clears it in a finally.
		Minecraft mc = Minecraft.getInstance();
		if (!IN_GUI && mc.player == player && mc.options.getCameraType().isFirstPerson()) return;

		for (int pt = 0; pt < Panoply.POINTS; pt++) {
			ItemStack stack = PanoplyPayload.Client.at(pt);
			boolean live = PanoplyPayload.Client.isActive(pt);
			boolean marker = DEBUG_MARKERS;
			if (!marker && (stack.isEmpty() || !live)) continue;
			if (pt == PanoplyPayload.Client.DRAWN) continue;   // it is in your hand

			PanoplyAnchors.Anchor a = PanoplyAnchors.of(pt);
			ModelPart bone = boneOf(a.bone);
			if (bone == null) continue;

			ps.pushPose();
			bone.translateAndRotate(ps);          // into that bone's local space

			// PanoplyAnchors uses +Y = up the way anyone authoring expects.
			// Model space is Y-DOWN, so y is negated exactly once, here.
			ps.translate(a.x / 16f, -a.y / 16f, a.z / 16f);
			ps.mulPose(Axis.ZP.rotationDegrees(a.rz));
			ps.mulPose(Axis.YP.rotationDegrees(a.ry));
			ps.mulPose(Axis.XP.rotationDegrees(a.rx));

			if (marker && (stack.isEmpty() || !live)) {
				drawMarker(ps, buf, light, stack.isEmpty() ? 0xFF3C6478 : 0xFFFFB020);
			} else {
				ps.scale(a.scale, a.scale, a.scale);
				mc.getItemRenderer().renderStatic(stack, CONTEXT, light,
						OverlayTexture.NO_OVERLAY, ps, buf, player.level(), pt);
				if (marker) drawMarker(ps, buf, light, 0xFF5FE3A0);
			}
			ps.popPose();
		}
	}

	private ModelPart boneOf(PanoplyAnchors.Bone b) {
		PlayerModel<AbstractClientPlayer> m = getParentModel();
		return switch (b) {
			case HEAD -> m.head;
			case BODY -> m.body;
			case RIGHT_ARM -> m.rightArm;
			case LEFT_ARM -> m.leftArm;
			case RIGHT_LEG -> m.rightLeg;
			case LEFT_LEG -> m.leftLeg;
		};
	}

	/**
	 * A tiny solid cube so an anchor is visible with nothing equipped.
	 * Wrapped because debug render types are the first thing to move between
	 * mappings, and a marker is never worth crashing a render pass over.
	 */
	private void drawMarker(PoseStack ps, MultiBufferSource buf, int light, int argb) {
		try { marker(ps, buf, argb); } catch (Throwable ignored) {}
	}

	private void marker(PoseStack ps, MultiBufferSource buf, int argb) {
		float s = MARKER / 16f;
		VertexConsumer vc = buf.getBuffer(RenderType.debugFilledBox());
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		PoseStack.Pose pose = ps.last();
		float[][] q = {
			{-s,-s,-s},{ s,-s,-s},{ s, s,-s},{-s, s,-s},   // back
			{-s,-s, s},{-s, s, s},{ s, s, s},{ s,-s, s},   // front
			{-s,-s,-s},{-s, s,-s},{-s, s, s},{-s,-s, s},   // left
			{ s,-s,-s},{ s,-s, s},{ s, s, s},{ s, s,-s},   // right
			{-s, s,-s},{ s, s,-s},{ s, s, s},{-s, s, s},   // top
			{-s,-s,-s},{-s,-s, s},{ s,-s, s},{ s,-s,-s}    // bottom
		};
		for (float[] v : q)
			vc.addVertex(pose.pose(), v[0], v[1], v[2]).setColor(r, g, b, 1f);
	}

	// ==================== REGISTRATION ====================

	/**
	 * Bolts the layer onto both player skins (default and slim). Without this
	 * the class exists and never renders, which is the classic "why is nothing
	 * showing" for a render layer.
	 */
	@SubscribeEvent
	public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
		for (net.minecraft.client.resources.PlayerSkin.Model skin
				: net.minecraft.client.resources.PlayerSkin.Model.values()) {
			if (event.getSkin(skin) instanceof PlayerRenderer pr) pr.addLayer(new PanoplyLayer(pr));
		}
	}
}