package net.mcreator.ordeal;

import net.mcreator.ordeal.core.client.OrdealDraw;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The permanent Leo / Che pick, as a real screen rather than an action-bar
 * flash. Two cards, a name and one word each - the plan is explicit that the
 * detail belongs after the choice, not on it, because you are picking at stage
 * 1 and cannot evaluate a stage-3 mechanic yet.
 *
 * Not a pause screen: the first press often happens mid-fight, and freezing the
 * world for it (or leaving you standing still in multiplayer) is worse than the
 * choice being a second slower.
 */
public class IliosStateScreen extends Screen {

	public static int CARD_W = 168;
	public static int CARD_H = 156;
	public static int GAP    = 14;

	public static final String[] IDS   = { "leo", "che" };
	public static final String[] NAMES = { "WINGS OF THE PHOENIX", "ARMOR OF THE SUN" };
	public static final String[] WORDS = { "SPEED", "DURABILITY" };
	public static final String[] PATHS = { "LEO'S PATH", "CHE'S PATH" };
	private static final int[] ACCENT  = { 0xFFF2A63C, 0xFFFF8A5B };

	private int selected = -1;
	private int confirmX, confirmY, confirmW, confirmH;

	public IliosStateScreen() {
		super(Component.literal("Ilios State"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int gridX() { return (this.width - (2 * CARD_W + GAP)) / 2; }
	private int gridY() { return (this.height - CARD_H) / 2 - 2; }

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		OrdealDraw.rect(g, 0, 0, this.width, this.height, 0xD8060A10);

		int gx = gridX();
		int gy = gridY();
		int accent = 0xFFF2A63C;

		int headY = gy - 44;
		String head = "CHOOSE YOUR ILIOS STATE";
		OrdealDraw.text(g, head, (this.width - OrdealDraw.width(head)) / 2, headY, OrdealDraw.INK);
		OrdealDraw.rect(g, (this.width - 190) / 2, headY + 11, 190, 1, OrdealDraw.alpha(accent, 0x60));

		String warn = "permanent for this instance of the talent";
		OrdealDraw.text(g, warn, (this.width - OrdealDraw.width(warn)) / 2, headY + 20, OrdealDraw.INK_DIM);

		for (int i = 0; i < 2; i++) {
			int x = gx + i * (CARD_W + GAP);
			boolean hover = OrdealDraw.inside(mouseX, mouseY, x, gy, CARD_W, CARD_H);
			boolean sel = selected == i;
			int col = ACCENT[i];

			OrdealDraw.rect(g, x, gy, CARD_W, CARD_H, sel ? OrdealDraw.alpha(col, 0x22)
					: hover ? 0x14FFFFFF : 0x99070B12);
			OrdealDraw.card(g, x, gy, CARD_W, CARD_H, sel || hover ? col : OrdealDraw.LOCKED);

			List<String> title = OrdealDraw.wrapPx(NAMES[i], CARD_W - 20, 2);
			int ty = gy + 14;
			for (int l = 0; l < title.size(); l++)
				OrdealDraw.text(g, title.get(l), x + 10, ty + l * 11, sel || hover ? col : OrdealDraw.INK);

			OrdealDraw.rect(g, x + 10, gy + 42, CARD_W - 20, 1, OrdealDraw.alpha(col, 0x50));

			// one word, dead centre - the whole decision at this point
			String word = WORDS[i];
			OrdealDraw.text(g, word, x + (CARD_W - OrdealDraw.width(word)) / 2,
					gy + CARD_H / 2 - 4, sel || hover ? col : OrdealDraw.INK);

			OrdealDraw.text(g, PATHS[i], x + (CARD_W - OrdealDraw.width(PATHS[i])) / 2,
					gy + CARD_H - 20, OrdealDraw.alpha(col, 0xA0));
		}

		confirmW = 150;
		confirmH = 18;
		confirmX = (this.width - confirmW) / 2;
		confirmY = gy + CARD_H + 14;

		if (selected >= 0) {
			boolean hover = OrdealDraw.inside(mouseX, mouseY, confirmX, confirmY, confirmW, confirmH);
			int col = ACCENT[selected];
			OrdealDraw.rect(g, confirmX, confirmY, confirmW, confirmH,
					hover ? OrdealDraw.alpha(col, 0x33) : OrdealDraw.alpha(col, 0x18));
			OrdealDraw.outline(g, confirmX, confirmY, confirmW, confirmH, col);
			String label = "AWAKEN · " + WORDS[selected];
			OrdealDraw.text(g, label, confirmX + (confirmW - OrdealDraw.width(label)) / 2,
					confirmY + 5, col);
		} else {
			String hint = "pick one to see the confirm";
			OrdealDraw.text(g, hint, (this.width - OrdealDraw.width(hint)) / 2,
					confirmY + 5, OrdealDraw.LOCKED);
		}

		String esc = "ESC to decide later · pressing the state key asks again";
		OrdealDraw.text(g, esc, (this.width - OrdealDraw.width(esc)) / 2,
				confirmY + confirmH + 8, OrdealDraw.LOCKED);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			int gx = gridX();
			int gy = gridY();
			for (int i = 0; i < 2; i++) {
				int x = gx + i * (CARD_W + GAP);
				if (OrdealDraw.inside(mx, my, x, gy, CARD_W, CARD_H)) {
					selected = selected == i ? -1 : i;
					return true;
				}
			}
			if (selected >= 0 && OrdealDraw.inside(mx, my, confirmX, confirmY, confirmW, confirmH)) {
				IliosStatePayload.choose(IDS[selected]);
				Minecraft.getInstance().setScreen(null);
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}
}