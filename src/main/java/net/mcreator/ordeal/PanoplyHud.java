package net.mcreator.ordeal;

import net.mcreator.ordeal.core.client.OrdealDraw;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * THE EQUIP BAR - a second hotbar to the LEFT of the vanilla one.
 *
 * SIX SLOTS, and you pick what is in them: drag a point onto a slot in the
 * panoply page. Hold your Panoply Equip key and press a number - or scroll -
 * to draw that one; the drawn entry gets the green frame.
 *
 * Until you have dragged anything it auto-fills with whatever you are
 * carrying, so it works the moment your first item goes in. See
 * Panoply.resolveBar.
 *
 * It hides itself entirely when the bar is empty, so a player who never
 * touches the system never sees it.
 */
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class PanoplyHud {

	private PanoplyHud() {}

	public static boolean ENABLED = true;

	/** Slot box size and the gap between boxes. Vanilla's are 20 wide. */
	public static int BOX = 20;
	public static int GAP = 1;

	/** Distance from the left edge of the vanilla hotbar. */
	public static int OFFSET = 6;

	/** Lifted this far off the bottom, matching the hotbar's own inset. */
	public static int BOTTOM = 22;

	/** Fades out this many ticks after the last change. 0 = always visible. */
	public static int HOLD_TICKS = 0;

	@SubscribeEvent
	public static void onRenderHotbar(RenderGuiLayerEvent.Post event) {
		if (!ENABLED) return;
		if (!net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR.equals(event.getName())) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) return;
		if (mc.player.isSpectator()) return;

		// the bar you bound, holes already closed up by resolveBar
		int[] bar = PanoplyPayload.Client.BAR;
		int[] pts = new int[Panoply.BAR_SLOTS];
		int n = 0;
		for (int i = 0; i < bar.length; i++) if (bar[i] >= 0) pts[n++] = bar[i];
		int drawn = PanoplyPayload.Client.DRAWN;
		if (n == 0) return;

		GuiGraphics g = event.getGuiGraphics();
		int sw = g.guiWidth(), sh = g.guiHeight();

		// the vanilla hotbar is 182 wide, centred - sit just left of it
		int hotbarLeft = sw / 2 - 91;
		int total = n * BOX + (n - 1) * GAP;
		int x = hotbarLeft - OFFSET - total;
		int y = sh - BOTTOM;
		if (x < 2) x = 2;   // never run off the screen on a narrow GUI scale

		boolean holding = PanoplyKeys.equipHeld();

		for (int i = 0; i < n; i++) {
			int pt = pts[i];
			int bx = x + i * (BOX + GAP);
			ItemStack s = PanoplyPayload.Client.at(pt);
			boolean isDrawn = (pt == drawn);
			boolean live = PanoplyPayload.Client.isActive(pt);

			OrdealDraw.rect(g, bx, y, BOX, BOX, 0x99070B12);
			OrdealDraw.outline(g, bx, y, BOX, BOX,
					isDrawn ? OrdealDraw.GREEN
					: holding ? OrdealDraw.CYAN
					: live ? OrdealDraw.CYAN_FAINT
					: OrdealDraw.LOCKED);

			g.renderItem(s, bx + 2, y + 2);
			g.renderItemDecorations(mc.font, s, bx + 2, y + 2);

			// the number you press while the equip key is held
			if (holding || isDrawn) {
				String num = String.valueOf(i + 1);
				OrdealDraw.text(g, num, bx + 2, y - 8,
						isDrawn ? OrdealDraw.GREEN : OrdealDraw.CYAN);
			}
		}

		// no label over the bar. The numbers above each box already say what to
		// press, and a sentence there sat on top of them.
	}

	/** Screen-order index (what the HUD numbers) -> flat point. -1 when none. */
	public static int pointForBarIndex(int barIndex) {
		int[] bar = PanoplyPayload.Client.BAR;
		int n = 0;
		for (int i = 0; i < bar.length; i++) {
			if (bar[i] < 0) continue;
			if (n == barIndex) return bar[i];
			n++;
		}
		return -1;
	}

	/** How many entries the bar actually shows. The scroll wheel wraps on this. */
	public static int barCount() {
		int n = 0;
		for (int v : PanoplyPayload.Client.BAR) if (v >= 0) n++;
		return n;
	}

	/** Screen-order index of the point in hand, or -1. */
	public static int barIndexOfDrawn() {
		int drawn = PanoplyPayload.Client.DRAWN;
		if (drawn < 0) return -1;
		int[] bar = PanoplyPayload.Client.BAR;
		int n = 0;
		for (int i = 0; i < bar.length; i++) {
			if (bar[i] < 0) continue;
			if (bar[i] == drawn) return n;
			n++;
		}
		return -1;
	}
}