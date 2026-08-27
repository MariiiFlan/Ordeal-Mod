package net.mcreator.ordeal.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class OrdealDraw {

	private OrdealDraw() {}

	public static final int CYAN       = 0xFF7ED8F5;
	public static final int CYAN_DIM   = 0xFF4B7D92;
	public static final int CYAN_FAINT = 0x387ED8F5;
	public static final int INK        = 0xFFEAF7FF;
	public static final int INK_DIM    = 0xFF41677A;
	public static final int GROUND     = 0xF2070B12;
	public static final int SURFACE    = 0xFF0B1119;
	public static final int LOCKED     = 0xFF2D4A59;
	public static final int GREEN      = 0xFF5FE3A0;
	public static final int ILIOS      = 0xFFF2A63C;

	public static int alpha(int argb, int a) {
		return (argb & 0x00FFFFFF) | ((a & 0xFF) << 24);
	}

	public static void rect(GuiGraphics g, int x, int y, int w, int h, int argb) {
		if (w <= 0 || h <= 0) return;
		g.fill(x, y, x + w, y + h, argb);
	}

	public static void outline(GuiGraphics g, int x, int y, int w, int h, int argb) {
		rect(g, x, y, w, 1, argb);
		rect(g, x, y + h - 1, w, 1, argb);
		rect(g, x, y, 1, h, argb);
		rect(g, x + w - 1, y, 1, h, argb);
	}

	public static void brackets(GuiGraphics g, int x, int y, int w, int h, int len, int argb) {
		rect(g, x, y, len, 1, argb);                   rect(g, x, y, 1, len, argb);
		rect(g, x + w - len, y, len, 1, argb);         rect(g, x + w - 1, y, 1, len, argb);
		rect(g, x, y + h - 1, len, 1, argb);           rect(g, x, y + h - len, 1, len, argb);
		rect(g, x + w - len, y + h - 1, len, 1, argb); rect(g, x + w - 1, y + h - len, 1, len, argb);
	}

	/** Diagonal hatch for empty panel areas. */
	public static void hatch(GuiGraphics g, int x, int y, int w, int h, int argb, int spacing) {
		for (int i = -h; i < w; i += spacing) {
			for (int j = 0; j < h; j++) {
				int px = x + i + j;
				if (px >= x && px < x + w) rect(g, px, y + j, 1, 1, argb);
			}
		}
	}

	public static void text(GuiGraphics g, String s, int x, int y, int argb) {
		g.drawString(Minecraft.getInstance().font, s, x, y, argb, false);
	}

	public static void textRight(GuiGraphics g, String s, int rightX, int y, int argb) {
		g.drawString(Minecraft.getInstance().font, s, rightX - width(s), y, argb, false);
	}

	public static int width(String s) {
		return Minecraft.getInstance().font.width(s);
	}

	/** Bracketed label used for type tags. Returns width consumed. */
	public static int chip(GuiGraphics g, String label, int x, int y, int argb) {
		String s = "‹" + label + "›";
		text(g, s, x, y, argb);
		return width(s) + 6;
	}

	public static final int CHIP_H = 11;

	/**
	 * Type tag drawn as a tinted plate rather than plain text, so the talent
	 * types read at a glance. Returns the width consumed including the gap.
	 */
	public static int chipFilled(GuiGraphics g, String label, int x, int y, int argb) {
		int w = width(label) + 8;
		rect(g, x, y, w, CHIP_H, alpha(argb, 0x22));
		outline(g, x, y, w, CHIP_H, alpha(argb, 0x99));
		rect(g, x, y, 1, CHIP_H, argb);
		text(g, label, x + 4, y + 2, argb);
		return w + 4;
	}

	/**
	 * Greedy word wrap to a pixel width. The last line is cut with an ellipsis
	 * if the text runs past maxLines, so a long description can never push the
	 * panel out of shape.
	 */
	public static List<String> wrapPx(String text, int maxPx, int maxLines) {
		List<String> out = new java.util.ArrayList<>();
		if (text == null || text.isEmpty() || maxPx <= 0) return out;
		StringBuilder line = new StringBuilder();
		for (String word : text.split("\\s+")) {
			if (word.isEmpty()) continue;
			String probe = line.length() == 0 ? word : line + " " + word;
			if (width(probe) <= maxPx) { line.setLength(0); line.append(probe); continue; }
			if (line.length() > 0) out.add(line.toString());
			line.setLength(0);
			line.append(word);
			if (out.size() == maxLines) break;
		}
		if (out.size() < maxLines && line.length() > 0) out.add(line.toString());
		while (out.size() > maxLines) out.remove(out.size() - 1);
		if (out.size() == maxLines) {
			// anything left over? mark the tail so it does not read as a full stop
			int used = 0;
			for (String l : out) used += l.split("\\s+").length;
			if (used < text.trim().split("\\s+").length) {
				String last = out.get(maxLines - 1);
				while (!last.isEmpty() && width(last + "...") > maxPx)
					last = last.substring(0, last.length() - 1);
				out.set(maxLines - 1, last + "...");
			}
		}
		return out;
	}

	public static void tooltip(GuiGraphics g, List<Component> lines, int mouseX, int mouseY) {
		g.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
	}

	public static void bar(GuiGraphics g, int x, int y, int w, int h,
	                       double value, double max, int fill, int track) {
		rect(g, x, y, w, h, track);
		rect(g, x, y, span(w, value, max), h, fill);
		outline(g, x, y, w, h, CYAN_FAINT);
	}

	/**
	 * The TALENT STRENGTH bar. Full width equals the player's chi limit, so the bar itself
	 * grows over the game. One entry per equipped talent; the remainder reads as unspent.
	 */
	public static void segmentedBar(GuiGraphics g, int x, int y, int w, int h,
	                                double[] values, int[] colours, double max, int track) {
		rect(g, x, y, w, h, track);
		int cursor = 0;
		for (int i = 0; i < values.length && i < colours.length; i++) {
			int seg = span(w, values[i], max);
			if (cursor + seg > w) seg = w - cursor;
			if (seg <= 0) continue;
			rect(g, x + cursor, y, seg, h, colours[i]);
			if (cursor > 0) rect(g, x + cursor, y, 1, h, GROUND);
			cursor += seg;
		}
		outline(g, x, y, w, h, CYAN_FAINT);
	}

	/** Marks where the natural cap ends and blood-bought space begins. */
	public static void capMarker(GuiGraphics g, int x, int y, int w, int h,
	                             double capValue, double max) {
		if (max <= capValue) return;
		rect(g, x + span(w, capValue, max), y - 2, 1, h + 4, alpha(CYAN, 0xCC));
	}

	public static void pips(GuiGraphics g, int x, int y, int count, int filled,
	                        int size, int gap, int on, int off) {
		for (int i = 0; i < count; i++) {
			int px = x + i * (size + gap);
			if (i < filled) rect(g, px, y, size, size, on);
			else outline(g, px, y, size, size, off);
		}
	}


	/** Segmented meter - reads as data rather than a progress bar. */
	public static void cells(GuiGraphics g, int x, int y, int w, int h,
	                         int count, double value, double max, int on, int off) {
		int cw = Math.max(2, (w - (count - 1)) / count);
		int filled = max <= 0 ? 0 : (int) Math.round(count * Math.max(0, Math.min(1, value / max)));
		for (int i = 0; i < count; i++)
			rect(g, x + i * (cw + 1), y, cw, h, i < filled ? on : off);
	}

	/** Framed sub-panel used for the model box and stat cards. */
	public static void card(GuiGraphics g, int x, int y, int w, int h, int border) {
		rect(g, x, y, w, h, 0x1E000000);
		outline(g, x, y, w, h, border);
	}

	private static int span(int w, double value, double max) {
		if (max <= 0) return 0;
		double pct = Math.max(0, Math.min(1, value / max));
		return (int) Math.round(w * pct);
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/**
	 * Every ability icon is authored on ONE canvas size and scaled to whatever
	 * slot it lands in. The plain blit() overload samples pixel-for-pixel, so a
	 * 32px icon drawn into a 12px row showed only its top-left corner — this
	 * overload separates destination size from source size and scales properly.
	 */
	public static final int ICON_TEX = 32;

	public static void icon(GuiGraphics g, ResourceLocation tex, int x, int y, int size) {
		if (tex == null || size <= 0) return;
		g.blit(tex, x, y, size, size, 0f, 0f, ICON_TEX, ICON_TEX, ICON_TEX, ICON_TEX);
	}
}