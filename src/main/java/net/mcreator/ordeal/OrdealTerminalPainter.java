package net.mcreator.ordeal.core.client;

import net.mcreator.ordeal.network.OrdealModVariables;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.mcreator.ordeal.core.OrdealActionMessage;
import net.mcreator.ordeal.core.OrdealCombat;
import net.mcreator.ordeal.core.OrdealStats;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public class OrdealTerminalPainter {

	private static final String SCREEN_CLASS = "KodeFieldTerminalGUIScreen";

	private static final int PANEL_W = 520, PANEL_H = 306;
	private static final int HEADER_H = 24, PAD = 14;
	private static final int CONTENT_X = PAD, CONTENT_W = PANEL_W - PAD * 2;
	private static final int FOOT_Y = 292;

	private static final int TAB_CHAR_W = 62, TAB_TAL_W = 54, TAB_CHI_W = 30, TOPTAB_H = 16, TOPTAB_Y = 4;
	private static final String[] TOPTABS  = { "CHARACTER", "TALENTS", "CHI", "TRAITS", "PANOPLY" };
	private static final int      TOPTAB_GAP = 6;
	/** 2px either side of the label, so the underline is not flush to the glyphs. */
	private static final int      TOPTAB_PAD = 4;

	/**
	 * Each tab is as wide as its own text. Fixed widths meant CHI sat in a 30px
	 * box and PANOPLY in a 56px one, so the visible gaps between labels were all
	 * different lengths - which is what made the strip look badly spaced.
	 */
	private static int tabW(int i) {
		return OrdealDraw.width(TOPTABS[i]) + TOPTAB_PAD;
	}

	private static int tabsTotal() {
		int total = -TOPTAB_GAP;
		for (int i = 0; i < TOPTABS.length; i++) total += tabW(i) + TOPTAB_GAP;
		return total;
	}

	/**
	 * Left edge of the tab strip.
	 *
	 * Centred on the PANEL used to be fine with four tabs. PANOPLY made the
	 * strip 280 wide, and centring that put its left edge at x+120 - straight
	 * through "K.O.D.E > FIELD TERMINAL", which ends around x+148.
	 *
	 * So it centres on the space that is LEFT, between the title and the right
	 * pad, instead of on the whole panel. Same balanced look, just measured
	 * from where the header actually ends. If a sixth tab ever makes the strip
	 * wider than that space, it pins to the title and runs off the right rather
	 * than back over the text.
	 */
	private static int topTabsX(int x) {
		int total = tabsTotal();
		// centred on the PANEL, which is where the eye expects it
		int centred = x + (PANEL_W - total) / 2;
		// ...unless that would run into the title, which is why it was not
		// centred before. Auto-sized tabs are narrow enough that this floor
		// almost never bites; when it does it is by a few pixels.
		int floor = x + PAD + OrdealDraw.width("K.O.D.E > FIELD TERMINAL") + 8;
		return Math.max(centred, floor);
	}

	private static final int MODEL_X = PAD, MODEL_Y = 32, MODEL_W = 130, MODEL_H = 108;
	private static final int ID_Y = 146, ID_H = 96;
	private static final int MID_X = 152, MID_W = 206;
	private static final int CARD_Y = 32, CARD_H = 36;
	private static final int ATTR_Y = 76, ROW_Y = 90, ROW_H = 20;
	private static final int PLUS_X = MID_X + MID_W - 20;
	private static final int TBTN_Y = 236, TBTN_H = 22;
	private static final int CON_X = 366, CON_W = PANEL_W - 366 - PAD;
	private static final int CON_TRACK_Y = 92, CON_PRESET_Y = 122, CON_APPLY_Y = 240;
	private static final int AMBER = 0xFFFFB020, AMBER_DIM = 0xFFC98A52;

	private static final int TTABS_Y = 32, TTAB_H = 14;
	private static final int TNAME_Y = 52, TCHIPS_Y = 64, BIGNUM_Y = 46;
	/** Talent flavour text sits between the type chips and the strength bar. */
	private static final int TDESC_Y = 79, TDESC_LINES = 2, TDESC_LH = 10;
	private static final int STR_Y = 104, BAR_Y = 114, BAR_H = 10, LEGEND_Y = 128;
	private static final int BTN_W = 84, BTN_H = 13;
	private static final int SECT_Y = 144;
	private static final int LIST_X = CONTENT_X, LIST_W = 296;
	private static final int LIST_Y = 156, LROW_H = 16, ROWS_VISIBLE = 8;

	public static String ENHANCEMENT_TAB = "enhancement";
	private static final int LOAD_X = CONTENT_X + LIST_W + 10;
	private static final int LOAD_W = CONTENT_W - LIST_W - 10;
	private static final int SLOT = 30, SLOT_GAP = 5, SLOTS_Y = 156;
	private static final int ICON = 14, SCROLLBAR_W = 3;

	private static final int BLOOD_MAX = 5;

	private static final String[] STAT_LABEL = {
			"STRENGTH", "DURABILITY", "AGILITY", "HEALTH", "CHI", "CHI CONTROL", "PERCEPTION" };
	private static final String[] STAT_DESC = {
			"melee damage", "damage reduction", "move speed", "max HP",
			"stamina pool", "chi cost down", "sense range" };
	private static final String[] STAT_CMD = {
			"strength", "durability", "agility", "health", "chi", "chicontrol", "perception" };

	private static int page = 0;   // 0 character, 1 talents, 2 chi, 3 traits
	private static int traitScroll = 0;

	private static int traitSel = 0;
	private static final int TRAIT_Y = 52, TRAIT_ROW_H = 28, TRAIT_ROWS = 8;
	private static final int TRAIT_LIST_W = 236, TRAIT_GAP = 10;
	private static int scroll = 0;
	private static int activeTab = 0;

	// hold-to-repeat on + buttons
	private static int heldStat = -1;         // 0-6 stats, 7 = talent strength
	private static long heldSince = 0;
	private static long lastRepeat = 0;
	private static boolean draggingConceal = false;
	private static int pendingConceal = -1;   // local slider value until applied

	@SubscribeEvent
	public static void onRender(ScreenEvent.Render.Post event) {
		if (!isTerminal(event.getScreen())) return;
		Player player = Minecraft.getInstance().player;
		if (player == null) return;

		GuiGraphics g = event.getGuiGraphics();
		int x = (event.getScreen().width - PANEL_W) / 2;
		int y = (event.getScreen().height - PANEL_H) / 2;
		int mx = event.getMouseX(), my = event.getMouseY();

		Snapshot s = Snapshot.read(player);
		if (draggingConceal && !mouseDown()) draggingConceal = false;
		if (draggingConceal && page == 0) pendingConceal = trackValue(x + CON_X, mx);

		OrdealDraw.rect(g, 0, 0, event.getScreen().width, event.getScreen().height, 0xB0000000);
		panel(g, x, y);
		header(g, x, y, mx, my);

		if (page == 0) character(g, x, y, s, mx, my);
		else if (page == 1) talents(g, x, y, s, mx, my);
		else if (page == 3) traits(g, x, y, s, mx, my);
		else if (page == 4) net.mcreator.ordeal.PanoplyPage.render(g, x, y, mx, my);
		else chi(g, x, y, s, mx, my);

		footer(g, x, y, s);
		holdRepeat(x, y, s, mx, my);
	}

	private static boolean mouseDown() {
		long win = Minecraft.getInstance().getWindow().getWindow();
		return org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
				== org.lwjgl.glfw.GLFW.GLFW_PRESS;
	}

	private static void holdRepeat(int x, int y, Snapshot s, int mx, int my) {
		if (heldStat < 0) return;
		if (!mouseDown()) { heldStat = -1; return; }
		long now = System.currentTimeMillis();
		if (now - heldSince < 600 || now - lastRepeat < 100) return;
		if (heldStat <= 6 && page == 0) {
			int by = y + ROW_Y + heldStat * ROW_H;
			if (OrdealDraw.inside(mx, my, x + PLUS_X, by, 16, 14)) {
				act("spend", STAT_CMD[heldStat], 0);
				lastRepeat = now;
			}
		} else if (heldStat == 7 && page == 1 && s.activeSlot > 0) {
			if (OrdealDraw.inside(mx, my, x + CONTENT_X + CONTENT_W - BTN_W, y + LEGEND_Y - 3, BTN_W, BTN_H)) {
				act("talentstr", "", s.activeSlot);
				lastRepeat = now;
			}
		}
	}

	@SubscribeEvent
	public static void onScroll(ScreenEvent.MouseScrolled.Post event) {
		if (!isTerminal(event.getScreen()) || (page != 1 && page != 3)) return;
		Player player = Minecraft.getInstance().player;
		if (player == null) return;
		int step = (int) Math.signum(event.getScrollDeltaY());
		if (page == 3) {
			int max = Math.max(0, Snapshot.read(player).traits.size() - TRAIT_ROWS);
			traitScroll = Math.max(0, Math.min(max, traitScroll - step));
			return;
		}
		int max = Math.max(0, Snapshot.read(player).abilities.size() - ROWS_VISIBLE);
		scroll = Math.max(0, Math.min(max, scroll - step));
	}

	@SubscribeEvent
	public static void onClick(ScreenEvent.MouseButtonPressed.Post event) {
		Screen screen = event.getScreen();
		if (!isTerminal(screen)) return;
		Player player = Minecraft.getInstance().player;
		if (player == null) return;

		int x = (screen.width - PANEL_W) / 2;
		int y = (screen.height - PANEL_H) / 2;
		double mx = event.getMouseX(), my = event.getMouseY();

		int tabsX = topTabsX(x);
		for (int i = 0; i < TOPTABS.length; i++) {
			if (OrdealDraw.inside(mx, my, tabsX, y + TOPTAB_Y, tabW(i), TOPTAB_H)) {
				page = i;
				net.mcreator.ordeal.PanoplyPage.closed();   // nothing stays picked up across tabs
				return;
			}
			tabsX += tabW(i) + TOPTAB_GAP;
		}

		Snapshot s = Snapshot.read(player);

		if (page == 0) {
			if (OrdealDraw.inside(mx, my, x + MID_X, y + TBTN_Y, MID_W, TBTN_H)) {
				page = 1; return;
			}
			for (int i = 0; i < STAT_LABEL.length; i++) {
				int by = y + ROW_Y + i * ROW_H;
				if (OrdealDraw.inside(mx, my, x + PLUS_X, by, 16, 14)) {
					act("spend", STAT_CMD[i], 0);
					heldStat = i; heldSince = System.currentTimeMillis(); lastRepeat = heldSince;
					return;
				}
			}
			concealClick(x, y, s, mx, my);
			return;
		}

		if (page == 4) { net.mcreator.ordeal.PanoplyPage.click(x, y, mx, my, event.getButton()); return; }

		if (page == 2) return;

		if (page == 3) {
			int lx = x + CONTENT_X, ly = y + TRAIT_Y;
			for (int i = 0; i < s.traits.size(); i++) {
				int ry = ly + (i - traitScroll) * TRAIT_ROW_H;
				if (ry < ly || ry + TRAIT_ROW_H > ly + TRAIT_ROWS * TRAIT_ROW_H) continue;
				if (OrdealDraw.inside(mx, my, lx, ry, TRAIT_LIST_W, TRAIT_ROW_H - 2)) {
					traitSel = i; return;
				}
			}
			return;
		}

		int cx = x + CONTENT_X;
		int tx = cx;
		for (int i = 0; i < s.tabs.size(); i++) {
			int w = OrdealDraw.width(s.tabs.get(i).shortName) + 16;
			if (OrdealDraw.inside(mx, my, tx, y + TTABS_Y, w, TTAB_H)) {
				activeTab = i; scroll = 0; return;
			}
			tx += w + 4;
		}

		if (s.activeSlot > 0
				&& OrdealDraw.inside(mx, my, cx + CONTENT_W - BTN_W, y + LEGEND_Y - 3, BTN_W, BTN_H)) {
			act("talentstr", "", s.activeSlot);
			heldStat = 7; heldSince = System.currentTimeMillis(); lastRepeat = heldSince;
			return;
		}

		int idx = rowAt(x, y, mx, my, s.abilities.size());
		if (idx >= 0) {
			Row r = s.abilities.get(idx);
			if (r.id.equals("sense_filter")) {
				net.mcreator.ordeal.OrdealSilhouette.SHOW_PRESENCE =
						!net.mcreator.ordeal.OrdealSilhouette.SHOW_PRESENCE;
				return;
			}
			if (r.passive) {
				if (r.unlocked) act("passive", r.id, 0);
				return;
			}
			if (r.unlocked) act("select", r.id.equals(s.selected) ? "" : r.id, 0);
			return;
		}

		int gx = x + LOAD_X + (LOAD_W - (5 * SLOT + 4 * SLOT_GAP)) / 2;
		for (int i = 0; i < 10; i++) {
			int sx = gx + (i % 5) * (SLOT + SLOT_GAP);
			int sy = y + SLOTS_Y + (i / 5) * (SLOT + SLOT_GAP);
			if (!OrdealDraw.inside(mx, my, sx, sy, SLOT, SLOT)) continue;
			boolean filled = s.loadout[i] != null && !s.loadout[i].isEmpty();
			if (s.selectedName.isEmpty() && filled) act("bind", "", i + 1);
			else if (!s.selectedName.isEmpty() && !selectedIsPassive(s)) act("bind", s.selectedName, i + 1);
			return;
		}
	}

	private static void concealClick(int x, int y, Snapshot s, double mx, double my) {
		int cx = x + CON_X;
		// slider track
		if (OrdealDraw.inside(mx, my, cx + 6, y + CON_TRACK_Y - 3, CON_W - 12, 16)) {
			pendingConceal = trackValue(cx, mx);
			draggingConceal = true;
			return;
		}
		// apply
		if (OrdealDraw.inside(mx, my, cx + 6, y + CON_APPLY_Y, CON_W - 12, 16)) {
			int level = pendingConceal >= 0 ? pendingConceal : (int) Math.round(s.conceal * 100);
			act("conceal", "", level);
			pendingConceal = -1;
		}
	}

	private static int trackValue(int cx, double mx) {
		double f = (mx - (cx + 6)) / (double) (CON_W - 12);
		return (int) Math.max(0, Math.min(100, Math.round(f * 100)));
	}

	@SubscribeEvent
	public static void onRelease(ScreenEvent.MouseButtonReleased.Post event) {
		if (!isTerminal(event.getScreen())) return;
		heldStat = -1;
		draggingConceal = false;
		if (page == 4) {
			// where a panoply drag lands, and where a click that never moved
			// turns into the show/hide toggle
			net.minecraft.client.gui.screens.Screen sc = event.getScreen();
			int x = (sc.width - PANEL_W) / 2;
			int y = (sc.height - PANEL_H) / 2;
			net.mcreator.ordeal.PanoplyPage.release(x, y,
					event.getMouseX(), event.getMouseY(), event.getButton());
		}
	}

	@SubscribeEvent
	public static void onOpen(ScreenEvent.Init.Post event) {
		if (!isTerminal(event.getScreen())) return;
		OrdealTalents.reload();
		if (page < 0 || page > 4) page = 0;
		net.mcreator.ordeal.PanoplyPage.closed();
		if (activeTab < 0) activeTab = 0;
		scroll = 0;
		heldStat = -1;
		draggingConceal = false;
		pendingConceal = -1;
	}

	private static void act(String action, String arg, int value) {
		if (Minecraft.getInstance().player != null)
			PacketDistributor.sendToServer(new OrdealActionMessage(action, arg, value));
	}

	private static boolean isTerminal(Screen screen) {
		return screen != null && screen.getClass().getSimpleName().equals(SCREEN_CLASS);
	}

	// ---- chrome -------------------------------------------------------------

	private static void panel(GuiGraphics g, int x, int y) {
		OrdealDraw.rect(g, x, y, PANEL_W, PANEL_H, OrdealDraw.GROUND);
		OrdealDraw.outline(g, x, y, PANEL_W, PANEL_H, OrdealDraw.CYAN_FAINT);
		OrdealDraw.brackets(g, x + 2, y + 2, PANEL_W - 4, PANEL_H - 4, 8, OrdealDraw.CYAN);
	}

	private static void header(GuiGraphics g, int x, int y, int mx, int my) {
		OrdealDraw.rect(g, x + 1, y + 1, PANEL_W - 2, HEADER_H - 1, OrdealDraw.SURFACE);
		OrdealDraw.rect(g, x + 1, y + HEADER_H, PANEL_W - 2, 1, OrdealDraw.CYAN_FAINT);
		OrdealDraw.text(g, "K.O.D.E > FIELD TERMINAL", x + PAD, y + 9, OrdealDraw.CYAN);

		int tabsX = topTabsX(x);
		for (int i = 0; i < TOPTABS.length; i++) {
			topTab(g, tabsX, y + TOPTAB_Y, tabW(i), TOPTABS[i], page == i, mx, my);
			tabsX += tabW(i) + TOPTAB_GAP;
		}
	}

	private static void topTab(GuiGraphics g, int x, int y, int w, String label,
	                           boolean on, int mx, int my) {
		boolean hov = OrdealDraw.inside(mx, my, x, y, w, TOPTAB_H);
		int col = on ? OrdealDraw.INK : hov ? OrdealDraw.CYAN : OrdealDraw.CYAN_DIM;
		OrdealDraw.text(g, label, x + (w - OrdealDraw.width(label)) / 2, y + 4, col);
		if (on) OrdealDraw.rect(g, x, y + TOPTAB_H + 2, w, 2, OrdealDraw.CYAN);
	}

	private static void footer(GuiGraphics g, int x, int y, Snapshot s) {
		OrdealDraw.rect(g, x + 1, y + FOOT_Y - 6, PANEL_W - 2, 1, OrdealDraw.CYAN_FAINT);
		OrdealDraw.text(g, "CHI LIMIT", x + PAD, y + FOOT_Y, OrdealDraw.CYAN_DIM);
		OrdealDraw.text(g, (int) s.chiLimit + " / " + (int) s.limitMax,
				x + PAD + OrdealDraw.width("CHI LIMIT") + 8, y + FOOT_Y, OrdealDraw.INK);

		String tsp = "TALENT SP " + (int) s.talentSp + "  AND  USED " + (int) s.tspUsed + " / " + (int) s.tspCap;
		OrdealDraw.text(g, tsp, x + PANEL_W / 2 - OrdealDraw.width(tsp) / 2, y + FOOT_Y, OrdealDraw.INK_DIM);

		int pipsW = BLOOD_MAX * 5 + (BLOOD_MAX - 1) * 3;
		int pipsX = x + PANEL_W - PAD - pipsW;
		OrdealDraw.pips(g, pipsX, y + FOOT_Y + 1, BLOOD_MAX, s.bloodDoses, 5, 3,
				0xFFFF6B6B, OrdealDraw.alpha(OrdealDraw.CYAN, 0x50));
		OrdealDraw.textRight(g, "BLOOD " + s.bloodDoses + "/" + BLOOD_MAX,
				pipsX - 8, y + FOOT_Y, OrdealDraw.INK_DIM);
	}

	// ---- character ----------------------------------------------------------

	private static void character(GuiGraphics g, int x, int y, Snapshot s, int mx, int my) {
		int conceal = pendingConceal >= 0 ? pendingConceal : (int) Math.round(s.conceal * 100);
		double out = (100 - conceal) / 100.0;

		int model = x + MODEL_X;
		OrdealDraw.card(g, model, y + MODEL_Y, MODEL_W, MODEL_H, conceal > 0 ? AMBER_DIM : OrdealDraw.CYAN_FAINT);
		OrdealDraw.hatch(g, model + 1, y + MODEL_Y + 1, MODEL_W - 2, MODEL_H - 2, 0x0AFFFFFF, 6);
		OrdealDraw.brackets(g, model, y + MODEL_Y, MODEL_W, MODEL_H, 10, conceal > 0 ? AMBER : OrdealDraw.CYAN);
		Player self = Minecraft.getInstance().player;
		if (self != null)
			InventoryScreen.renderEntityInInventoryFollowsMouse(g,
					model + 6, y + MODEL_Y + 6, model + MODEL_W - 6, y + MODEL_Y + MODEL_H - 6,
					48, 0.0625f, (float) mx, (float) my, self);
		if (conceal > 0) {
			OrdealDraw.rect(g, model + 1, y + MODEL_Y + 1, MODEL_W - 2, MODEL_H - 2,
					OrdealDraw.alpha(0xFF06090F, (int) (conceal * 1.6)));
			OrdealDraw.hatch(g, model + 1, y + MODEL_Y + 1, MODEL_W - 2, MODEL_H - 2,
					OrdealDraw.alpha(AMBER, 20 + conceal / 2), 5);
		}

		OrdealDraw.card(g, model, y + ID_Y, MODEL_W, ID_H, OrdealDraw.CYAN_FAINT);
		OrdealDraw.text(g, s.playerName.toUpperCase(), model + 8, y + ID_Y + 8, OrdealDraw.INK);
		OrdealDraw.rect(g, model + 8, y + ID_Y + 20, MODEL_W - 16, 1, OrdealDraw.CYAN_FAINT);
		idRow(g, model, y + ID_Y + 28, "LEVEL",  String.valueOf((int) s.level), OrdealDraw.INK);
		idRow(g, model, y + ID_Y + 42, "FAMILY", s.family, OrdealDraw.INK);
		idRow(g, model, y + ID_Y + 56, "RACE",   s.isKimyo ? "KIMYO" : "HUMAN",
				s.isKimyo ? OrdealDraw.ILIOS : OrdealDraw.INK);
		idRow(g, model, y + ID_Y + 70, "CLAN",   s.clan, OrdealDraw.INK);

		int r = x + MID_X;
		OrdealDraw.card(g, r, y + CARD_Y, MID_W, CARD_H, s.sp > 0 ? 0xFFFFB877 : OrdealDraw.CYAN_FAINT);
		OrdealDraw.text(g, "SP " + (int) s.sp, r + 6, y + CARD_Y + 5,
				s.sp > 0 ? 0xFFFFB877 : OrdealDraw.INK_DIM);
		OrdealDraw.textRight(g, "USED " + (int) s.spUsed + " / " + (int) s.spCap,
				r + MID_W - 6, y + CARD_Y + 5, OrdealDraw.INK_DIM);
		OrdealDraw.cells(g, r + 6, y + CARD_Y + 15, MID_W - 12, 5, 26, s.xp,
				Math.max(1, s.xpCap), OrdealDraw.CYAN, OrdealDraw.alpha(OrdealDraw.CYAN, 0x1A));
		OrdealDraw.text(g, "NEXT LEVEL +" + (int) s.nextSp + " SP AND +" + (int) s.nextTsp + " TALENT SP",
				r + 6, y + CARD_Y + 24, OrdealDraw.INK_DIM);

		OrdealDraw.rect(g, r, y + ATTR_Y - 4, MID_W, 13, 0x14FFFFFF);
		OrdealDraw.text(g, "PHYSICAL ATTRIBUTES", r + 6, y + ATTR_Y - 1, OrdealDraw.CYAN_DIM);
		OrdealDraw.textRight(g, conceal > 0 ? "EFFECTIVE" : "VALUE", r + MID_W - 26, y + ATTR_Y - 1, OrdealDraw.INK_DIM);

		List<Component> statTip = null;
		for (int i = 0; i < STAT_LABEL.length; i++) {
			int ry = y + ROW_Y + i * ROW_H;
			double v = s.stats[i];
			int eff = (int) Math.round(v * out);

			int plusX = x + PLUS_X;
			int valX  = plusX - 6;
			int barX  = r + 76, barW = valX - barX - 26;

			boolean rowHov = OrdealDraw.inside(mx, my, r, ry - 3, MID_W - 24, ROW_H);
			if (rowHov) OrdealDraw.rect(g, r, ry - 3, MID_W, ROW_H, 0x0E7ED8F5);

			OrdealDraw.text(g, STAT_LABEL[i], r + 6, ry + 2, OrdealDraw.INK);
			OrdealDraw.rect(g, barX, ry + 3, barW, 6, OrdealDraw.alpha(OrdealDraw.CYAN, 0x1A));
			int fw = (int) Math.round(barW * Math.min(1, eff / 100.0));
			int lw = (int) Math.round(barW * Math.min(1, v / 100.0)) - fw;
			OrdealDraw.rect(g, barX, ry + 3, fw, 6, OrdealDraw.CYAN);
			if (lw > 0) OrdealDraw.hatch(g, barX + fw, ry + 3, lw, 6, OrdealDraw.alpha(AMBER, 0x8C), 3);
			OrdealDraw.textRight(g, String.format("%02d", conceal > 0 ? eff : (int) v), valX, ry + 2,
					conceal > 0 ? 0xFFFFD39A : OrdealDraw.INK);

			// spent out for the run: the cap is enforced server side now, so the
			// button has to stop LOOKING clickable too
			boolean spentOut = s.spCap > 0 && s.spUsed >= s.spCap;
			boolean can = s.sp > 0 && v < 100 && v < s.level && !spentOut;
			boolean hov = OrdealDraw.inside(mx, my, plusX, ry, 16, 14);
			int col = can ? (hov ? 0xFFFFD9A0 : 0xFFFFB877) : OrdealDraw.LOCKED;
			OrdealDraw.rect(g, plusX, ry, 16, 14, can ? (hov ? 0x33FFB877 : 0x1AFFB877) : 0);
			OrdealDraw.outline(g, plusX, ry, 16, 14, col);
			OrdealDraw.text(g, "+", plusX + 6, ry + 3, col);

			if (rowHov) {
				statTip = new ArrayList<>();
				statTip.add(tint(STAT_LABEL[i], 0xFFEAF7FF).withStyle(ChatFormatting.BOLD));
				statTip.add(tint(STAT_DESC[i], 0xFF4B7D92));
				statTip.add(Component.empty());
				statTip.add(stat("NOW", effect(i, s), 0xFF7ED8F5));
				if (conceal > 0) statTip.add(stat("CONCEALED", eff + " / " + (int) v, 0xFFFFB020));
				if (v >= 100) statTip.add(tint("maxed", 0xFF5FE3A0));
				else if (spentOut) statTip.add(tint("lifetime SP spent - " + (int) s.spUsed
						+ " / " + (int) s.spCap, 0xFFFF6B6B));
				else if (v >= s.level) statTip.add(tint("locked - needs level " + (int) (v + 1), 0xFFFF6B6B));
				else if (s.sp <= 0) statTip.add(tint("no SP", 0xFFFF6B6B));
				else statTip.add(tint("click + to raise - hold to channel", 0xFF5FE3A0));
			}
		}
		if (statTip != null) OrdealDraw.tooltip(g, statTip, mx, my);

		boolean hov = OrdealDraw.inside(mx, my, r, y + TBTN_Y, MID_W, TBTN_H);
		OrdealDraw.rect(g, r, y + TBTN_Y, MID_W, TBTN_H, hov ? 0x33FFB877 : 0x1AFFB877);
		OrdealDraw.outline(g, r, y + TBTN_Y, MID_W, TBTN_H, 0xFFFFB877);
		OrdealDraw.text(g, "TALENTS", r + 8, y + TBTN_Y + 7, 0xFFFFD9A0);
		OrdealDraw.textRight(g, s.talentBrief + " >", r + MID_W - 8, y + TBTN_Y + 7, OrdealDraw.INK_DIM);

		concealPanel(g, x, y, s, conceal, mx, my);
	}

	private static void concealPanel(GuiGraphics g, int x, int y, Snapshot s, int conceal, int mx, int my) {
		int cx = x + CON_X;
		boolean on = conceal > 0;
		boolean sup = conceal >= 90;
		int frame = on ? AMBER : OrdealDraw.CYAN_FAINT;

		OrdealDraw.rect(g, cx, y + 32, CON_W, PANEL_H - 32 - 44, 0x99060A10);
		OrdealDraw.outline(g, cx, y + 32, CON_W, PANEL_H - 32 - 44, frame);
		OrdealDraw.text(g, "CONCEALMENT", cx + 6, y + 38, on ? AMBER : OrdealDraw.CYAN_DIM);
		OrdealDraw.textRight(g, sup ? "SUPPRESSED" : on ? "PARTIAL" : "OFF",
				cx + CON_W - 6, y + 38, on ? AMBER : OrdealDraw.CYAN_DIM);
		OrdealDraw.rect(g, cx + 1, y + 48, CON_W - 2, 1, OrdealDraw.alpha(frame, 0x60));

		// big number
		String big = conceal + "%";
		g.pose().pushPose();
		g.pose().translate(cx + 6, y + 56, 0);
		g.pose().scale(2f, 2f, 1f);
		OrdealDraw.text(g, big, 0, 0, on ? AMBER : 0xFF5F7F96);
		g.pose().popPose();
		OrdealDraw.textRight(g, "OUTPUT", cx + CON_W - 6, y + 54, OrdealDraw.CYAN_DIM);
		OrdealDraw.textRight(g, (100 - conceal) + "%", cx + CON_W - 6, y + 64,
				conceal >= 60 ? 0xFFFFD39A : OrdealDraw.INK);

		// track
		int tx = cx + 6, tw = CON_W - 12, ty = y + CON_TRACK_Y;
		OrdealDraw.rect(g, tx, ty, tw, 10, 0x59000000);
		int fw = (int) Math.round(tw * conceal / 100.0);
		OrdealDraw.rect(g, tx, ty, fw, 10, OrdealDraw.alpha(AMBER, sup ? 0x80 : 0x47));
		OrdealDraw.hatch(g, tx, ty, fw, 10, OrdealDraw.alpha(AMBER, 0x8C), 4);
		OrdealDraw.outline(g, tx, ty, tw, 10, OrdealDraw.alpha(OrdealDraw.CYAN, 0x47));
		int m90 = tx + (int) Math.round(tw * 0.9);
		OrdealDraw.rect(g, m90, ty - 2, 1, 14, AMBER);
		OrdealDraw.rect(g, tx + fw - 1, ty - 2, 2, 14, on ? AMBER : OrdealDraw.CYAN_DIM);
		OrdealDraw.text(g, "0", tx, ty + 13, 0xFF3C6478);
		OrdealDraw.textRight(g, "100", tx + tw, ty + 13, 0xFF3C6478);

		// the trade
		int gy = y + CON_PRESET_Y;
		OrdealDraw.text(g, "A MAX-SENSE READER SEES", cx + 6, gy, OrdealDraw.CYAN_DIM);
		if (sup) {
			OrdealDraw.hatch(g, cx + 6, gy + 10, CON_W - 12, 16, 0x29FFB020, 5);
			OrdealDraw.outline(g, cx + 6, gy + 10, CON_W - 12, 16, 0x8CFFB020);
			String t = "SIGNATURE SUPPRESSED";
			OrdealDraw.text(g, t, cx + 6 + (CON_W - 12 - OrdealDraw.width(t)) / 2, gy + 14, AMBER);
		} else {
			tradeRow(g, cx, gy + 10, "RACE", s.isKimyo ? "KIMYO" : "HUMAN", OrdealDraw.INK);
			tradeRow(g, cx, gy + 21, "TALENT", conceal >= 50 ? "UNCLEAR" : s.talentShort, conceal >= 50 ? AMBER_DIM : s.talentAccent);
			tradeRow(g, cx, gy + 32, "CHI READ", (int) Math.round(s.chiPct * (100 - conceal) / 100.0) + "%"
					+ (conceal > 0 ? " MASKED" : ""), conceal > 0 ? AMBER_DIM : OrdealDraw.INK);
		}
		int py = gy + 48;
		OrdealDraw.text(g, "YOU PAY", cx + 6, py, OrdealDraw.CYAN_DIM);
		tradeRow(g, cx, py + 10, "DAMAGE", "x" + String.format("%.2f", (100 - conceal) / 100.0),
				conceal > 0 ? 0xFFFFD39A : OrdealDraw.INK);
		tradeRow(g, cx, py + 21, "CHI POOL", (int) Math.round(s.chiMaxFull * (100 - conceal) / 100.0)
				+ " / " + (int) s.chiMaxFull, conceal > 0 ? 0xFFFFD39A : OrdealDraw.INK);

		// apply
		boolean dirty = pendingConceal >= 0 && pendingConceal != (int) Math.round(s.conceal * 100);
		boolean ahov = OrdealDraw.inside(mx, my, cx + 6, y + CON_APPLY_Y, CON_W - 12, 16);
		int acol = dirty ? AMBER : OrdealDraw.LOCKED;
		OrdealDraw.rect(g, cx + 6, y + CON_APPLY_Y, CON_W - 12, 16, dirty ? (ahov ? 0x40FFB020 : 0x29FFB020) : 0);
		OrdealDraw.outline(g, cx + 6, y + CON_APPLY_Y, CON_W - 12, 16, acol);
		String al = dirty ? "APPLY " + conceal + "%" : "APPLIED " + conceal + "%";
		OrdealDraw.text(g, al, cx + 6 + (CON_W - 12 - OrdealDraw.width(al)) / 2, y + CON_APPLY_Y + 4,
				dirty ? 0xFFFFD39A : OrdealDraw.INK_DIM);
	}

	private static void tradeRow(GuiGraphics g, int cx, int gy, String k, String v, int col) {
		OrdealDraw.text(g, k, cx + 6, gy, OrdealDraw.CYAN_DIM);
		OrdealDraw.textRight(g, v, cx + CON_W - 6, gy, col);
	}

	private static String effect(int i, Snapshot s) {
		double v = s.stats[i];
		switch (i) {
			case 0: return "+" + String.format("%.1f", v * OrdealCombat.AP_PER_STR) + " ATK";
			case 1: return (int) (OrdealCombat.GUARD_BASE + v * OrdealCombat.GUARD_PER_DUR) + " GUARD";
			case 2: return "+" + (int) (v * OrdealStats.SPEED_PER_AGI * 100) + "% SPD";
			case 3: return (int) (20 + Math.max(0, v)) + " HP";
			case 4: return (int) (v * OrdealStats.CHI_PER_POINT) + " CHI";
			case 5: return "-" + (int) Math.min(40, v * 0.4) + "% COST";
			case 6: return (int) (8 + v * 0.6) + " BLOCKS";
			default: return "";
		}
	}

	private static void idRow(GuiGraphics g, int x, int y, String label, String value, int col) {
		OrdealDraw.text(g, label, x + 10, y, OrdealDraw.CYAN_DIM);
		OrdealDraw.textRight(g, value, x + MODEL_W - 10, y, col);
	}

	// ---- talents ------------------------------------------------------------

	private static void talents(GuiGraphics g, int x, int y, Snapshot s, int mx, int my) {
		int cx = x + CONTENT_X;
		int tx = cx;
		for (int i = 0; i < s.tabs.size(); i++) {
			OrdealTalents.Talent t = s.tabs.get(i);
			int tw = OrdealDraw.width(t.shortName) + 16;
			boolean on = i == activeTab;
			OrdealDraw.rect(g, tx, y + TTABS_Y, tw, TTAB_H, on ? OrdealDraw.alpha(t.accent, 0x22) : 0x0E7ED8F5);
			OrdealDraw.outline(g, tx, y + TTABS_Y, tw, TTAB_H, on ? t.accent : OrdealDraw.CYAN_FAINT);
			OrdealDraw.text(g, t.shortName, tx + 8, y + TTABS_Y + 3, on ? t.accent : OrdealDraw.CYAN_DIM);
			tx += tw + 4;
		}

		OrdealTalents.Talent active = s.tabs.get(Math.min(activeTab, s.tabs.size() - 1));
		boolean isBasic = active.id.equals("basic");

		OrdealDraw.text(g, active.name, cx, y + TNAME_Y, active.accent);

		// type tags, one colour per type so the talent's nature reads at a glance
		int chipX = cx;
		for (int i = 0; i < active.types.length; i++) {
			int col = i < active.typeColours.length && active.typeColours[i] != 0
					? active.typeColours[i]
					: OrdealTalents.typeColour(active.types[i]);
			chipX += OrdealDraw.chipFilled(g, active.types[i], chipX, y + TCHIPS_Y, col);
		}

		talentDesc(g, cx, y, active, s);

		if (isBasic) {
			OrdealDraw.textRight(g, "LEVEL", cx + CONTENT_W, y + TNAME_Y - 10, OrdealDraw.CYAN_DIM);
			bigNumber(g, cx + CONTENT_W, y + BIGNUM_Y + 12, (int) s.level, OrdealDraw.CYAN);
		} else {
			OrdealDraw.textRight(g, "STRENGTH", cx + CONTENT_W, y + TNAME_Y - 10, OrdealDraw.CYAN_DIM);
			bigNumber(g, cx + CONTENT_W, y + BIGNUM_Y + 12, (int) s.activeStrength, active.accent);
		}
		strengthBar(g, cx, y, s, isBasic, mx, my);

		abilityList(g, x, y, s, mx, my);
		loadout(g, x, y, s, mx, my);
	}

	// ---- traits --------------------------------------------------------------

	/**
	 * Every trait in the mod, with the ones you actually hold floated to the top.
	 * The list stays complete on purpose - it reads as a goal board - but your own
	 * kit is what you see first.
	 */
	private static void traits(GuiGraphics g, int x, int y, Snapshot s, int mx, int my) {
		int cx = x + CONTENT_X;

		OrdealDraw.text(g, "TRAITS", cx, y + TTABS_Y, OrdealDraw.CYAN_DIM);
		OrdealDraw.textRight(g, s.traitsOwned + " / " + s.traits.size() + " ACQUIRED",
				cx + CONTENT_W, y + TTABS_Y, s.traitsOwned > 0 ? OrdealDraw.GREEN : OrdealDraw.INK_DIM);
		OrdealDraw.rect(g, cx, y + TTABS_Y + 12, CONTENT_W, 1, OrdealDraw.CYAN_FAINT);

		int ly = y + TRAIT_Y;
		int viewH = TRAIT_ROWS * TRAIT_ROW_H;

		if (s.traits.isEmpty()) {
			OrdealDraw.text(g, "NO TRAITS REGISTERED - CHECK assets/ordeal/traits/",
					cx, ly + 8, OrdealDraw.INK_DIM);
			return;
		}
		if (traitSel >= s.traits.size()) traitSel = s.traits.size() - 1;

		int maxScroll = Math.max(0, s.traits.size() - TRAIT_ROWS);
		if (traitScroll > maxScroll) traitScroll = maxScroll;

		// ---- left: the roster. One line each, so nothing can ever be clipped.
		g.enableScissor(cx, ly, cx + TRAIT_LIST_W, ly + viewH);
		for (int i = 0; i < s.traits.size(); i++) {
			int ry = ly + (i - traitScroll) * TRAIT_ROW_H;
			if (ry + TRAIT_ROW_H < ly || ry > ly + viewH) continue;

			OrdealTraits.Trait t = s.traits.get(i);
			boolean own = s.traitOwned[i];
			boolean sel = i == traitSel;
			boolean hov = OrdealDraw.inside(mx, my, cx, ry, TRAIT_LIST_W, TRAIT_ROW_H - 2);
			int accent = own ? t.accent : OrdealDraw.LOCKED;

			OrdealDraw.rect(g, cx, ry, TRAIT_LIST_W, TRAIT_ROW_H - 2,
					sel ? OrdealDraw.alpha(accent, 0x24)
							: hov ? 0x127ED8F5
							: own ? OrdealDraw.alpha(t.accent, 0x0E) : 0x14000000);
			OrdealDraw.rect(g, cx, ry, 3, TRAIT_ROW_H - 2, accent);
			if (sel) OrdealDraw.outline(g, cx, ry, TRAIT_LIST_W, TRAIT_ROW_H - 2,
					OrdealDraw.alpha(accent, 0x99));

			OrdealDraw.text(g, t.name.toUpperCase(), cx + 10, ry + 4,
					own ? OrdealDraw.INK : 0xFF6F93AD);
			OrdealDraw.text(g, own ? "ACQUIRED" : "LOCKED", cx + 10, ry + 15,
					own ? OrdealDraw.GREEN : 0xFF48626F);
		}
		g.disableScissor();
		OrdealDraw.outline(g, cx, ly, TRAIT_LIST_W, viewH, OrdealDraw.CYAN_FAINT);

		if (maxScroll > 0) {
			int sx = cx + TRAIT_LIST_W - SCROLLBAR_W - 1;
			OrdealDraw.rect(g, sx, ly + 1, SCROLLBAR_W, viewH - 2, 0x1A7ED8F5);
			int thumbH = Math.max(10, (viewH - 2) * TRAIT_ROWS / s.traits.size());
			OrdealDraw.rect(g, sx, ly + 1 + (viewH - 2 - thumbH) * traitScroll / maxScroll,
					SCROLLBAR_W, thumbH, OrdealDraw.CYAN_DIM);
		}

		// ---- right: the selected trait in full, with room to breathe
		OrdealTraits.Trait t = s.traits.get(traitSel);
		boolean own = s.traitOwned[traitSel];
		int accent = own ? t.accent : OrdealDraw.LOCKED;
		int dx = cx + TRAIT_LIST_W + TRAIT_GAP;
		int dw = CONTENT_W - TRAIT_LIST_W - TRAIT_GAP;

		OrdealDraw.rect(g, dx, ly, dw, viewH, 0x1E000000);
		OrdealDraw.outline(g, dx, ly, dw, viewH, OrdealDraw.alpha(accent, own ? 0x80 : 0x40));
		OrdealDraw.brackets(g, dx, ly, dw, viewH, 8, accent);

		OrdealDraw.text(g, t.name.toUpperCase(), dx + 12, ly + 12,
				own ? accent : 0xFF6F93AD);
		OrdealDraw.text(g, own ? "ACQUIRED" : "NOT ACQUIRED", dx + 12, ly + 24,
				own ? OrdealDraw.GREEN : OrdealDraw.INK_DIM);
		OrdealDraw.rect(g, dx + 12, ly + 36, dw - 24, 1, OrdealDraw.alpha(accent, 0x40));

		java.util.List<String> body = OrdealDraw.wrapPx(t.desc, dw - 24, 8);
		for (int i = 0; i < body.size(); i++)
			OrdealDraw.text(g, body.get(i), dx + 12, ly + 46 + i * 10,
					own ? 0xFFDBEEF8 : 0xFF7C93A3);

		if (!t.obtain.isEmpty()) {
			java.util.List<String> how = OrdealDraw.wrapPx(t.obtain, dw - 24, 2);
			int hy = ly + viewH - 12 - how.size() * 10;
			OrdealDraw.rect(g, dx + 12, hy - 8, dw - 24, 1, OrdealDraw.alpha(OrdealDraw.CYAN, 0x1E));
			for (int i = 0; i < how.size(); i++)
				OrdealDraw.text(g, how.get(i), dx + 12, hy + i * 10, 0xFFA9CDDD);
		}
	}

	// ---- chi -----------------------------------------------------------------

	private static void chi(GuiGraphics g, int x, int y, Snapshot s, int mx, int my) {
		int areaW = 226, areaH = PANEL_H - 32 - 44;
		int ax = x + PAD, ay = y + 32;
		OrdealDraw.rect(g, ax, ay, areaW, areaH, 0x73030609);
		OrdealDraw.outline(g, ax, ay, areaW, areaH, OrdealDraw.CYAN_FAINT);
		OrdealDraw.text(g, "CHI BOUNDARY", ax + 5, ay + 4, 0xFF3C6478);

		double t = (System.currentTimeMillis() % 1000000L) / 1000.0;
		float cxp = ax + areaW / 2f, cyp = ay + areaH / 2f + 4;
		float maxR = Math.min(areaW, areaH) / 2f - 16;
		float ringR = (float) (maxR * Math.sqrt(Math.max(1, s.chiLimit) / Math.max(1, s.limitMax)));

		ring(g, cxp, cyp, (float) (maxR * Math.sqrt(100 / Math.max(1, s.limitMax))), 1f, 0x387ED8F5, 24, true);
		ring(g, cxp, cyp, maxR, 1f, 0x40FF8A2B, 24, true);
		ring(g, cxp, cyp, ringR, 1.6f, 0xFF7ED8F5, 64, false);

		int blobs = s.strengths.length;
		// Big and alive: masses may lap into each other a little, never past the ring.
		float BULGE = 1.1f;
		float[] r = new float[blobs];
		for (int i = 0; i < blobs; i++)
			r[i] = (float) (ringR * 0.95f / BULGE * Math.sqrt(Math.min(1, s.strengths[i] / Math.max(1, s.chiLimit))));

		float[] bx = new float[blobs], by = new float[blobs];
		if (blobs == 1) { bx[0] = cxp; by[0] = cyp; }
		else if (blobs > 1) {
			float need = (r[0] + r[1]) * 0.8f;
			float roomA = Math.max(0, ringR - r[0] * BULGE);
			float roomB = Math.max(0, ringR - r[1] * BULGE);
			float room = roomA + roomB;
			if (need > room && need > 0) need = room;
			float offA = room <= 0 ? 0 : need * (roomA / room);
			float offB = need - offA;
			double dir = 0.9;
			bx[0] = cxp + (float) Math.cos(dir) * offA; by[0] = cyp + (float) Math.sin(dir) * offA;
			bx[1] = cxp - (float) Math.cos(dir) * offB; by[1] = cyp - (float) Math.sin(dir) * offB;
		}

		for (int i = 0; i < blobs; i++) {
			if (r[i] < 2) continue;
			blob(g, bx[i], by[i], r[i], t, i * 2.4, s.accents[i]);
		}
		// labels after both masses so neither paints over them
		for (int i = 0; i < blobs; i++) {
			if (r[i] < 2) continue;
			String lbl = s.talentNames[i] + " " + (int) s.strengths[i];
			int lx = (int) (bx[i] - OrdealDraw.width(lbl) / 2f);
			int lyy = (int) (by[i] + r[i] * BULGE + 3);
			if (i == 1 && blobs > 1) lyy = (int) (by[i] - r[i] * BULGE - 10);
			lyy = Math.max(ay + 14, Math.min(lyy, ay + areaH - 10));
			OrdealDraw.rect(g, lx - 2, lyy - 1, OrdealDraw.width(lbl) + 4, 10, 0x99060A10);
			OrdealDraw.text(g, lbl, lx, lyy, s.accents[i]);
		}
		if (blobs == 0)
			OrdealDraw.text(g, "NO TALENT - BOUNDARY EMPTY",
					(int) cxp - OrdealDraw.width("NO TALENT - BOUNDARY EMPTY") / 2, (int) cyp - 4, 0x737ED8F5);

		// right column
		int rx = x + PAD + areaW + 10;
		int rw = PANEL_W - (PAD + areaW + 10) - PAD;

		OrdealDraw.card(g, rx, y + 32, rw, 40, OrdealDraw.CYAN_FAINT);
		OrdealDraw.text(g, "CHI LIMIT", rx + 6, y + 37, OrdealDraw.CYAN_DIM);
		OrdealDraw.textRight(g, (int) s.chiLimit + " / " + (int) s.limitMax,
				rx + rw - 6, y + 37, OrdealDraw.INK);
		int bx2 = rx + 6, bw2 = rw - 12;
		OrdealDraw.rect(g, bx2, y + 48, bw2, 8, 0x59000000);
		OrdealDraw.rect(g, bx2, y + 48,
				(int) (bw2 * Math.min(1, s.chiLimit / Math.max(1, s.limitMax))), 8, OrdealDraw.CYAN);
		OrdealDraw.rect(g, bx2 + (int) (bw2 * (100 / Math.max(1, s.limitMax))), y + 46, 1, 12, 0xE6FF8A2B);
		OrdealDraw.outline(g, bx2, y + 48, bw2, 8, OrdealDraw.alpha(OrdealDraw.CYAN, 0x47));
		OrdealDraw.text(g, "NATURAL CAP 100", bx2, y + 60, AMBER_DIM);
		OrdealDraw.textRight(g, "BLOOD CAP 150", bx2 + bw2, y + 60, 0xFF3C6478);

		int oy = y + 80;
		int oh = PANEL_H - 44 - 80;
		OrdealDraw.card(g, rx, oy, rw, oh, OrdealDraw.CYAN_FAINT);
		OrdealDraw.text(g, "OCCUPANCY", rx + 6, oy + 5, OrdealDraw.CYAN_DIM);
		double free = Math.max(0, s.chiLimit - s.totalStrength);
		OrdealDraw.textRight(g, "ROOM LEFT " + (int) free, rx + rw - 6, oy + 5,
				free <= 0 ? AMBER_DIM : OrdealDraw.INK_DIM);
		OrdealDraw.rect(g, rx + 1, oy + 16, rw - 2, 1, OrdealDraw.CYAN_FAINT);

		int ly = oy + 24;
		for (int i = 0; i < s.strengths.length; i++) {
			OrdealDraw.rect(g, rx + 6, ly + 1, 6, 6, s.accents[i]);
			OrdealDraw.text(g, s.talentNames[i], rx + 16, ly, OrdealDraw.INK);
			OrdealDraw.textRight(g, (int) s.strengths[i] + " / " + (int) s.chiLimit, rx + rw - 6, ly, s.accents[i]);
			OrdealDraw.text(g, (int) Math.round(100 * s.strengths[i] / Math.max(1, s.chiLimit))
					+ "% OF THE BOUNDARY", rx + 16, ly + 10, 0xFF3C6478);
			ly += 26;
		}
		if (s.strengths.length == 0)
			OrdealDraw.text(g, "NOTHING CLAIMS THIS BOUNDARY", rx + 6, ly, OrdealDraw.INK_DIM);
	}

	private static void ring(GuiGraphics g, float cx, float cy, float r, float w, int argb, int segs, boolean dashed) {
		for (int i = 0; i < segs; i++) {
			if (dashed && i % 2 == 1) continue;
			double a = i * Math.PI * 2 / segs;
			int px = (int) (cx + Math.cos(a) * r);
			int py = (int) (cy + Math.sin(a) * r);
			OrdealDraw.rect(g, px, py, Math.max(1, (int) w), Math.max(1, (int) w), argb);
		}
	}

	/** Living chi mass: wobbling radial fill drawn as vertical strips. */
	private static void blob(GuiGraphics g, float cx, float cy, float r, double t, double seed, int accent) {
		int steps = Math.max(10, (int) (r * 1.5));
		for (int i = 0; i < steps; i++) {
			double a = i * Math.PI * 2 / steps;
			double k = 1
					+ 0.085 * Math.sin(3 * a + t * 0.55 + seed)
					+ 0.055 * Math.sin(5 * a - t * 0.42 + seed * 1.7)
					+ 0.035 * Math.sin(7 * a + t * 0.71 + seed * 2.3);
			double rr = r * k;
			int x0 = (int) (cx + Math.cos(a) * rr);
			int y0 = (int) (cy + Math.sin(a) * rr);
			// edge
			OrdealDraw.rect(g, x0, y0, 2, 2, accent);
			// fill ray
			int fx = (int) cx, fy = (int) cy;
			int dx = x0 - fx, dy = y0 - fy;
			int n = Math.max(Math.abs(dx), Math.abs(dy));
			for (int j = 0; j < n; j += 3) {
				OrdealDraw.rect(g, fx + dx * j / Math.max(1, n), fy + dy * j / Math.max(1, n), 2, 2,
						OrdealDraw.alpha(accent, 0x38));
			}
		}
		// churn highlights
		for (int i = 0; i < 3; i++) {
			double ph = t * (0.22 + i * 0.06) + i * 1.9 + seed;
			int ix = (int) (cx + Math.cos(ph) * r * 0.34);
			int iy = (int) (cy + Math.sin(ph * 1.3) * r * 0.34);
			OrdealDraw.rect(g, ix - 2, iy - 2, 4, 4, OrdealDraw.alpha(0xFFFFFFFF, 0x24));
		}
	}

	private static void bigNumber(GuiGraphics g, int rightX, int y, int value, int colour) {
		String t = String.valueOf(value);
		g.pose().pushPose();
		g.pose().translate(rightX - OrdealDraw.width(t) * 2, y, 0);
		g.pose().scale(2f, 2f, 1f);
		OrdealDraw.text(g, t, 0, 0, colour);
		g.pose().popPose();
	}

	/**
	 * The talent's own description. The big number occupies the top right, so the
	 * text keeps clear of that column and wraps to TDESC_LINES lines underneath.
	 */
	private static void talentDesc(GuiGraphics g, int cx, int y, OrdealTalents.Talent t, Snapshot s) {
		String body = t.desc == null ? "" : t.desc.trim();
		if (body.isEmpty())
			body = t.id.equals("basic")
					? "Techniques every awakened body can learn."
					: "No record on file. Add a \"desc\" line to " + t.id + ".json.";

		int textW = CONTENT_W - 80;   // leave the STRENGTH / LEVEL column alone
		java.util.List<String> lines = OrdealDraw.wrapPx(body, textW, TDESC_LINES);

		int blockH = Math.max(1, lines.size()) * TDESC_LH;
		OrdealDraw.rect(g, cx, y + TDESC_Y - 3, 1, blockH + 2, OrdealDraw.alpha(t.accent, 0xAA));
		for (int i = 0; i < lines.size(); i++)
			OrdealDraw.text(g, lines.get(i), cx + 6, y + TDESC_Y + i * TDESC_LH,
					i == 0 ? 0xFF9FB4C4 : 0xFF7C93A3);

		// the count of abilities this talent brings, parked on the right of the block
		String tag = t.abilities.size() + (t.abilities.size() == 1 ? " ABILITY" : " ABILITIES");
		OrdealDraw.textRight(g, tag, cx + CONTENT_W, y + TDESC_Y + TDESC_LH, OrdealDraw.INK_DIM);
	}

	private static void strengthBar(GuiGraphics g, int x, int y, Snapshot s, boolean isBasic, int mx, int my) {
		OrdealDraw.text(g, "TALENT STRENGTH", x, y + STR_Y, OrdealDraw.CYAN_DIM);
		OrdealDraw.textRight(g, (int) s.totalStrength + " / " + (int) s.chiLimit,
				x + CONTENT_W, y + STR_Y, OrdealDraw.INK);

		OrdealDraw.segmentedBar(g, x, y + BAR_Y, CONTENT_W, BAR_H, s.strengths, s.accents,
				Math.max(1, s.chiLimit), OrdealDraw.alpha(OrdealDraw.CYAN, 0x1E));

		int lx = x;
		for (int i = 0; i < s.talentNames.length; i++) {
			OrdealDraw.rect(g, lx, y + LEGEND_Y + 1, 5, 5, s.accents[i]);
			String label = s.talentNames[i] + " " + (int) s.strengths[i];
			OrdealDraw.text(g, label, lx + 8, y + LEGEND_Y, OrdealDraw.CYAN_DIM);
			lx += 8 + OrdealDraw.width(label) + 12;
		}
		OrdealDraw.rect(g, lx, y + LEGEND_Y + 1, 5, 5, OrdealDraw.alpha(OrdealDraw.CYAN, 0x2E));
		OrdealDraw.text(g, "UNSPENT " + (int) Math.max(0, s.chiLimit - s.totalStrength),
				lx + 8, y + LEGEND_Y, OrdealDraw.CYAN_DIM);

		if (isBasic) return;
		boolean full = s.totalStrength >= s.chiLimit;
		boolean maxed = s.activeStrength >= 150;
		boolean spentOut = s.tspCap > 0 && s.tspUsed >= s.tspCap;
		boolean can = s.talentSp > 0 && !full && !maxed && !spentOut;
		int bx = x + CONTENT_W - BTN_W, by = y + LEGEND_Y - 3;
		boolean hov = OrdealDraw.inside(mx, my, bx, by, BTN_W, BTN_H);
		int col = can ? (hov ? 0xFFFFD9A0 : 0xFFFFB877) : OrdealDraw.LOCKED;

		String label = maxed ? "MAXED"
				: spentOut ? "TSP SPENT"
				: s.talentSp <= 0 ? "NO TALENT SP"
				: full ? "AT CHI LIMIT"
				: "+ STRENGTH";

		OrdealDraw.rect(g, bx, by, BTN_W, BTN_H, can && hov ? 0x33FFB877 : 0x1AFFB877);
		OrdealDraw.outline(g, bx, by, BTN_W, BTN_H, col);
		OrdealDraw.text(g, label, bx + (BTN_W - OrdealDraw.width(label)) / 2, by + 3, col);
	}

	private static void abilityList(GuiGraphics g, int x, int y, Snapshot s, int mx, int my) {
		int lx = x + LIST_X;
		int ly = y + LIST_Y;
		int viewH = ROWS_VISIBLE * LROW_H;
		int listW = LIST_W - SCROLLBAR_W - 4;

		int maxScroll = Math.max(0, s.abilities.size() - ROWS_VISIBLE);
		if (scroll > maxScroll) scroll = maxScroll;

		OrdealDraw.text(g, "ABILITIES", lx, y + SECT_Y, OrdealDraw.CYAN_DIM);
		OrdealDraw.textRight(g, s.abilities.size() + " TOTAL", lx + LIST_W, y + SECT_Y, OrdealDraw.INK_DIM);

		OrdealDraw.rect(g, lx, ly, listW, viewH, 0x14000000);
		g.enableScissor(lx, ly, lx + listW, ly + viewH);

		Row hovered = null;
		for (int i = 0; i < s.abilities.size(); i++) {
			int ry = ly + (i - scroll) * LROW_H;
			if (ry + LROW_H < ly || ry > ly + viewH) continue;

			Row r = s.abilities.get(i);
			boolean isSel = r.id.equals(s.selected);
			boolean isHov = OrdealDraw.inside(mx, my, lx, ry, listW, LROW_H);
			if (isHov) hovered = r;

			if (isSel) OrdealDraw.rect(g, lx, ry, listW, LROW_H, OrdealDraw.alpha(r.accent, 0x22));
			else if (isHov) OrdealDraw.rect(g, lx, ry, listW, LROW_H, 0x127ED8F5);

			int ix = lx + 3, iy = ry + (LROW_H - ICON) / 2;
			int frame = r.unlocked ? r.accent : OrdealDraw.LOCKED;
			OrdealDraw.outline(g, ix, iy, ICON, ICON, frame);
			if (r.iconTex != null)
				OrdealDraw.icon(g, r.iconTex, ix + 1, iy + 1, ICON - 2);
			else
				OrdealDraw.text(g, r.icon, ix + (ICON - OrdealDraw.width(r.icon)) / 2, iy + 3, frame);

			OrdealDraw.text(g, r.name, ix + ICON + 8, ry + 4,
					r.unlocked ? OrdealDraw.INK : OrdealDraw.INK_DIM);

			String status = r.id.equals("sense_filter")
					? (net.mcreator.ordeal.OrdealSilhouette.SHOW_PRESENCE ? "ON" : "OFF")
					: r.passive ? (r.unlocked ? (r.passiveOn ? "ON" : "OFF") : r.lockLabel)
					: r.unlocked ? (isSel ? "SELECTED" : "READY") : r.lockLabel;
			int col = r.id.equals("sense_filter")
					? (net.mcreator.ordeal.OrdealSilhouette.SHOW_PRESENCE ? OrdealDraw.GREEN : OrdealDraw.INK_DIM)
					: r.passive ? (r.unlocked && r.passiveOn ? OrdealDraw.GREEN : OrdealDraw.INK_DIM)
					: isSel ? 0xFFFFB877 : r.unlocked ? OrdealDraw.GREEN : OrdealDraw.INK_DIM;
			OrdealDraw.textRight(g, status, lx + listW - 6, ry + 4, col);

			// The list is ordered by talent strength requirement, so show the
			// requirement on EVERY row - it used to appear only while an ability
			// was locked, which made the order look arbitrary the moment you
			// could use something.
			if (!r.id.equals("sense_filter") && r.req > 0 && r.unlocked) {
				String tag = "T.STR " + r.req;
				OrdealDraw.textRight(g, tag,
						lx + listW - 10 - OrdealDraw.width(status), ry + 4,
						r.unlocked ? 0xFF41677A : OrdealDraw.LOCKED);
			}
		}

		g.disableScissor();
		OrdealDraw.outline(g, lx, ly, listW, viewH, OrdealDraw.CYAN_FAINT);

		if (maxScroll > 0) {
			int tx = lx + listW + 4;
			OrdealDraw.rect(g, tx, ly, SCROLLBAR_W, viewH, 0x1A7ED8F5);
			int thumbH = Math.max(10, viewH * ROWS_VISIBLE / s.abilities.size());
			OrdealDraw.rect(g, tx, ly + (viewH - thumbH) * scroll / maxScroll,
					SCROLLBAR_W, thumbH, OrdealDraw.CYAN_DIM);
		}

		if (hovered != null) OrdealDraw.tooltip(g, tooltipFor(hovered, s), mx, my);
	}

	private static void loadout(GuiGraphics g, int x, int y, Snapshot s, int mx, int my) {
		int lx = x + LOAD_X;
		OrdealDraw.text(g, "LOADOUT", lx, y + SECT_Y, OrdealDraw.CYAN_DIM);
		OrdealDraw.textRight(g, s.selectedName.isEmpty() ? "CLICK FILLED TO CLEAR" : "PICK A SLOT",
				lx + LOAD_W, y + SECT_Y, s.selectedName.isEmpty() ? OrdealDraw.INK_DIM : 0xFFFFB877);

		int gx = lx + (LOAD_W - (5 * SLOT + 4 * SLOT_GAP)) / 2;
		for (int i = 0; i < 10; i++) {
			int sx = gx + (i % 5) * (SLOT + SLOT_GAP);
			int sy = y + SLOTS_Y + (i / 5) * (SLOT + SLOT_GAP);
			String bound = s.loadout[i];
			boolean filled = bound != null && !bound.isEmpty();
			boolean armed = !s.selectedName.isEmpty() && !filled;
			boolean hov = OrdealDraw.inside(mx, my, sx, sy, SLOT, SLOT);

			OrdealTalents.Ability ab = filled ? OrdealTalents.abilityByName(bound) : null;
			OrdealTalents.Talent owner = filled ? OrdealTalents.ownerOfName(bound) : null;
			int accent = owner != null ? owner.accent : OrdealDraw.CYAN_FAINT;
			int frame = filled ? accent : armed ? 0xFFFFB877 : OrdealDraw.CYAN_FAINT;

			OrdealDraw.rect(g, sx, sy, SLOT, SLOT,
					hov ? 0x1F7ED8F5 : armed ? 0x1AFFB877 : 0x14000000);
			OrdealDraw.outline(g, sx, sy, SLOT, SLOT, frame);
			OrdealDraw.brackets(g, sx, sy, SLOT, SLOT, 5, frame);

			if (ab != null && ab.iconTex != null)
				OrdealDraw.icon(g, ab.iconTex, sx + 3, sy + 3, SLOT - 6);
			else if (ab != null)
				OrdealDraw.text(g, ab.icon, sx + (SLOT - OrdealDraw.width(ab.icon)) / 2,
						sy + (SLOT - 8) / 2, accent);

			String n = String.format("%02d", i + 1);
			OrdealDraw.text(g, n, sx + (SLOT - OrdealDraw.width(n)) / 2, sy + SLOT - 9,
					filled ? OrdealDraw.alpha(accent, 0x80) : OrdealDraw.LOCKED);
		}

	}

	private static boolean selectedIsPassive(Snapshot s) {
		for (Row r : s.abilities)
			if (r.id.equals(s.selected)) return r.passive;
		return false;
	}

	private static List<Component> tooltipFor(Row r, Snapshot s) {
		List<Component> out = new ArrayList<>();
		out.add(tint(r.name, r.accent).withStyle(ChatFormatting.BOLD));
		out.add(tint(r.kind, 0xFF4B7D92));

		if (!r.desc.isEmpty()) {
			out.add(Component.empty());
			for (String line : wrap(r.desc, 38))
				out.add(tint(line, 0xFF9FB4C4).withStyle(ChatFormatting.ITALIC));
		}

		List<Component> nums = new ArrayList<>();
		if (r.chi > 0)
			nums.add(stat(r.passive ? "CHI PER USE" : "CHI COST", String.valueOf(
					Math.round(r.chi * (1.0 - Math.min(0.40, s.chiControl * 0.004)))), 0xFF7ED8F5));
		if (r.cdTicks > 0)
			nums.add(stat("COOLDOWN", String.format("%.1f",
					(r.cdTicks / 20.0) * (1.0 - Math.min(0.35, s.agility * 0.0035))) + "s", 0xFF7ED8F5));
		if (r.base != 0 || r.per != 0)
			nums.add(stat("DAMAGE", String.format("%.1f", r.base + r.per * r.scaleStat), 0xFFFF8A5B));
		else if (!r.passive)
			nums.add(stat("DAMAGE", "-", 0xFF4B7D92));
		if (!nums.isEmpty()) {
			out.add(Component.empty());
			out.addAll(nums);
		}

		// Spell the gate out in full and say WHICH kind of requirement it is -
		// a talent's strength and the STRENGTH stat are different numbers and
		// "STR 2" could be either.
		String requires = requirementText(r, s);
		if (!requires.isEmpty()) {
			out.add(Component.empty());
			out.add(stat("REQUIRES", requires, r.unlocked ? 0xFF5FE3A0 : 0xFFFF6B6B));
		}

		out.add(Component.empty());
		out.add(r.unlocked
				? tint(r.passive ? (r.passiveOn ? "> click to turn OFF" : "> click to turn ON") : "> click to select", 0xFF5FE3A0)
				: tint("locked - " + r.lockLabel, 0xFFFF6B6B));
		return out;
	}

	/** "TALENT STRENGTH 2", or the physical stats a basic ability gates on. */
	private static String requirementText(Row r, Snapshot s) {
		StringBuilder b = new StringBuilder();
		if (r.levelNeeded > 0) b.append("LEVEL ").append((int) r.levelNeeded);
		if (r.req > 0) {
			if (b.length() > 0) b.append("   ");
			b.append("TALENT STRENGTH ").append(r.req);
		}
		if (!r.reqStats.isEmpty()) {
			for (var e : r.reqStats.entrySet()) {
				if (b.length() > 0) b.append("   ");
				b.append(longStat(e.getKey())).append(' ').append((int) (double) e.getValue());
			}
		}
		return b.toString();
	}

	private static MutableComponent tint(String text, int rgb) {
		return Component.literal(text)
				.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF)));
	}

	private static MutableComponent stat(String label, String value, int valueRgb) {
		return tint(label, 0xFF3C6478)
				.append(tint("   " + value, valueRgb).withStyle(ChatFormatting.BOLD));
	}

	private static List<String> wrap(String text, int max) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (line.length() + word.length() + 1 > max && line.length() > 0) {
				lines.add(line.toString());
				line = new StringBuilder();
			}
			if (line.length() > 0) line.append(' ');
			line.append(word);
		}
		if (line.length() > 0) lines.add(line.toString());
		return lines;
	}

	private static int rowAt(int x, int y, double mx, double my, int count) {
		int lx = x + LIST_X;
		int ly = y + LIST_Y;
		if (!OrdealDraw.inside(mx, my, lx, ly, LIST_W - SCROLLBAR_W - 4, ROWS_VISIBLE * LROW_H)) return -1;
		int idx = (int) ((my - ly) / LROW_H) + scroll;
		return (idx >= 0 && idx < count) ? idx : -1;
	}

	// ---- data ---------------------------------------------------------------

	public static class Row {
		public String id, icon, name, kind, desc = "", lockLabel = "";
		public boolean passive, passiveOn;
		public net.minecraft.resources.ResourceLocation iconTex;
		public boolean unlocked;
		public int req, accent;
		public double chi, cdTicks, base, per, scaleStat;
		public int levelNeeded;
		/** Physical stat gates, for the basic abilities. Empty for talent ones. */
		public java.util.Map<String, Double> reqStats = new java.util.LinkedHashMap<>();
	}

	private static double statByName(Snapshot s, String name) {
		switch (name.toLowerCase()) {
			case "strength": return s.stats[0];
			case "durability": return s.stats[1];
			case "agility": return s.stats[2];
			case "health": return s.stats[3];
			case "chi": return s.stats[4];
			case "chicontrol": return s.stats[5];
			case "perception": return s.stats[6];
			default: return 0;
		}
	}

	private static String longStat(String name) {
		switch (name.toLowerCase()) {
			case "strength": return "STRENGTH";
			case "durability": return "DURABILITY";
			case "agility": return "AGILITY";
			case "health": return "HEALTH";
			case "chi": return "CHI";
			case "chicontrol": return "CHI CONTROL";
			case "perception": return "PERCEPTION";
			default: return name.toUpperCase();
		}
	}

	private static String shortStat(String name) {
		switch (name.toLowerCase()) {
			case "strength": return "STR";
			case "durability": return "DUR";
			case "agility": return "AGI";
			case "health": return "HP";
			case "chi": return "CHI";
			case "chicontrol": return "CTL";
			case "perception": return "PER";
			default: return name.toUpperCase();
		}
	}

	public static class Snapshot {
		public String playerName = "", family = "-", clan = "-", selected = "", selectedName = "", talentSummary = "";
		public double level, xp, xpCap, sp, spUsed, spCap, talentSp, tspUsed, tspCap, chiLimit, chiControl, agility;
		public double limitMax = OrdealStats.LIMIT_MAX;
		public double conceal, chiPct, chiMaxFull, nextSp, nextTsp;
		public String talentShort = "NONE", talentBrief = "";
		public int talentAccent = OrdealDraw.CYAN;
		public double totalStrength, activeStrength;
		public int bloodDoses, activeSlot;
		public boolean isKimyo;
		public double[] stats = new double[7];
		public double[] strengths = new double[0];
		public int[] accents = new int[0];
		public String[] talentNames = new String[0];
		public String[] loadout = new String[10];
		public List<OrdealTalents.Talent> tabs = new ArrayList<>();
		/** Every trait in the mod, the ones you hold sorted to the front. */
		public List<OrdealTraits.Trait> traits = new ArrayList<>();
		public boolean[] traitOwned = new boolean[0];
		public int traitsOwned = 0;
		public int[] tabSlot = new int[0];
		public List<Row> abilities = new ArrayList<>();

		public static Snapshot read(Player player) {
			OrdealModVariables.PlayerVariables v =
					player.getData(OrdealModVariables.PLAYER_VARIABLES);

			Snapshot s = new Snapshot();
			s.playerName = player.getGameProfile().getName();

			// traits: complete list, acquired first, then by name
			List<OrdealTraits.Trait> tl = new ArrayList<>(OrdealTraits.all());
			tl.sort(java.util.Comparator
					.comparing((OrdealTraits.Trait t) -> OrdealTraits.has(v, t) ? 0 : 1)
					.thenComparing(t -> t.name));
			s.traits = tl;
			s.traitOwned = new boolean[tl.size()];
			for (int i = 0; i < tl.size(); i++) {
				s.traitOwned[i] = OrdealTraits.has(v, tl.get(i));
				if (s.traitOwned[i]) s.traitsOwned++;
			}

			s.level      = v.level;
			s.xp         = v.xp;
			s.xpCap      = v.xpCap;
			s.sp         = v.sp;
			s.spCap      = v.spLifetime_Cap;
			s.spUsed     = v.spLifetime;
			s.tspUsed    = v.talentSP_Lifetime;
			s.tspCap     = v.talentSp_Lifetime_Cap;
			s.conceal    = v.ChiConcealed;
			double nl = Math.min(100, v.level + 1);
			s.nextSp  = Math.floor(v.spLifetime_Cap * nl / 100.0) - Math.floor(v.spLifetime_Cap * (nl - 1) / 100.0);
			s.nextTsp = Math.floor(v.talentSp_Lifetime_Cap * nl / 100.0) - Math.floor(v.talentSp_Lifetime_Cap * (nl - 1) / 100.0);
			s.chiMaxFull = Math.max(1, v.statChi * 4);
			s.chiPct     = Math.round(100.0 * v.chi / Math.max(1, v.chiMax));
			s.talentSp   = v.talentSP;
			s.chiLimit   = v.chiLimit;
			s.limitMax   = Math.max(OrdealStats.LIMIT_MAX, v.chiLimit);
			s.chiControl = v.statChiControl;
			s.agility    = v.statAgility;
			s.bloodDoses = (int) v.bloodConsumed;
			s.selected   = v.ability_select == null || v.ability_select.equals("none")
					? "" : v.ability_select;

			s.family = v.family == null || v.family.isEmpty() ? "UNREGISTERED" : v.family.toUpperCase();
			s.clan   = v.clan == null || v.clan.isEmpty() ? "NONE" : v.clan.toUpperCase();

			s.stats[0] = v.statStrength;
			s.stats[1] = v.statDurability;
			s.stats[2] = v.statAgility;
			s.stats[3] = v.statHealth;
			s.stats[4] = v.statChi;
			s.stats[5] = v.statChiControl;
			s.stats[6] = v.statPerception;

			s.loadout[0] = v.loadout_1; s.loadout[1] = v.loadout_2; s.loadout[2] = v.loadout_3;
			s.loadout[3] = v.loadout_4; s.loadout[4] = v.loadout_5; s.loadout[5] = v.loadout_6;
			s.loadout[6] = v.loadout_7; s.loadout[7] = v.loadout_8; s.loadout[8] = v.loadout_9;
			s.loadout[9] = v.loadout_10;

			OrdealTalents.Talent t1 = OrdealTalents.get(v.talent1_id);
			OrdealTalents.Talent t2 = OrdealTalents.get(v.talent2_id);

			// Basic always leads.
			List<Integer> slots = new ArrayList<>();
			s.tabs.add(OrdealTalents.basic()); slots.add(0);

			OrdealTalents.Talent enh = OrdealTalents.get(ENHANCEMENT_TAB);
			// The whole tab stays hidden until an enhancement is actually bonded.
			if (enh != null && !enh.abilities.isEmpty()
					&& net.mcreator.ordeal.Enhancements.any(player)) { s.tabs.add(enh); slots.add(-1); }

			List<String> names = new ArrayList<>();
			List<Double> strs = new ArrayList<>();
			List<Integer> cols = new ArrayList<>();
			if (t1 != null) { s.tabs.add(t1); slots.add(1); names.add(t1.shortName); strs.add(v.talent1_strength); cols.add(t1.accent); }
			if (t2 != null) { s.tabs.add(t2); slots.add(2); names.add(t2.shortName); strs.add(v.talent2_strength); cols.add(t2.accent); }

			s.tabSlot = new int[slots.size()];
			for (int i = 0; i < slots.size(); i++) s.tabSlot[i] = slots.get(i);

			s.isKimyo = "kimyo".equals(v.race) || t1 != null || t2 != null;
			int count = (t1 != null ? 1 : 0) + (t2 != null ? 1 : 0);
			s.talentSummary = s.isKimyo
					? count + " REGISTERED - " + (int) s.talentSp + " TALENT SP"
					: "NONE REGISTERED - BASIC ONLY";

			s.talentNames = names.toArray(new String[0]);
			if (t1 != null) {
				s.talentShort = t1.shortName + (t2 != null ? " +1" : "");
				s.talentAccent = t1.accent;
			}
			s.talentBrief = s.isKimyo
					? count + " REG - " + (int) s.talentSp + " TSP"
					: "BASIC ONLY";
			s.strengths = new double[strs.size()];
			s.accents = new int[cols.size()];
			for (int i = 0; i < strs.size(); i++) {
				s.strengths[i] = strs.get(i);
				s.accents[i] = cols.get(i);
				s.totalStrength += strs.get(i);
			}

			OrdealTalents.Ability sel = OrdealTalents.ability(s.selected);
			s.selectedName = sel != null ? sel.name : "";

			int tab = Math.min(activeTab, s.tabs.size() - 1);
			OrdealTalents.Talent active = s.tabs.get(tab);
			s.activeSlot = s.tabSlot[tab];
			s.activeStrength = s.activeSlot == 1 ? v.talent1_strength
					: s.activeSlot == 2 ? v.talent2_strength
					: s.activeSlot == -1 ? Math.max(v.talent1_strength, v.talent2_strength) : 0;

			if (active.id.equals("basic")) {
				// free utility, always first: toggles seeing presence glow
				Row sf = new Row();
				sf.id = "sense_filter"; sf.icon = "\u25C8"; sf.name = "SENSE FILTER";
				sf.kind = "TOGGLE"; sf.desc = "Turn the presence read on or off. Free.";
				sf.unlocked = true; sf.accent = active.accent;
				s.abilities.add(sf);
			}
			for (OrdealTalents.Ability a : active.abilities) {
				// Enhancements are earned, not advertised: an enhancement row
				// only exists once the player is actually bonded to it. This
				// filters them out of EVERY tab they might be listed in.
				if (net.mcreator.ordeal.Enhancements.valid(a.id)
						&& !net.mcreator.ordeal.Enhancements.has(player, a.id)) continue;
				Row r = new Row();
				r.id = a.id; r.icon = a.icon; r.name = a.name; r.kind = a.kind; r.desc = a.desc; r.iconTex = a.iconTex;
				r.req = a.req;
				r.reqStats.putAll(a.reqStats);
				r.chi = a.chi; r.cdTicks = a.cdTicks; r.base = a.base; r.per = a.per;
				r.levelNeeded = a.levelNeeded;
				r.accent = active.accent;
				r.passive = (a.kind != null && a.kind.toUpperCase(java.util.Locale.ROOT).contains("PASSIVE"))
						|| net.mcreator.ordeal.Enhancements.valid(a.id);
				r.passiveOn = net.mcreator.ordeal.Passives.onClient(a.id);
				// Stat requirements gate EVERY ability, not just the basic
				// talent's. This check used to live inside the basic branch
				// only, so a talent ability printed "AGILITY 8" in its REQUIRES
				// line, coloured it green and offered "click to select" while
				// your agility sat at 0 - the requirement was displayed and
				// never enforced.
				StringBuilder missing = new StringBuilder();
				boolean statsOk = true;
				for (var e : a.reqStats.entrySet()) {
					if (statByName(s, e.getKey()) >= e.getValue()) continue;
					statsOk = false;
					if (missing.length() > 0) missing.append("  ");
					missing.append(shortStat(e.getKey())).append(' ')
							.append((int) (double) e.getValue());
				}

				if (active.id.equals("basic")) {
					r.unlocked = statsOk;
					r.lockLabel = missing.toString();
					r.scaleStat = v.statStrength;
				} else {
					boolean lvlOk = v.level >= a.levelNeeded;
					boolean strOk = s.activeStrength >= a.req;
					r.unlocked = strOk && lvlOk && statsOk;
					// Report the first gate you fail, in the order you would
					// clear them. "STR" alone reads as the STRENGTH stat, so the
					// talent's own strength is spelled T.STR.
					r.lockLabel = !lvlOk ? "LVL " + a.levelNeeded
							: !strOk ? "T.STR " + a.req
							: missing.toString();
					r.scaleStat = s.activeStrength;
				}
				s.abilities.add(r);
			}
			return s;
		}
	}
}