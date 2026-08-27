package net.mcreator.ordeal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * See the shape of an area attack before you trust it.
 *
 * Draws the volume an AoE actually tests against, live, in the world, and
 * lights up every entity inside it. CUBE is exactly what MCreator's "in square
 * cube with size N" compiles to — an axis-aligned box, half-extent N/2 — so
 * you can watch the corners reach further than the faces do. The other shapes
 * are what you could switch to, drawn at the same size so the difference is
 * the only thing you see.
 *
 * Client only, and a client command, so it costs a shipped server nothing.
 *
 *   /aoe                 on / off
 *   /aoe shape cube|sphere|cylinder|cone|all
 *   /aoe size 6          the number you pass the block
 *   /aoe reach 1         how far in front of you it is centred
 *   /aoe eye 1.6         how far up
 *   /aoe height 4        cylinder only
 *   /aoe angle 90        cone only
 *   /aoe fixy            see the look.x/look.y drift described below
 *   /aoe pin             freeze it where it stands, so you can walk into it
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class AoeDebug {

	private AoeDebug() {}

	private static boolean on = false;
	private static String shape = "cube";
	private static double size = 6;      // the N from "square cube with size N"
	private static double reach = 1;     // forward offset of the centre
	private static double eye = 1.6;     // vertical offset of the centre
	private static double height = 4;    // cylinder
	private static double angle = 90;    // cone
	/** Your procedure feeds look.x into the Y offset; this shows it corrected. */
	private static boolean fixY = false;
	private static Vec3 pinned = null;

	private static final int CUBE = 0x7ED8F5, SPHERE = 0x5FE3A0,
			CYL = 0xB07FE8, CONE = 0xFF8A2B, HIT = 0xFFD860;

	// ---- command ------------------------------------------------------------

	@SubscribeEvent
	public static void onRegister(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("aoe")
				.executes(c -> {
					on = !on;
					say(c.getSource(), on ? "AoE debug ON - " + describe() : "AoE debug off");
					return 1;
				})
				.then(Commands.literal("shape")
						.then(Commands.argument("s", StringArgumentType.word())
								.executes(c -> {
									String s = StringArgumentType.getString(c, "s").toLowerCase();
									if (!s.matches("cube|sphere|cylinder|cone|all")) {
										say(c.getSource(), "shape must be cube, sphere, cylinder, cone or all");
										return 0;
									}
									shape = s;
									on = true;
									say(c.getSource(), "shape " + s);
									return 1;
								})))
				.then(num("size", v -> size = v))
				.then(num("reach", v -> reach = v))
				.then(num("eye", v -> eye = v))
				.then(num("height", v -> height = v))
				.then(num("angle", v -> angle = v))
				.then(Commands.literal("fixy").executes(c -> {
					fixY = !fixY;
					say(c.getSource(), fixY
							? "Y offset CORRECTED (uses look.y)"
							: "Y offset as your procedure has it (uses look.x)");
					return 1;
				}))
				.then(Commands.literal("pin").executes(c -> {
					LocalPlayer p = Minecraft.getInstance().player;
					pinned = pinned != null || p == null ? null : centre(p);
					say(c.getSource(), pinned == null ? "unpinned" : "pinned - walk into it");
					return 1;
				})));
	}

	private interface Setter { void set(double v); }

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
			num(String name, Setter setter) {
		return Commands.literal(name).then(Commands.argument("v", DoubleArgumentType.doubleArg(0, 128))
				.executes(c -> {
					setter.set(DoubleArgumentType.getDouble(c, "v"));
					on = true;
					say(c.getSource(), name + " = " + DoubleArgumentType.getDouble(c, "v"));
					return 1;
				}));
	}

	private static void say(net.minecraft.commands.CommandSourceStack src, String msg) {
		src.sendSuccess(() -> Component.literal("[aoe] " + msg), false);
	}

	private static String describe() {
		return shape + " size " + size + " reach " + reach + " eye " + eye;
	}

	// ---- geometry -----------------------------------------------------------

	/** The exact point your procedure centres on, bug and all. */
	private static Vec3 centre(Entity e) {
		Vec3 look = e.getLookAngle();
		double yOff = fixY ? look.y : look.x;   // the procedure uses look.x here
		return new Vec3(e.getX() + reach * look.x,
				e.getY() + eye + reach * yOff,
				e.getZ() + reach * look.z);
	}

	private static AABB box(Vec3 c) {
		return new AABB(c, c).inflate(size / 2d);
	}

	private static boolean caught(Entity e, Vec3 c, String s) {
		AABB b = e.getBoundingBox();
		return switch (s) {
			case "sphere" -> b.getCenter().distanceTo(c) <= size / 2d;
			case "cylinder" -> {
				Vec3 m = b.getCenter();
				double dx = m.x - c.x, dz = m.z - c.z;
				yield Math.sqrt(dx * dx + dz * dz) <= size / 2d && Math.abs(m.y - c.y) <= height / 2d;
			}
			case "cone" -> {
				Vec3 m = b.getCenter();
				Vec3 to = m.subtract(c);
				if (to.length() > size / 2d) yield false;
				LocalPlayer p = Minecraft.getInstance().player;
				if (p == null) yield false;
				Vec3 look = p.getLookAngle();
				Vec3 flatTo = new Vec3(to.x, 0, to.z);
				Vec3 flatLook = new Vec3(look.x, 0, look.z);
				if (flatTo.lengthSqr() < 1e-6 || flatLook.lengthSqr() < 1e-6) yield true;
				double dot = flatTo.normalize().dot(flatLook.normalize());
				yield Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot)))) <= angle / 2d;
			}
			default -> box(c).intersects(b);
		};
	}

	// ---- render -------------------------------------------------------------

	@SubscribeEvent
	public static void onRender(RenderLevelStageEvent event) {
		if (!on || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) return;

		Vec3 c = pinned != null ? pinned : centre(p);
		Vec3 cam = event.getCamera().getPosition();
		PoseStack ps = event.getPoseStack();
		MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

		boolean all = shape.equals("all");
		if (all || shape.equals("cube")) cube(ps, buf, cam, c, CUBE);
		if (all || shape.equals("sphere")) sphere(ps, buf, cam, c, SPHERE);
		if (all || shape.equals("cylinder")) cylinder(ps, buf, cam, c, CYL);
		if (all || shape.equals("cone")) cone(ps, buf, cam, c, p.getLookAngle(), CONE);

		// light up whatever the live shape would actually hit
		String test = all ? "cube" : shape;
		int hits = 0;
		for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box(c).inflate(4), x -> x != p)) {
			if (!caught(e, c, test)) continue;
			hits++;
			AABB b = e.getBoundingBox();
			edges(ps, buf, cam, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, HIT);
		}

		if (p.tickCount % 5 == 0)
			p.displayClientMessage(Component.literal(
					"§b" + (all ? "all" : shape) + "§7 size §f" + size
							+ "§7 · hits §f" + hits
							+ (pinned != null ? " §7· §epinned" : "")
							+ (fixY ? " §7· §afixy" : "")), true);
	}

	private static void cube(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam, Vec3 c, int col) {
		double r = size / 2d;
		edges(ps, buf, cam, c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r, col);
	}

	private static void edges(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam,
			double x0, double y0, double z0, double x1, double y1, double z1, int col) {
		strip(ps, buf, cam, col, new double[][] {
				{x0,y0,z0},{x1,y0,z0},{x1,y0,z1},{x0,y0,z1},{x0,y0,z0} });
		strip(ps, buf, cam, col, new double[][] {
				{x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1},{x0,y1,z0} });
		strip(ps, buf, cam, col, new double[][] {{x0,y0,z0},{x0,y1,z0}});
		strip(ps, buf, cam, col, new double[][] {{x1,y0,z0},{x1,y1,z0}});
		strip(ps, buf, cam, col, new double[][] {{x1,y0,z1},{x1,y1,z1}});
		strip(ps, buf, cam, col, new double[][] {{x0,y0,z1},{x0,y1,z1}});
	}

	private static void sphere(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam, Vec3 c, int col) {
		double r = size / 2d;
		for (int axis = 0; axis < 3; axis++) {
			double[][] pts = new double[41][3];
			for (int i = 0; i <= 40; i++) {
				double a = Math.PI * 2 * i / 40;
				double u = Math.cos(a) * r, v = Math.sin(a) * r;
				pts[i] = axis == 0 ? new double[]{c.x, c.y + u, c.z + v}
						: axis == 1 ? new double[]{c.x + u, c.y, c.z + v}
						: new double[]{c.x + u, c.y + v, c.z};
			}
			strip(ps, buf, cam, col, pts);
		}
	}

	private static void cylinder(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam, Vec3 c, int col) {
		double r = size / 2d, hh = height / 2d;
		for (int k = 0; k < 2; k++) {
			double y = c.y + (k == 0 ? -hh : hh);
			double[][] pts = new double[41][3];
			for (int i = 0; i <= 40; i++) {
				double a = Math.PI * 2 * i / 40;
				pts[i] = new double[]{c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r};
			}
			strip(ps, buf, cam, col, pts);
		}
		for (int i = 0; i < 4; i++) {
			double a = Math.PI * 2 * i / 4;
			double px = c.x + Math.cos(a) * r, pz = c.z + Math.sin(a) * r;
			strip(ps, buf, cam, col, new double[][] {{px, c.y - hh, pz}, {px, c.y + hh, pz}});
		}
	}

	private static void cone(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam, Vec3 c,
			Vec3 look, int col) {
		double r = size / 2d;
		double base = Math.atan2(look.z, look.x);
		double half = Math.toRadians(angle / 2d);
		double[][] arc = new double[33][3];
		for (int i = 0; i <= 32; i++) {
			double a = base - half + (half * 2) * i / 32;
			arc[i] = new double[]{c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r};
		}
		strip(ps, buf, cam, col, arc);
		strip(ps, buf, cam, col, new double[][] {{c.x, c.y, c.z}, arc[0]});
		strip(ps, buf, cam, col, new double[][] {{c.x, c.y, c.z}, arc[32]});
	}

	private static void strip(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam,
			int col, double[][] pts) {
		float r = ((col >> 16) & 0xFF) / 255f, g = ((col >> 8) & 0xFF) / 255f, b = (col & 0xFF) / 255f;
		VertexConsumer vc = buf.getBuffer(RenderType.debugLineStrip(2.0));
		ps.pushPose();
		ps.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f mat = ps.last().pose();
		for (double[] pt : pts)
			vc.addVertex(mat, (float) pt[0], (float) pt[1], (float) pt[2]).setColor(r, g, b, 1f);
		ps.popPose();
		buf.endBatch();
	}
}