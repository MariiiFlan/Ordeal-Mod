package net.mcreator.ordeal;

import net.mcreator.ordeal.core.client.OrdealDraw;
import net.mcreator.ordeal.core.client.OrdealTalents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public class EnhancementScreen extends Screen {

	public static int CARD_W = 148;
	public static int CARD_H = 168;
	public static int GAP    = 10;

	private static final int[] ACCENT = { 0xFF7ED8F5, 0xFFF2A63C, 0xFFFF6B6B };

	private final String talentId;
	private final double strength;
	private int selected = -1;
	private int confirmX, confirmY, confirmW, confirmH;

	public EnhancementScreen(String talentId, double strength) {
		super(Component.literal("Bonding"));
		this.talentId = talentId == null ? "" : talentId;
		this.strength = strength;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public static void open(String talentId, double strength) {
		Minecraft.getInstance().setScreen(new EnhancementScreen(talentId, strength));
	}

	private String talentName() {
		OrdealTalents.Talent t = OrdealTalents.get(talentId);
		return t == null || t.name == null || t.name.isEmpty()
				? talentId.toUpperCase(Locale.ROOT) : t.name;
	}

	private int talentAccent() {
		OrdealTalents.Talent t = OrdealTalents.get(talentId);
		return t == null ? OrdealDraw.CYAN : t.accent;
	}

	private int gridX() {
		return (this.width - (3 * CARD_W + 2 * GAP)) / 2;
	}

	private int gridY() {
		return (this.height - CARD_H) / 2 - 4;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		OrdealDraw.rect(g, 0, 0, this.width, this.height, 0xD8060A10);

		int gx = gridX();
		int gy = gridY();
		int accent = talentAccent();

		int headY = gy - 48;
		String head = "CHOOSE YOUR ENHANCEMENT";
		OrdealDraw.text(g, head, (this.width - OrdealDraw.width(head)) / 2, headY, OrdealDraw.INK);
		OrdealDraw.rect(g, (this.width - 180) / 2, headY + 11, 180, 1, OrdealDraw.alpha(accent, 0x60));

		String sub = talentName() + "  ·  STRENGTH " + (int) strength;
		OrdealDraw.text(g, sub, (this.width - OrdealDraw.width(sub)) / 2, headY + 17, accent);

		String warn = "one only, and it cannot be changed";
		OrdealDraw.text(g, warn, (this.width - OrdealDraw.width(warn)) / 2, headY + 29, OrdealDraw.INK_DIM);

		for (int i = 0; i < 3; i++) {
			int x = gx + i * (CARD_W + GAP);
			boolean hover = OrdealDraw.inside(mouseX, mouseY, x, gy, CARD_W, CARD_H);
			boolean sel = selected == i;
			int col = ACCENT[i];

			OrdealDraw.rect(g, x, gy, CARD_W, CARD_H, sel ? OrdealDraw.alpha(col, 0x22)
					: hover ? 0x14FFFFFF : 0x99070B12);
			OrdealDraw.card(g, x, gy, CARD_W, CARD_H, sel || hover ? col : OrdealDraw.LOCKED);

			int ty = gy + 12;
			OrdealDraw.text(g, Enhancements.NAMES[i], x + 10, ty, sel || hover ? col : OrdealDraw.INK);
			OrdealDraw.rect(g, x + 10, ty + 11, CARD_W - 20, 1, OrdealDraw.alpha(col, 0x50));

			OrdealDraw.text(g, Enhancements.HEADLINES[i], x + 10, ty + 19, OrdealDraw.INK_DIM);

			List<String> body = OrdealDraw.wrapPx(Enhancements.BLURBS[i], CARD_W - 20, 9);
			for (int l = 0; l < body.size(); l++)
				OrdealDraw.text(g, body.get(l), x + 10, ty + 34 + l * 10, OrdealDraw.INK);

			List<String> cost = OrdealDraw.wrapPx(Enhancements.COSTS[i], CARD_W - 20, 2);
			for (int l = 0; l < cost.size(); l++)
				OrdealDraw.text(g, cost.get(l), x + 10, gy + CARD_H - 22 + l * 9,
						OrdealDraw.alpha(col, 0xB0));
		}

		confirmW = 132;
		confirmH = 18;
		confirmX = (this.width - confirmW) / 2;
		confirmY = gy + CARD_H + 14;

		if (selected >= 0) {
			boolean hover = OrdealDraw.inside(mouseX, mouseY, confirmX, confirmY, confirmW, confirmH);
			int col = ACCENT[selected];
			OrdealDraw.rect(g, confirmX, confirmY, confirmW, confirmH,
					hover ? OrdealDraw.alpha(col, 0x33) : OrdealDraw.alpha(col, 0x18));
			OrdealDraw.outline(g, confirmX, confirmY, confirmW, confirmH, col);
			String label = "ENHANCEMENT: " + Enhancements.NAMES[selected];
			OrdealDraw.text(g, label, confirmX + (confirmW - OrdealDraw.width(label)) / 2,
					confirmY + 5, col);
		} else {
			String hint = "pick one to see the confirm";
			OrdealDraw.text(g, hint, (this.width - OrdealDraw.width(hint)) / 2,
					confirmY + 5, OrdealDraw.LOCKED);
		}

		String esc = "ESC to decide later";
		OrdealDraw.text(g, esc, (this.width - OrdealDraw.width(esc)) / 2,
				confirmY + confirmH + 8, OrdealDraw.LOCKED);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			int gx = gridX();
			int gy = gridY();
			for (int i = 0; i < 3; i++) {
				int x = gx + i * (CARD_W + GAP);
				if (OrdealDraw.inside(mx, my, x, gy, CARD_W, CARD_H)) {
					selected = selected == i ? -1 : i;
					return true;
				}
			}
			if (selected >= 0 && OrdealDraw.inside(mx, my, confirmX, confirmY, confirmW, confirmH)) {
				EnhancementPayload.choose("", Enhancements.ALL[selected]);
				EnhancementPrompt.snooze();
				Minecraft.getInstance().setScreen(null);
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public void onClose() {
		EnhancementPrompt.snooze();
		super.onClose();
	}
}