package net.mcreator.ordeal;

import net.mcreator.ordeal.core.client.OrdealDraw;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * THE PANOPLY SCREEN - 520 x 306, the same panel as the rest of the terminal.
 *
 * HOW IT BEHAVES, matching the ability menu:
 *   left click a point with something on the cursor  -> place it there
 *   left click a filled point with an empty cursor   -> wear / just carry it
 *   shift + left click a filled point                -> take it out to your bag
 *   right click a filled point                       -> draw it to your hand
 *
 * ACTIVE vs CARRIED is the thing to understand. Both hold the item. ACTIVE
 * renders it on the model and grants its passive; CARRIED is storage - the
 * spare blade on your hip that is doing nothing until you wear or draw it.
 * The frame tells you which: lit is active, dim is carried.
 *
 * The screen NEVER moves an item itself. Every click sends a request and the
 * server does the moving, so what you see is always what the server agreed to.
 */
public class PanoplyScreen extends Screen {

	// ---- panel geometry, same constants as OrdealTerminalPainter ----
	private static final int PANEL_W = 520, PANEL_H = 306;
	private static final int HEADER_H = 24, PAD = 14;
	private static final int SLOT = 30, GAP = 5;
	private static final int MODEL_X = PAD, MODEL_Y = 32, MODEL_W = 150, MODEL_H = 184;
	private static final int COL_A = 176, COL_B = 344;
	private static final int GROUP_PITCH = 48;
	private static final int BAR_Y = 240, BAND_Y = 222;

	/** Rarity owns the frame. Cyan stays terminal chrome so empty never reads as a tier. */
	private static final int R_COMMON = 0xFF8FA8B6, R_UNCOMMON = 0xFF5FE3A0,
			R_RARE = 0xFF4FA8FF, R_EPIC = 0xFF9B6BFF, R_LEGENDARY = 0xFFFFB020;

	private int left, top;
	private int hover = -1;

	public PanoplyScreen() { super(Component.literal("Panoply")); }

	public static void open() { Minecraft.getInstance().setScreen(new PanoplyScreen()); }

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	protected void init() {
		left = (this.width - PANEL_W) / 2;
		top = (this.height - PANEL_H) / 2;
	}

	// ==================== LAYOUT ====================

	/** Screen position of a flat point, or null when it is off-panel. */
	private int[] posOf(int pt) {
		int slot = Panoply.slotOf(pt);
		if (slot < 0) return null;
		int entry = pt - Panoply.BASE[slot];
		int col = (slot % 2 == 0) ? COL_A : COL_B;   // HEAD/FACE/SHOULDERS/BACK left
		int row = slot / 2;
		int x = left + col + entry * (SLOT + GAP);
		int y = top + MODEL_Y + row * GROUP_PITCH + 11;
		return new int[] { x, y };
	}

	private int pointAt(double mx, double my) {
		for (int pt = 0; pt < Panoply.POINTS; pt++) {
			int[] p = posOf(pt);
			if (p == null) continue;
			if (mx >= p[0] && mx < p[0] + SLOT && my >= p[1] && my < p[1] + SLOT) return pt;
		}
		return -1;
	}

	// ==================== RENDER ====================

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		OrdealDraw.rect(g, 0, 0, this.width, this.height, 0xB0000000);
		hover = pointAt(mouseX, mouseY);

		OrdealDraw.rect(g, left, top, PANEL_W, PANEL_H, OrdealDraw.GROUND);
		OrdealDraw.outline(g, left, top, PANEL_W, PANEL_H, OrdealDraw.CYAN_FAINT);
		OrdealDraw.brackets(g, left, top, PANEL_W, PANEL_H, 12, OrdealDraw.CYAN);

		// header
		OrdealDraw.text(g, "ORDEAL TERMINAL", left + PAD, top + 8, OrdealDraw.INK);
		OrdealDraw.rect(g, left, top + HEADER_H, PANEL_W, 1, 0xFF1E3040);
		tab(g, left + PAD, top + 4, 62, "CHARACTER", false);
		tab(g, left + PAD + 66, top + 4, 54, "TALENTS", false);
		tab(g, left + PAD + 124, top + 4, 30, "CHI", false);
		tab(g, left + PAD + 158, top + 4, 56, "PANOPLY", true);

		int carried = PanoplyPayload.Client.carried();
		int active = PanoplyPayload.Client.activeCount();
		OrdealDraw.textRight(g, carried + "/" + Panoply.POINTS + " CARRIED  " + active + " ACTIVE",
				left + PANEL_W - PAD, top + 9, OrdealDraw.CYAN_DIM);

		// model preview
		OrdealDraw.rect(g, left + MODEL_X, top + MODEL_Y, MODEL_W, MODEL_H, 0x99060A10);
		OrdealDraw.outline(g, left + MODEL_X, top + MODEL_Y, MODEL_W, MODEL_H, 0xFF1E3040);
		OrdealDraw.hatch(g, left + MODEL_X + 1, top + MODEL_Y + 1, MODEL_W - 2, MODEL_H - 2,
				0x0AFFFFFF, 6);
		OrdealDraw.text(g, "PREVIEW", left + MODEL_X + 6, top + MODEL_Y + 5, OrdealDraw.INK_DIM);
		if (this.minecraft != null && this.minecraft.player != null) {
			net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(
					g, left + MODEL_X + 6, top + MODEL_Y + 20,
					left + MODEL_X + MODEL_W - 6, top + MODEL_Y + MODEL_H - 26, 42, 0.0625f,
					mouseX, mouseY, this.minecraft.player);
		}
		int drawn = PanoplyPayload.Client.DRAWN;
		if (drawn >= 0) {
			ItemStack held = this.minecraft != null && this.minecraft.player != null
					? this.minecraft.player.getMainHandItem() : ItemStack.EMPTY;
			OrdealDraw.text(g, "DRAWN " + held.getHoverName().getString(),
					left + MODEL_X + 6, top + MODEL_Y + MODEL_H - 14, OrdealDraw.GREEN);
		}

		// slot groups
		for (int slot = 0; slot < Panoply.SLOTS.length; slot++) {
			int col = (slot % 2 == 0) ? COL_A : COL_B;
			int row = slot / 2;
			int gx = left + col, gy = top + MODEL_Y + row * GROUP_PITCH;
			OrdealDraw.text(g, Panoply.LABEL[slot], gx, gy, OrdealDraw.INK);
			OrdealDraw.textRight(g, String.valueOf(Panoply.CAP[slot]),
					gx + 134, gy, OrdealDraw.LOCKED);
			for (int e = 0; e < Panoply.CAP[slot]; e++) drawPoint(g, Panoply.BASE[slot] + e);
		}

		// draw bar band
		OrdealDraw.rect(g, left + PAD, top + BAND_Y, PANEL_W - PAD * 2, 1, 0xFF16232F);
		OrdealDraw.text(g, "DRAW BAR", left + PAD, top + BAND_Y + 7, OrdealDraw.GREEN);
		OrdealDraw.text(g, "HOLD THE EQUIP KEY IN THE WORLD", left + PAD + 60, top + BAND_Y + 7,
				OrdealDraw.LOCKED);
		int bx = left + PAD, shown = 0;
		for (int pt = 0; pt < Panoply.POINTS && shown < 6; pt++) {
			ItemStack s = PanoplyPayload.Client.at(pt);
			if (s.isEmpty()) continue;
			int x = bx + shown * (SLOT + GAP), y = top + BAR_Y;
			OrdealDraw.rect(g, x, y, SLOT, SLOT, 0x99070B12);
			OrdealDraw.outline(g, x, y, SLOT, SLOT,
					pt == drawn ? OrdealDraw.GREEN : OrdealDraw.CYAN_FAINT);
			g.renderItem(s, x + 7, y + 7);
			OrdealDraw.text(g, String.valueOf(shown + 1), x + 2, y + 1, OrdealDraw.CYAN);
			shown++;
		}

		OrdealDraw.text(g, "CLICK PLACE  ·  CLICK AGAIN WEAR  ·  SHIFT TAKE OUT  ·  RIGHT DRAW",
				left + PAD, top + 292, OrdealDraw.INK_DIM);

		super.render(g, mouseX, mouseY, partialTick);
		if (hover >= 0) tooltip(g, mouseX, mouseY, hover);
		renderCarried(g, mouseX, mouseY);
	}

	private void tab(GuiGraphics g, int x, int y, int w, String label, boolean on) {
		OrdealDraw.rect(g, x, y, w, 16, on ? 0x227ED8F5 : 0x0E7ED8F5);
		OrdealDraw.outline(g, x, y, w, 16, on ? OrdealDraw.CYAN : 0xFF24384A);
		OrdealDraw.text(g, label, x + (w - OrdealDraw.width(label)) / 2, y + 4,
				on ? OrdealDraw.INK : OrdealDraw.LOCKED);
	}

	private void drawPoint(GuiGraphics g, int pt) {
		int[] p = posOf(pt);
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
			g.renderItem(s, p[0] + 7, p[1] + 7);
			g.renderItemDecorations(this.font, s, p[0] + 7, p[1] + 7);
			// a small pip in the corner: green = active, hollow = carried only
			OrdealDraw.rect(g, p[0] + SLOT - 5, p[1] + 2, 3, 3,
					live ? OrdealDraw.GREEN : 0xFF2D4A59);
		} else {
			int e = pt - Panoply.BASE[Panoply.slotOf(pt)];
			OrdealDraw.text(g, String.valueOf(e + 1), p[0] + SLOT - 7, p[1] + SLOT - 9,
					OrdealDraw.LOCKED);
		}
	}

	/**
	 * Frame colour by rarity. Reads the item's name formatting, which is what
	 * MCreator sets on a custom item - swap this for a data component when you
	 * want rarity to be its own field.
	 */
	private int rarity(ItemStack s) {
		if (s.isEmpty()) return OrdealDraw.LOCKED;
		return switch (s.getRarity()) {
			case UNCOMMON -> R_UNCOMMON;
			case RARE -> R_RARE;
			case EPIC -> R_EPIC;
			default -> s.isEnchanted() ? R_LEGENDARY : R_COMMON;
		};
	}

	private void tooltip(GuiGraphics g, int mx, int my, int pt) {
		ItemStack s = PanoplyPayload.Client.at(pt);
		int slot = Panoply.slotOf(pt);
		if (s.isEmpty()) {
			ItemStack cursor = carried();
			String line = cursor.isEmpty()
					? "§8empty · " + Panoply.LABEL[slot].toLowerCase(Locale.ROOT)
					: Panoply.fits(cursor, slot)
						? "§aplace " + cursor.getHoverName().getString() + " here"
						: "§c" + cursor.getHoverName().getString() + " does not go here";
			g.renderTooltip(this.font, Component.literal(line), mx, my);
			return;
		}
		List<Component> lines = new java.util.ArrayList<>();
		lines.add(s.getHoverName());
		lines.add(Component.literal(PanoplyPayload.Client.isActive(pt)
				? "§aACTIVE §8· worn, passive live" : "§8CARRIED · storage only"));
		lines.add(Component.literal("§bright click to draw"));
		List<String> fits = Panoply.slotsFor(s);
		if (fits.size() > 1) lines.add(Component.literal("§8also fits · "
				+ String.join(", ", fits.subList(1, fits.size()))));
		lines.add(Component.literal("§8shift click to take out"));
		g.renderComponentTooltip(this.font, lines, mx, my);
	}

	private ItemStack carried() {
		return this.minecraft == null || this.minecraft.player == null
				? ItemStack.EMPTY : this.minecraft.player.containerMenu.getCarried();
	}

	private void renderCarried(GuiGraphics g, int mx, int my) {
		ItemStack c = carried();
		if (c.isEmpty()) return;
		g.renderItem(c, mx - 8, my - 8);
		g.renderItemDecorations(this.font, c, mx - 8, my - 8);
	}

	// ==================== INPUT ====================

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		int pt = pointAt(mx, my);
		if (pt >= 0) {
			ItemStack here = PanoplyPayload.Client.at(pt);
			if (button == 1) {                              // right click: draw to hand
				if (!here.isEmpty()) PanoplyKeys.draw(pt);
				return true;
			}
			if (hasShiftDown() && !here.isEmpty()) { PanoplyKeys.take(pt); return true; }
			if (!carried().isEmpty()) { PanoplyKeys.place(pt); return true; }
			if (!here.isEmpty()) { PanoplyKeys.toggle(pt); return true; }
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (this.minecraft != null && this.minecraft.options.keyInventory.matches(key, scan)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}
}