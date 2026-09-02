package net.mcreator.ordeal;

import net.mcreator.ordeal.core.client.OrdealDraw;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * THE PANOPLY PAGE - tab 5 of the terminal, beside CHARACTER / TALENTS / CHI /
 * TRAITS. It draws inside the terminal's own 520x306 panel and uses its own
 * header, footer and tab strip, so this file holds only the page body.
 *
 * That keeps the edit to OrdealTerminalPainter down to five lines - see the
 * snippet - instead of dropping a rewritten 58 KB file on you.
 *
 * HOW IT BEHAVES, matching the ability menu - click to pick, click to place:
 *   left click an inventory slot                     -> pick that item up
 *   left click a point while holding one             -> place it there
 *   shift + left click an inventory slot             -> straight to the first
 *                                                       point it legally fits
 *   left click a filled point with nothing picked    -> show / hide it
 *   DRAG a point onto an equip bar slot              -> bind it to that number
 *   DRAG an equip bar slot onto another              -> swap the two
 *   DRAG an equip bar slot off the bar               -> empty that slot
 *   shift + left click a filled point                -> take it out to your bag
 *   right click a filled point                       -> draw it to your hand
 *   right click an equip bar slot                    -> empty it
 *   right click, or click empty space, while holding -> put it back down
 *   DRAG inside the preview                         -> turn the model
 *   the < o > buttons                                -> turn 22.5 a click,
 *                                                       90 with shift, o resets
 *
 * YOUR INVENTORY sits under the preview, three rows and the hotbar, numbered
 * the way vanilla numbers them. The page reads it for display only; the placing
 * is a request carrying the SLOT NUMBER, and the server reads the real stack
 * out of its own copy. A client cannot conjure an item by lying about it.
 *
 * SHOWN vs HIDDEN. Everything you put in is storage AND is on your model by
 * default - a point is SHOWN the moment you place it. Clicking only hides it:
 * the item is still there, still yours, still drawable, just not rendered and
 * its passive off. Lit frame is shown, dimmed is hidden.
 *
 * THE EQUIP BAR is separate from that, and it is yours to arrange: six slots,
 * bound by dragging. It is what the HUD strip left of your hotbar draws and
 * what the equip key's numbers and scroll wheel count along.
 *
 * The page NEVER moves an item itself. Every click asks the server, so what you
 * see is always what the server agreed to.
 */
@OnlyIn(Dist.CLIENT)
public final class PanoplyPage {

	private PanoplyPage() {}

	// geometry inside the terminal panel, in panel-local coordinates
	private static final int PAD = 14;
	private static final int SLOT = 30, GAP = 5;
	private static final int MODEL_X = PAD, MODEL_Y = 32, MODEL_W = 150, MODEL_H = 130;

	/** Your inventory, drawn under the preview so you can place from it. */
	private static final int INV_X = PAD, INV_Y = 178, INV_CELL = 16, INV_PITCH = 17;
	private static final int INV_HOTBAR_Y = INV_Y + 3 * INV_PITCH + 6;
	private static final int COL_A = 176, COL_B = 344;
	private static final int GROUP_PITCH = 48;
	// BAR_Y clears BAND_Y's label by enough for the slot numbers, which sit 8px
	// above each box - at 245 those digits landed inside "EQUIP BAR" itself
	private static final int BAND_X = 176, BAND_Y = 228, BAR_Y = 254;
	/** The bottom band runs smaller so the equip bar and the armour lane both fit. */
	private static final int SMALL = 20, SMALL_GAP = 4;
	private static final int DRAW_SLOTS = 6, ARMOR_SLOTS = 5;
	private static final int AMBER = 0xFFFFB020, AMBER_LOCKED = 0xFF5A4520;

	/** Rarity owns the frame. Cyan stays terminal chrome so empty never reads as a tier. */
	private static final int R_COMMON = 0xFF8FA8B6, R_UNCOMMON = 0xFF5FE3A0,
			R_RARE = 0xFF4FA8FF, R_EPIC = 0xFF9B6BFF, R_LEGENDARY = 0xFFFFB020;

	private static int hover = -1;
	private static int hoverInv = -1;

	/**
	 * The inventory slot you have picked up, or -1. This is the ability menu's
	 * select-then-place, not a drag: click an item, click a point. No container
	 * cursor is involved, so nothing can be left floating if a click is lost.
	 */
	private static int picked = -1;

	/**
	 * DRAG STATE. A press on a point or a bar slot arms a drag; it only becomes
	 * a drag once the mouse actually moves, so a press-and-release in place is
	 * still an ordinary click and the show/hide toggle survives.
	 */
	private static int dragPoint = -1;    // dragging FROM a panoply point
	private static int dragBar = -1;      // dragging FROM a bar slot
	private static int dragX, dragY;
	private static boolean dragging = false;
	private static final int DRAG_SLOP = 3;

	// ---- the preview turntable ----
	/** Degrees. 0 faces you; grows clockwise seen from above. */
	private static float spin = 0f;
	/** Degrees of tilt, clamped so the model never rolls over onto its head. */
	private static float tilt = 0f;
	private static final float TILT_MAX = 40f;
	private static boolean spinDrag = false;
	private static int spinLastX, spinLastY;

	private static final int ROT_W = 15, ROT_H = 11, ROT_GAP = 3, ROT_N = 3;
	private static final String[] ROT_GLYPH = { "<", "o", ">" };

	// ==================== LAYOUT ====================

	/** Screen position of a flat point. x,y are the panel's top-left. */
	private static int[] posOf(int x, int y, int pt) {
		int slot = Panoply.slotOf(pt);
		if (slot < 0) return null;
		int entry = pt - Panoply.BASE[slot];
		int col = (slot % 2 == 0) ? COL_A : COL_B;
		int row = slot / 2;
		return new int[] { x + col + entry * (SLOT + GAP), y + MODEL_Y + row * GROUP_PITCH + 11 };
	}

	/** Top-left of rotate button i: 0 left, 1 reset, 2 right. */
	private static int[] rotPos(int x, int y, int i) {
		int total = ROT_N * ROT_W + (ROT_N - 1) * ROT_GAP;
		int rx = x + MODEL_X + MODEL_W - 5 - total;
		return new int[] { rx + i * (ROT_W + ROT_GAP), y + MODEL_Y + 4 };
	}

	private static int rotAt(int x, int y, double mx, double my) {
		for (int i = 0; i < ROT_N; i++) {
			int[] p = rotPos(x, y, i);
			if (mx >= p[0] && mx < p[0] + ROT_W && my >= p[1] && my < p[1] + ROT_H) return i;
		}
		return -1;
	}

	/** The box the model itself is drawn in - also the turntable's drag area. */
	private static int[] previewBox(int x, int y) {
		return new int[] {
			x + MODEL_X + 6,            y + MODEL_Y + 20,
			x + MODEL_X + MODEL_W - 6,  y + MODEL_Y + MODEL_H - 20 };
	}

	private static boolean inPreview(int x, int y, double mx, double my) {
		int[] b = previewBox(x, y);
		return mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
	}

	/** Top-left of equip bar slot i. */
	private static int[] barPos(int x, int y, int i) {
		return new int[] { x + BAND_X + i * (SMALL + SMALL_GAP), y + BAR_Y };
	}

	private static int barSlotAt(int x, int y, double mx, double my) {
		for (int i = 0; i < Panoply.BAR_SLOTS; i++) {
			int[] p = barPos(x, y, i);
			if (mx >= p[0] && mx < p[0] + SMALL && my >= p[1] && my < p[1] + SMALL) return i;
		}
		return -1;
	}

	private static int pointAt(int x, int y, double mx, double my) {
		for (int pt = 0; pt < Panoply.POINTS; pt++) {
			int[] p = posOf(x, y, pt);
			if (p == null) continue;
			if (mx >= p[0] && mx < p[0] + SLOT && my >= p[1] && my < p[1] + SLOT) return pt;
		}
		return -1;
	}

	// ==================== RENDER ====================

	/** Called from OrdealTerminalPainter when page == 4. */
	public static void render(GuiGraphics g, int x, int y, int mx, int my) {
		hover = pointAt(x, y, mx, my);
		Minecraft mc = Minecraft.getInstance();

		// a press only becomes a DRAG once the mouse has actually moved, so a
		// press-and-release in place is still a plain click
		if ((dragPoint >= 0 || dragBar >= 0) && !dragging
				&& (Math.abs(mx - dragX) > DRAG_SLOP || Math.abs(my - dragY) > DRAG_SLOP))
			dragging = true;

		// ---- model preview ----
		if (spinDrag) {
			spin += (mx - spinLastX) * 1.6f;
			tilt = Math.max(-TILT_MAX, Math.min(TILT_MAX, tilt + (my - spinLastY) * 1.0f));
			spinLastX = mx; spinLastY = my;
			spin = ((spin % 360f) + 360f) % 360f;   // keep it from drifting off
		}

		OrdealDraw.rect(g, x + MODEL_X, y + MODEL_Y, MODEL_W, MODEL_H, 0x99060A10);
		OrdealDraw.outline(g, x + MODEL_X, y + MODEL_Y, MODEL_W, MODEL_H, 0xFF1E3040);
		OrdealDraw.hatch(g, x + MODEL_X + 1, y + MODEL_Y + 1, MODEL_W - 2, MODEL_H - 2, 0x0AFFFFFF, 6);
		OrdealDraw.text(g, "PREVIEW", x + MODEL_X + 6, y + MODEL_Y + 5, OrdealDraw.INK_DIM);
		drawTurntable(g, x, y, mx, my);
		if (mc.player != null) preview(g, x, y, mx, my);

		drawInventory(g, x, y, mx, my);

		int carried = PanoplyPayload.Client.carried();
		int active = PanoplyPayload.Client.activeCount();
		int drawn = PanoplyPayload.Client.DRAWN;
		OrdealDraw.text(g, carried + "/" + Panoply.POINTS + " CARRIED  " + active + " SHOWN",
				x + MODEL_X + 6, y + MODEL_Y + MODEL_H - 14, OrdealDraw.CYAN_DIM);

		// ---- the eight slot groups ----
		for (int slot = 0; slot < Panoply.SLOTS.length; slot++) {
			int col = (slot % 2 == 0) ? COL_A : COL_B;
			int gx = x + col, gy = y + MODEL_Y + (slot / 2) * GROUP_PITCH;
			OrdealDraw.text(g, Panoply.LABEL[slot], gx, gy, OrdealDraw.INK);
			// no capacity number here - the slot frames already show how many
			// there are, and a lone digit floating in the gap just reads as junk
			for (int e = 0; e < Panoply.CAP[slot]; e++) point(g, x, y, Panoply.BASE[slot] + e);
		}

		// ---- bottom band: equip bar left, talent armor right ----
		// starts at BAND_X so it never crosses the inventory column
		OrdealDraw.rect(g, x + BAND_X, y + BAND_Y, 520 - PAD - BAND_X, 1, 0xFF16232F);
		// no hint string here - "TALENT ARMOR" is right-aligned on this same
		// line and anything longer than the label runs straight into it
		OrdealDraw.text(g, "EQUIP BAR", x + BAND_X, y + BAND_Y + 7, OrdealDraw.GREEN);
		OrdealDraw.textRight(g, "TALENT ARMOR", x + 520 - PAD, y + BAND_Y + 7, AMBER);

		// SIX EQUIP SLOTS, always drawn - an empty rack still has to read as a
		// rack, and it is the drop target, so it cannot disappear when empty.
		int[] bar = PanoplyPayload.Client.BAR;
		int hoverBar = barSlotAt(x, y, mx, my);
		boolean dropping = dragging && (dragPoint >= 0 || dragBar >= 0);

		for (int i = 0; i < DRAW_SLOTS && i < bar.length; i++) {
			int[] bp = barPos(x, y, i);
			int bx = bp[0], by = bp[1];
			int pt = bar[i];
			ItemStack s = pt < 0 ? ItemStack.EMPTY : PanoplyPayload.Client.at(pt);
			boolean target = dropping && i == hoverBar;

			OrdealDraw.rect(g, bx, by, SMALL, SMALL,
					target ? 0x337ED8F5 : s.isEmpty() ? 0x66070B12 : 0x99070B12);
			OrdealDraw.outline(g, bx, by, SMALL, SMALL,
					target ? OrdealDraw.CYAN
					: pt >= 0 && pt == drawn ? OrdealDraw.GREEN
					: dropping ? OrdealDraw.CYAN_DIM
					: s.isEmpty() ? OrdealDraw.LOCKED : OrdealDraw.CYAN_FAINT);
			if (!s.isEmpty() && !(dragging && dragBar == i)) g.renderItem(s, bx + 2, by + 2);
			OrdealDraw.text(g, String.valueOf(i + 1), bx + 1, by - 8,
					s.isEmpty() ? OrdealDraw.LOCKED : OrdealDraw.CYAN);
		}

		// five talent-armor slots, right aligned. States fill these; you cannot.
		int tstage = 0;
		try { tstage = StageLadder.CLIENT_STAGE; } catch (Throwable ignored) {}
		int tx0 = x + 520 - PAD - (ARMOR_SLOTS * SMALL + (ARMOR_SLOTS - 1) * SMALL_GAP);
		for (int i = 0; i < ARMOR_SLOTS; i++) {
			int bx = tx0 + i * (SMALL + SMALL_GAP), by = y + BAR_Y;
			boolean on = tstage > 0;
			OrdealDraw.rect(g, bx, by, SMALL, SMALL, on ? 0x33241B0C : 0x66070B12);
			OrdealDraw.outline(g, bx, by, SMALL, SMALL, on ? AMBER : AMBER_LOCKED);
			OrdealDraw.hatch(g, bx + 1, by + 1, SMALL - 2, SMALL - 2, 0x14FFB020, 5);
		}

		// a tooltip under a dragged item just gets in the way
		if (!dragging) {
			if (hover >= 0) tooltip(g, mx, my, hover);
			else if (hoverInv >= 0) {
				ItemStack s = invStack(hoverInv);
				if (!s.isEmpty()) g.renderTooltip(mc.font, s, mx, my);
			}
		}

		// what you have picked up follows the mouse, so a placement reads as a
		// placement rather than a mystery
		if (picked >= 0) {
			ItemStack s = invStack(picked);
			if (s.isEmpty()) picked = -1;
			else {
				g.renderItem(s, mx - 8, my - 8);
				g.renderItemDecorations(mc.font, s, mx - 8, my - 8);
			}
		}

		// and so does whatever you are dragging to the equip bar
		if (dragging) {
			int pt = dragPoint >= 0 ? dragPoint
					: dragBar >= 0 ? PanoplyPayload.Client.barAt(dragBar) : -1;
			ItemStack s = pt < 0 ? ItemStack.EMPTY : PanoplyPayload.Client.at(pt);
			if (!s.isEmpty()) g.renderItem(s, mx - 8, my - 8);
			OrdealDraw.text(g, dragBar >= 0 && hoverBar < 0 ? "§8release to empty" : "§bequip bar",
					mx + 10, my + 8, OrdealDraw.CYAN_DIM);
		}
	}

	// ==================== PREVIEW ====================

	/**
	 * The player, in the panel, at whatever angle you have turned it to.
	 *
	 * NOT mouse-follow any more: the model stays where you put it, so you can
	 * turn it to the hip you are working on and then go click that hip without
	 * the model spinning away as the cursor travels. Drag inside the box, or
	 * use the three buttons.
	 *
	 * PanoplyLayer.IN_GUI is the important line. The layer refuses to draw your
	 * own body while the camera is first person - which it is, sitting in a
	 * menu - so without this flag the preview shows a bare skin and none of
	 * your panoply. Cleared in a finally so a throw in the middle of a render
	 * cannot leave items smeared across your first-person hand.
	 */
	private static void preview(GuiGraphics g, int x, int y, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		int[] b = previewBox(x, y);
		boolean was = PanoplyLayer.IN_GUI;
		PanoplyLayer.IN_GUI = true;
		try {
			turntable(g, b[0], b[1], b[2], b[3], 42, 0.0625f, spin, tilt, mc.player);
		} catch (Throwable t) {
			// any mapping surprise in the entity-in-inventory call and we fall
			// back to vanilla's own helper rather than losing the whole page
			InventoryScreen.renderEntityInInventoryFollowsMouse(g,
					b[0], b[1], b[2], b[3], 42, 0.0625f, mx, my, mc.player);
		} finally {
			PanoplyLayer.IN_GUI = was;
		}
	}

	/**
	 * Vanilla's entity-in-a-panel render with the angles handed in instead of
	 * read off the cursor. Body and head turn together - the mouse-follow
	 * version turns the head twice as far as the body, which looks alive but
	 * makes it impossible to see the back of a head straight on.
	 *
	 * Every rotation field is saved and put back: this is your real player
	 * entity, not a copy, and leaving yBodyRot at a menu value would twist you
	 * in the world.
	 */
	private static void turntable(GuiGraphics g, int x1, int y1, int x2, int y2,
			int scale, float yOffset, float yaw, float pitch, LivingEntity e) {
		float cx = (x1 + x2) / 2f, cy = (y1 + y2) / 2f;
		g.enableScissor(x1, y1, x2, y2);

		Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf cam = new Quaternionf().rotateX(pitch * ((float) Math.PI / 180f));
		pose.mul(cam);

		float bYaw = e.yBodyRot, rYaw = e.getYRot(), rPitch = e.getXRot();
		float hYawO = e.yHeadRotO, hYaw = e.yHeadRot;

		e.yBodyRot = 180f + yaw;
		e.setYRot(180f + yaw);
		e.setXRot(-pitch);
		e.yHeadRot = e.getYRot();
		e.yHeadRotO = e.getYRot();

		float s = e.getScale();
		Vector3f tr = new Vector3f(0f, e.getBbHeight() / 2f + yOffset * s, 0f);
		InventoryScreen.renderEntityInInventory(g, cx, cy, (float) scale / s, tr, pose, cam, e);

		e.yBodyRot = bYaw;
		e.setYRot(rYaw);
		e.setXRot(rPitch);
		e.yHeadRotO = hYawO;
		e.yHeadRot = hYaw;
		g.disableScissor();
	}

	/** Three small buttons in the corner of the preview: turn, reset, turn. */
	private static void drawTurntable(GuiGraphics g, int x, int y, int mx, int my) {
		int hot = rotAt(x, y, mx, my);
		for (int i = 0; i < ROT_N; i++) {
			int[] p = rotPos(x, y, i);
			boolean on = i == hot;
			OrdealDraw.rect(g, p[0], p[1], ROT_W, ROT_H, on ? 0x337ED8F5 : 0x66070B12);
			OrdealDraw.outline(g, p[0], p[1], ROT_W, ROT_H, on ? OrdealDraw.CYAN : OrdealDraw.LOCKED);
			String s = ROT_GLYPH[i];
			OrdealDraw.text(g, s, p[0] + (ROT_W - OrdealDraw.width(s)) / 2, p[1] + 2,
					on ? OrdealDraw.CYAN : OrdealDraw.CYAN_DIM);
		}
	}

	private static void turn(float deg) {
		spin = ((spin + deg) % 360f + 360f) % 360f;
	}

	// ==================== INVENTORY ====================

	/**
	 * Your own inventory, under the preview. Three rows then the hotbar, the
	 * way every Minecraft screen shows it, so it reads without a legend.
	 *
	 * Slot numbering is vanilla's: 0-8 is the hotbar, 9-35 the main rows. The
	 * hotbar is drawn last but numbered first, which is why it is a separate
	 * loop rather than one run of 36.
	 */
	private static void drawInventory(GuiGraphics g, int x, int y, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		hoverInv = invSlotAt(x, y, mx, my);

		OrdealDraw.text(g, "INVENTORY", x + INV_X, y + INV_Y - 10, OrdealDraw.INK_DIM);
		for (int i = 0; i < 36; i++) {
			int[] p = invPos(x, y, i);
			ItemStack s = invStack(i);
			boolean hot = i == hoverInv;
			boolean sel = i == picked;
			OrdealDraw.rect(g, p[0], p[1], INV_CELL, INV_CELL,
					sel ? 0x337ED8F5 : hot ? 0x22FFFFFF : 0x66070B12);
			OrdealDraw.outline(g, p[0], p[1], INV_CELL, INV_CELL,
					sel ? OrdealDraw.CYAN : hot ? OrdealDraw.CYAN_DIM : OrdealDraw.LOCKED);
			if (!s.isEmpty()) {
				g.renderItem(s, p[0], p[1]);
				g.renderItemDecorations(mc.font, s, p[0], p[1]);
			}
		}
	}

	/** Top-left of an inventory slot. 0-8 hotbar, 9-35 the three rows above. */
	private static int[] invPos(int x, int y, int slot) {
		if (slot < 9) return new int[] { x + INV_X + slot * INV_PITCH, y + INV_HOTBAR_Y };
		int i = slot - 9;
		return new int[] { x + INV_X + (i % 9) * INV_PITCH, y + INV_Y + (i / 9) * INV_PITCH };
	}

	private static int invSlotAt(int x, int y, double mx, double my) {
		for (int i = 0; i < 36; i++) {
			int[] p = invPos(x, y, i);
			if (mx >= p[0] && mx < p[0] + INV_CELL && my >= p[1] && my < p[1] + INV_CELL) return i;
		}
		return -1;
	}

	private static ItemStack invStack(int slot) {
		Player p = Minecraft.getInstance().player;
		if (p == null || slot < 0 || slot >= 36) return ItemStack.EMPTY;
		return p.getInventory().getItem(slot);
	}

	private static void point(GuiGraphics g, int x, int y, int pt) {
		int[] p = posOf(x, y, pt);
		if (p == null) return;
		ItemStack s = PanoplyPayload.Client.at(pt);
		boolean live = PanoplyPayload.Client.isActive(pt);
		boolean isDrawn = pt == PanoplyPayload.Client.DRAWN;
		boolean hot = pt == hover;

		OrdealDraw.rect(g, p[0], p[1], SLOT, SLOT,
				hot ? 0x22FFFFFF : s.isEmpty() ? 0x66070B12 : 0x99070B12);

		int frame;
		if (isDrawn) frame = OrdealDraw.GREEN;
		else if (s.isEmpty()) frame = hot ? OrdealDraw.CYAN_DIM : OrdealDraw.LOCKED;
		else frame = live ? rarity(s) : OrdealDraw.alpha(rarity(s), 0x66);
		OrdealDraw.outline(g, p[0], p[1], SLOT, SLOT, frame);

		if (!s.isEmpty()) {
			if (!(dragging && dragPoint == pt)) {
				g.renderItem(s, p[0] + 7, p[1] + 7);
				g.renderItemDecorations(Minecraft.getInstance().font, s, p[0] + 7, p[1] + 7);
			}
			// green pip = shown on your model, dark pip = hidden
			OrdealDraw.rect(g, p[0] + SLOT - 5, p[1] + 2, 3, 3,
					live ? OrdealDraw.GREEN : 0xFF2D4A59);
			// its equip bar number, bottom left - the same digit you press
			int bs = PanoplyPayload.Client.barSlotOf(pt);
			if (bs >= 0)
				OrdealDraw.text(g, String.valueOf(bs + 1), p[0] + 3, p[1] + SLOT - 9,
						OrdealDraw.CYAN);
		} else {
			int e = pt - Panoply.BASE[Panoply.slotOf(pt)];
			OrdealDraw.text(g, String.valueOf(e + 1), p[0] + SLOT - 7, p[1] + SLOT - 9,
					OrdealDraw.LOCKED);
		}
	}

	/**
	 * Frame colour by rarity. Reads the item's own rarity, which is what
	 * MCreator sets on a custom item - swap for a data component when you want
	 * rarity to be its own field.
	 */
	private static int rarity(ItemStack s) {
		if (s.isEmpty()) return OrdealDraw.LOCKED;
		return switch (s.getRarity()) {
			case UNCOMMON -> R_UNCOMMON;
			case RARE -> R_RARE;
			case EPIC -> R_EPIC;
			default -> s.isEnchanted() ? R_LEGENDARY : R_COMMON;
		};
	}

	private static void tooltip(GuiGraphics g, int mx, int my, int pt) {
		Minecraft mc = Minecraft.getInstance();
		ItemStack s = PanoplyPayload.Client.at(pt);
		int slot = Panoply.slotOf(pt);
		if (s.isEmpty()) {
			ItemStack c = cursor();
			String line = c.isEmpty()
					? "§8empty · " + Panoply.LABEL[slot].toLowerCase(Locale.ROOT)
					: Panoply.fits(c, slot)
						? "§aplace " + c.getHoverName().getString() + " here"
						: "§c" + c.getHoverName().getString() + " does not go here";
			g.renderTooltip(mc.font, Component.literal(line), mx, my);
			return;
		}
		List<Component> lines = new ArrayList<>();
		lines.add(s.getHoverName());
		lines.add(Component.literal(PanoplyPayload.Client.isActive(pt)
				? "§aSHOWN §8· on your model, passive live"
				: "§8HIDDEN · still carried, passive off"));
		int bs = PanoplyPayload.Client.barSlotOf(pt);
		lines.add(Component.literal(bs >= 0
				? "§bequip bar §f" + (bs + 1) + " §8· drag to move it"
				: "§8drag onto the equip bar to give it a number"));
		List<String> fits = Panoply.slotsFor(s);
		if (fits.size() > 1)
			lines.add(Component.literal("§8also fits · " + String.join(", ", fits.subList(1, fits.size()))));
		lines.add(Component.literal("§8click to hide · right click to draw · shift click to take out"));
		g.renderComponentTooltip(mc.font, lines, mx, my);
	}

	private static ItemStack cursor() {
		Player p = Minecraft.getInstance().player;
		return p == null ? ItemStack.EMPTY : p.containerMenu.getCarried();
	}

	// ==================== CLICK ====================

	/**
	 * Called from OrdealTerminalPainter's click handler when page == 4.
	 * Returns true when the click was ours.
	 */
	public static boolean click(int x, int y, double mx, double my, int button) {
		boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();

		// ---- the preview turntable ----
		int rot = rotAt(x, y, mx, my);
		if (rot >= 0) {
			float step = shift ? 90f : 22.5f;
			if (rot == 0) turn(-step);
			else if (rot == 2) turn(step);
			else { spin = 0f; tilt = 0f; }
			return true;
		}
		if (inPreview(x, y, mx, my)) {
			if (button == 0) {
				spinDrag = true;
				spinLastX = (int) mx;
				spinLastY = (int) my;
			}
			picked = -1;
			return true;
		}

		// ---- the equip bar ----
		int bs = barSlotAt(x, y, mx, my);
		if (bs >= 0) {
			if (button == 1) {
				picked = -1;
				// shift + right anywhere on the bar throws the whole arrangement
				// away and lets it auto-fill from what you carry again
				if (shift) { PanoplyKeys.resetBar(); return true; }
				if (PanoplyPayload.Client.barAt(bs) >= 0) PanoplyKeys.bindBar(bs, -1);
				return true;
			}
			if (picked >= 0) { picked = -1; return true; }  // nothing to place here
			// arm a drag; release decides whether it was a drag or a click
			if (PanoplyPayload.Client.barAt(bs) >= 0) armDrag(-1, bs, mx, my);
			return true;
		}

		// ---- the inventory grid ----
		int inv = invSlotAt(x, y, mx, my);
		if (inv >= 0) {
			if (button == 1) { picked = -1; return true; }   // right click clears
			ItemStack s = invStack(inv);
			// clicking the one you already picked puts it down again
			if (inv == picked) picked = -1;
			else if (!s.isEmpty()) {
				picked = inv;
				// shift click sends it to the first point it legally fits, so the
				// common case is one click instead of two
				if (shift) {
					int dest = firstFit(s);
					if (dest >= 0) { PanoplyKeys.placeFrom(inv, dest); picked = -1; }
				}
			} else picked = -1;
			return true;
		}

		int pt = pointAt(x, y, mx, my);
		if (pt < 0) {
			// clicked the page but not a slot: drop what you were holding
			if (picked >= 0) { picked = -1; return true; }
			return false;
		}
		ItemStack here = PanoplyPayload.Client.at(pt);

		if (button == 1) {                                  // right: draw to hand
			if (picked >= 0) { picked = -1; return true; }
			if (!here.isEmpty()) PanoplyKeys.draw(pt);
			return true;
		}
		// something picked out of the inventory lands here
		if (picked >= 0) {
			PanoplyKeys.placeFrom(picked, pt);
			picked = -1;
			return true;
		}
		if (shift && !here.isEmpty()) { PanoplyKeys.take(pt); return true; }
		if (!cursor().isEmpty())      { PanoplyKeys.place(pt); return true; }
		// a filled point arms a drag. Release without moving = the show/hide
		// toggle, release over a bar slot = bind. Deciding at press time would
		// mean choosing before you know which one the player meant.
		if (!here.isEmpty()) armDrag(pt, -1, mx, my);
		return true;
	}

	private static void armDrag(int pt, int barIdx, double mx, double my) {
		dragPoint = pt;
		dragBar = barIdx;
		dragX = (int) mx;
		dragY = (int) my;
		dragging = false;
	}

	/**
	 * MOUSE UP. This is where a drag lands and where a click that never moved
	 * turns into the show/hide toggle.
	 *
	 * Called from OrdealTerminalPainter's release handler when page == 4.
	 */
	public static void release(int x, int y, double mx, double my, int button) {
		spinDrag = false;
		if (dragPoint < 0 && dragBar < 0) return;
		int fromPoint = dragPoint, fromBar = dragBar;
		boolean moved = dragging;
		dragPoint = -1; dragBar = -1; dragging = false;
		if (button != 0) return;

		int overBar = barSlotAt(x, y, mx, my);

		if (!moved) {
			// never left the slot: a plain click
			if (fromPoint >= 0) PanoplyKeys.toggle(fromPoint);
			return;
		}
		if (fromPoint >= 0) {
			// point -> bar slot binds it. Dropped anywhere else, nothing happens:
			// dragging an item off the page must never take it off your body.
			if (overBar >= 0) PanoplyKeys.bindBar(overBar, fromPoint);
			return;
		}
		// bar -> bar swaps, bar -> anywhere else empties the slot
		if (overBar >= 0 && overBar != fromBar) PanoplyKeys.swapBar(fromBar, overBar);
		else if (overBar < 0) PanoplyKeys.bindBar(fromBar, -1);
	}

	/** First empty legal point for this stack, then the first legal point at all. */
	private static int firstFit(ItemStack s) {
		int fallback = -1;
		for (int pt = 0; pt < Panoply.POINTS; pt++) {
			int slot = Panoply.slotOf(pt);
			if (slot < 0 || !Panoply.fits(s, slot)) continue;
			if (PanoplyPayload.Client.at(pt).isEmpty()) return pt;
			if (fallback < 0) fallback = pt;
		}
		return fallback;
	}

	/** The terminal calls this when the page closes, so nothing stays picked up. */
	public static void closed() {
		picked = -1; hover = -1; hoverInv = -1;
		dragPoint = -1; dragBar = -1; dragging = false;
		spinDrag = false;
	}
}