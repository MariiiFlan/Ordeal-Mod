package net.mcreator.ordeal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.mcreator.ordeal.OrdealAnimData.Ease;
import net.mcreator.ordeal.OrdealAnimData.Key;
import net.mcreator.ordeal.OrdealAnimData.Pose;

/**
 * Ordeal Animator — Third-Person Animation Editor.
 *
 * Layout: top toolbar · floating BONE + EASING windows · bottom transport
 * + multi-track dope sheet. The dummy model renders in the world behind
 * this screen (OrdealAnimatorClient); this screen never pauses the game
 * and never blurs the background.
 *
 * Controls:
 *   click model part = select bone · drag gizmo ring/arrow = rotate/move
 *   R / T = rotate / move gizmo · drag empty space = orbit the camera
 *   hold right = free look · middle-drag = pan · scroll = zoom · WASD/QE = fly · C = recenter
 *   Space = play/pause · ←/→ = step frame · Ctrl+←/→ = prev/next key
 *   double-click track = add key · Del = delete keys · Ctrl+C/V = copy/paste
 */
public class OrdealAnimatorScreen extends Screen {

	// ------------------------------------------------------------------
	// Theme
	// ------------------------------------------------------------------

	private static boolean darkTheme = true;

	private int colPanel() { return darkTheme ? 0xE0101014 : 0xE01E2430; }
	private int colPanelHead() { return darkTheme ? 0xF01A1A20 : 0xF02A3242; }
	private int colBorder() { return darkTheme ? 0xFF3A3A44 : 0xFF4A5A72; }
	private int colBtn() { return darkTheme ? 0xFF23232B : 0xFF2E3A50; }
	private int colBtnHot() { return darkTheme ? 0xFF34343F : 0xFF3E4E68; }
	private int colBtnOn() { return 0xFF2E6BD6; }
	private int colText() { return 0xFFE8E8E8; }
	private int colDim() { return 0xFF9A9AA6; }

	/** Same colour, alpha forced to solid - for panels that must block what is under them. */
	private static int opaque(int argb) { return 0xFF000000 | (argb & 0x00FFFFFF); }
	private static final int COL_PLAYHEAD = 0xFFE03A3A;
	private static final int COL_AUTOKEY = 0xFF2FA84F;

	private static final int[] TRACK_COLOR = {
			0xFFEAEAEA, 0xFF6FA8FF, 0xFFF0B840, 0xFFF0D060, 0xFF58D6A0, 0xFF58BCD6, 0xFFB07FE8
	};

	// ------------------------------------------------------------------
	// Floating windows
	// ------------------------------------------------------------------

	private static class Win {
		int x, y, w, h;
		final String title;
		boolean minimized = false, hidden = false;

		Win(String title, int x, int y, int w, int h) {
			this.title = title;
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}

		int drawH() { return minimized ? 12 : h; }

		boolean overTitle(double mx, double my) {
			return !hidden && mx >= x && mx <= x + w && my >= y && my <= y + 12;
		}

		boolean over(double mx, double my) {
			return !hidden && mx >= x && mx <= x + w && my >= y && my <= y + drawH();
		}
	}

	/** Buttons on the first row, clip name + control hint on the second. */
	private static final int TOOLBAR_H = 34;

	private final Win boneWin = new Win("BONE", 8, TOOLBAR_H + 4, 150, 178);
	private final Win easeWin = new Win("EASING", 0, TOOLBAR_H + 4, 168, 210); // x set in init

	// ------------------------------------------------------------------
	// Editor state
	// ------------------------------------------------------------------

	private boolean playing = false;
	private long lastNanos = 0;
	private float pxPerTick = 6f;
	private float scrollTicks = 0f;
	private boolean snap = true;
	private boolean autoKey = true;
	/**
	 * The Loop button.
	 *
	 * This used to be a screen-local flag that only affected the editor's own
	 * preview - it never reached the clip. So every animation saved with
	 * loop = false, and playing it in game ran it once and stopped, no matter
	 * how the button looked. It is the clip's own flag now.
	 */
	private boolean looping() {
		OrdealAnimData d = data();
		return d != null && d.loop;
	}

	/** 0 play once, 1 loop, 2 hold last frame. */
	private int endMode() {
		OrdealAnimData d = data();
		return d == null ? 0 : d.endMode();
	}

	private static class Sel {
		final String bone;
		final Key key;

		Sel(String bone, Key key) {
			this.bone = bone;
			this.key = key;
		}
	}

	private final List<Sel> selection = new ArrayList<>();
	private Map<String, List<Key>> clipboard = null;

	// drag state
	private static final int DRAG_NONE = 0, DRAG_WIN = 1, DRAG_SLIDER = 2, DRAG_GIZMO = 3,
			DRAG_KEYS = 4, DRAG_BOX = 5, DRAG_PLAYHEAD = 6, DRAG_ORBIT = 7, DRAG_MOVEAXIS = 8,
			DRAG_PAN = 9, DRAG_LOOK = 10;
	private int dragMode = DRAG_NONE;
	private Win dragWin = null;
	private int dragOffX, dragOffY;
	private int dragSlider = -1; // 0..5 = pitch,yaw,roll,x,y,z
	/** Last slider clicked and when, so a second click on it means "zero this". */
	private int lastSlider = -1;
	private long lastSliderClick = 0;
	private double lastMx, lastMy;
	private int gizmoAxis = -1;
	private double gizmoLastAngle = 0;
	private double boxX0, boxY0, boxX1, boxY1;
	private float keysDragAccum = 0f;
	private String flash = "";
	private long flashUntil = 0;

	private long lastClickMs = 0;
	private double lastClickX = -1000, lastClickY = -1000;

	// right-click context menu on a keyframe
	private static final String[] CTX_ITEMS = { "Duplicate", "Copy", "Paste here", "Delete" };
	private boolean ctxOpen = false;
	private int ctxXp, ctxYp;
	private float ctxTime;

	// modal: 0 none, 1 new-name, 2 load list, 3 import picker
	private int modal = 0;
	private EditBox nameBox;
	private List<String> loadList = new ArrayList<>();
	/**
	 * What the browser actually draws: the same names, split into a first-person
	 * group and a third-person one with a heading above each. A heading row is
	 * stored with HDR in front of it and is never selectable.
	 */
	private List<String> loadRows = new ArrayList<>();
	private static final String HDR = "\u0000";
	private int loadScroll = 0;

	// import picker: which animations out of a multi-clip Blockbench file to take
	private OrdealBlockbench.Pick importPick = null;
	private boolean[] importSel = new boolean[0];
	private int importScroll = 0;
	private static final int IMPORT_ROWS = 10;

	// load browser: the highlighted clip plays live on the dummy until you
	// pick Load (keep it) or leave (put the old clip back).
	private String previewName = null;
	private String backupJson = null, backupName = null;
	private float backupTime = 0f;
	private boolean deleteArm = false;
	/** What the name box is for: 0 = new clip, 1 = rename, 2 = duplicate. */
	private int nameMode = 0;
	private String nameTarget = "";

	// dope sheet geometry (computed per frame)
	private int dsTop, dsLabelW = 64, trackH = 13, rulerH = 12;

	public OrdealAnimatorScreen() {
		super(Component.literal("Ordeal Animation Editor"));
		OrdealOrbitCam.begin();
	}

	@Override
	protected void init() {
		easeWin.x = this.width - easeWin.w - 8;
		if (easeWin.x < 200)
			easeWin.x = 200;
		nameBox = new EditBox(this.font, this.width / 2 - 70, this.height / 2 - 6, 140, 14, Component.literal(""));
		nameBox.setMaxLength(32);
	}

	// ---- undo / redo --------------------------------------------------------

	private final java.util.ArrayDeque<String> undoStack = new java.util.ArrayDeque<>();
	private final java.util.ArrayDeque<String> redoStack = new java.util.ArrayDeque<>();
	private static final int HISTORY_MAX = 64;

	/** Snapshot before a mutation. Cheap — the model is small JSON. */
	private void pushUndo() {
		OrdealAnimData d = data();
		if (d == null) return;
		undoStack.push(d.toJson());
		while (undoStack.size() > HISTORY_MAX) undoStack.removeLast();
		redoStack.clear();
	}

	private void undo() {
		OrdealAnimData d = data();
		if (d == null || undoStack.isEmpty()) { flash("Nothing to undo"); return; }
		redoStack.push(d.toJson());
		restore(undoStack.pop());
		flash("Undo");
	}

	private void redo() {
		OrdealAnimData d = data();
		if (d == null || redoStack.isEmpty()) { flash("Nothing to redo"); return; }
		undoStack.push(d.toJson());
		restore(redoStack.pop());
		flash("Redo");
	}

	private void restore(String json) {
		OrdealAnimData d = OrdealAnimData.fromJson(json);
		if (d == null) return;
		OrdealAnimatorClient.data = d;
		OrdealAnimatorClient.livePose.clear();
		selection.clear();
		// The playhead stays where you left it. It used to clamp to the clip
		// length, so undoing the last key shortened the clip and threw you back
		// to 0:00 - undo should take back the edit, not your place in the
		// timeline.
	}

	private void resetWindows() {
		boneWin.x = 8; boneWin.y = TOOLBAR_H + 4; boneWin.minimized = false; boneWin.hidden = false;
		easeWin.x = this.width - easeWin.w - 8; easeWin.y = TOOLBAR_H + 4; easeWin.minimized = false; easeWin.hidden = false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// none — world stays visible, no blur
	}

	@Override
	public void onClose() {
		OrdealAnimatorClient.close();
		super.onClose();
	}

	@Override
	public void removed() {
		if (previewName != null) restorePreview();
		OrdealOrbitCam.end();
		super.removed();
	}

	private OrdealAnimData data() {
		return OrdealAnimatorClient.data;
	}

	// ==================================================================
	// RENDER
	// ==================================================================

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		advancePlayback();
		// wobble and living motion only ride the dummy while the timeline runs -
		// posing against a model that is swaying is miserable
		OrdealAnimatorClient.previewLayers = playing;
		if (modal == 0) OrdealOrbitCam.pollKeys();
		dsTop = this.height - (rulerH + OrdealAnimatorClient.bones().length * trackH + 22);
		// keep the dummy centred in the strip of world the panels leave visible
		OrdealOrbitCam.frameShift =
				(this.height / 2f - (TOOLBAR_H + dsTop - 20) / 2f) / Math.max(1, this.height);

		renderToolbar(g, mouseX, mouseY);
		renderTransport(g, mouseX, mouseY);
		renderDopeSheet(g, mouseX, mouseY);
		renderWin(g, boneWin, mouseX, mouseY);
		renderWin(g, easeWin, mouseX, mouseY);

		if (dragMode == DRAG_BOX) {
			int x0 = (int) Math.min(boxX0, boxX1), x1 = (int) Math.max(boxX0, boxX1);
			int y0 = (int) Math.min(boxY0, boxY1), y1 = (int) Math.max(boxY0, boxY1);
			g.fill(x0, y0, x1, y1, 0x302E6BD6);
			g.fill(x0, y0, x1, y0 + 1, 0xFF2E6BD6);
			g.fill(x0, y1 - 1, x1, y1, 0xFF2E6BD6);
			g.fill(x0, y0, x0 + 1, y1, 0xFF2E6BD6);
			g.fill(x1 - 1, y0, x1, y1, 0xFF2E6BD6);
		}

		if (modal == 1)
			renderNameModal(g, mouseX, mouseY);
		else if (modal == 2)
			renderLoadModal(g, mouseX, mouseY);
		else if (modal == 3)
			renderImportModal(g, mouseX, mouseY);

		if (ctxOpen)
			renderCtxMenu(g, mouseX, mouseY);

		String camNote = OrdealOrbitCam.status();
		if (camNote != null)
			g.drawCenteredString(this.font, camNote, this.width / 2, TOOLBAR_H + 20, 0xFFFF5050);

		if (System.currentTimeMillis() < flashUntil)
			g.drawCenteredString(this.font, flash, this.width / 2, TOOLBAR_H + 6, 0xFFFFD860);

		super.render(g, mouseX, mouseY, partialTick);
	}

	// ------------------------------------------------------------------
	// Keyframe context menu
	// ------------------------------------------------------------------

	private void renderCtxMenu(GuiGraphics g, int mx, int my) {
		int w = 78, rowH = 13, h = CTX_ITEMS.length * rowH + 4;
		int x = Math.min(ctxXp, this.width - w - 2), y = Math.min(ctxYp, this.height - h - 2);
		g.fill(x, y, x + w, y + h, 0xF2141418);
		g.fill(x, y, x + w, y + 1, colBorder());
		g.fill(x, y + h - 1, x + w, y + h, colBorder());
		g.fill(x, y, x + 1, y + h, colBorder());
		g.fill(x + w - 1, y, x + w, y + h, colBorder());
		for (int i = 0; i < CTX_ITEMS.length; i++) {
			int ry = y + 2 + i * rowH;
			boolean hot = mx >= x && mx <= x + w && my >= ry && my < ry + rowH;
			boolean dim = i == 2 && clipboard == null;
			if (hot && !dim) g.fill(x + 1, ry, x + w - 1, ry + rowH, colBtnHot());
			g.drawString(this.font, CTX_ITEMS[i], x + 6, ry + 2, dim ? colDim() : colText());
		}
	}

	private int ctxHit(double mx, double my) {
		int w = 78, rowH = 13, h = CTX_ITEMS.length * rowH + 4;
		int x = Math.min(ctxXp, this.width - w - 2), y = Math.min(ctxYp, this.height - h - 2);
		if (mx < x || mx > x + w || my < y + 2 || my > y + h - 2) return -1;
		int i = (int) ((my - y - 2) / rowH);
		return i >= 0 && i < CTX_ITEMS.length ? i : -1;
	}

	private void ctxAction(int idx) {
		OrdealAnimData d = data();
		if (d == null) return;
		switch (idx) {
			case 0 -> { // duplicate the selected keys one frame later
				if (selection.isEmpty()) return;
				pushUndo();
				List<Sel> dup = new ArrayList<>();
				for (Sel sl : selection) {
					Key k = sl.key.copy();
					k.t = sl.key.t + 1;
					d.putKey(sl.bone, k);
					dup.add(new Sel(sl.bone, k));
				}
				selection.clear();
				selection.addAll(dup);
				flash("Duplicated " + dup.size() + " key(s)");
			}
			case 1 -> copySelected();
			case 2 -> { // paste the clipboard where you right-clicked
				if (clipboard == null) return;
				pushUndo();
				float base = Math.max(0, snap ? Math.round(ctxTime) : ctxTime);
				selection.clear();
				for (Map.Entry<String, List<Key>> e : clipboard.entrySet())
					for (Key src : e.getValue()) {
						Key k = src.copy();
						k.t = base + src.t;
						d.putKey(e.getKey(), k);
						selection.add(new Sel(e.getKey(), k));
					}
				flash("Pasted");
			}
			case 3 -> deleteSelected();
		}
	}

	private void advancePlayback() {
		long now = System.nanoTime();
		if (playing && lastNanos != 0 && data() != null) {
			float dt = (now - lastNanos) / 1_000_000_000f;
			float len = data().length();
			OrdealAnimatorClient.time += dt * data().fps * data().speed;
			if (len <= 0) {
				OrdealAnimatorClient.time = 0;
			} else if (OrdealAnimatorClient.time > len) {
				if (looping() || previewName != null)
					OrdealAnimatorClient.time %= len;
				else {
					OrdealAnimatorClient.time = len;
					playing = false;
				}
			}
		}
		lastNanos = now;
	}

	// ------------------------------------------------------------------
	// Toolbar
	// ------------------------------------------------------------------

	private final List<int[]> toolbarHits = new ArrayList<>(); // {x0,y0,x1,y1,id}

	private void renderToolbar(GuiGraphics g, int mx, int my) {
		toolbarHits.clear();
		g.fill(0, 0, this.width, TOOLBAR_H, colPanel());
		g.fill(0, TOOLBAR_H - 1, this.width, TOOLBAR_H, colBorder());
		int x = 4;
		x = tbBtn(g, x, "New", 1, mx, my, false);
		x = tbBtn(g, x, "Save", 2, mx, my, false);
		x = tbBtn(g, x, "Load", 3, mx, my, false);
		x = tbBtn(g, x, "Import", 15, mx, my, false);
		x = tbBtn(g, x, "Export", 16, mx, my, false);
		x += 6;
		x = tbBtn(g, x, "-", 4, mx, my, false);
		String zl = (int) (pxPerTick / 6f * 100) + "%";
		g.drawString(this.font, zl, x + 2, 7, colDim());
		x += this.font.width(zl) + 6;
		x = tbBtn(g, x, "+", 5, mx, my, false);
		x += 6;
		x = tbBtn(g, x, "Reset Win", 6, mx, my, false);
		x = tbBtn(g, x, "Theme", 7, mx, my, false);
		x += 6;
		x = tbBtn(g, x, "Play In-Game", 8, mx, my, false);
		x = tbBtn(g, x, "Center Cam (C)", 14, mx, my, false);
		OrdealAnimData nd = data();
		// noise is the first-person setting, wobble the third-person one, so
		// only the one that does anything in this view is shown
		if (OrdealAnimatorClient.firstPerson)
			x = tbBtn(g, x, "Noise: " + OrdealAnimNoise.name(nd == null ? 0 : nd.noise), 17, mx, my,
					nd != null && nd.noise > 0);
		else
			x = tbBtn(g, x, "Wobble: " + OrdealAnimLean.name(nd == null ? 0 : nd.wobble), 19, mx, my,
					nd != null && nd.wobble > 0);
		x = tbBtn(g, x, OrdealAnimatorClient.firstPerson ? "View: 1st" : "View: 3rd", 18, mx, my,
				OrdealAnimatorClient.firstPerson);
		x = tbBtn(g, x, "Flip", 21, mx, my, false);

		g.fill(0, 21, this.width, 22, colBorder());
		g.drawString(this.font, OrdealAnimatorClient.clipName, 5, 24, 0xFFFFC857);
		String hint = "drag = orbit · right = look · middle = pan · scroll = zoom · WASD/QE = fly · C = centre";
		g.drawString(this.font, hint, this.width - this.font.width(hint) - 5, 24, colDim());

		int rx = this.width - 4;
		rx = tbBtnRight(g, rx, "Reset", 13, mx, my);
		rx = tbBtnRight(g, rx, "Straight", 12, mx, my);
		rx = tbBtnRight(g, rx, "Grab", 11, mx, my);
		rx = tbBtnRight(g, rx, "R→L", 10, mx, my);
		rx = tbBtnRight(g, rx, "L→R", 9, mx, my);
	}

	private int tbBtn(GuiGraphics g, int x, String label, int id, int mx, int my, boolean on) {
		int w = this.font.width(label) + 10;
		boolean hot = mx >= x && mx <= x + w && my >= 3 && my <= 19;
		g.fill(x, 3, x + w, 19, on ? colBtnOn() : hot ? colBtnHot() : colBtn());
		g.drawCenteredString(this.font, label, x + w / 2, 7, colText());
		toolbarHits.add(new int[]{x, 3, x + w, 19, id});
		return x + w + 3;
	}

	private int tbBtnRight(GuiGraphics g, int rightX, String label, int id, int mx, int my) {
		int w = this.font.width(label) + 10;
		int x = rightX - w;
		tbBtn(g, x, label, id, mx, my, false);
		return x - 3;
	}

	private void toolbarClick(int id) {
		switch (id) {
			case 1 -> { nameMode = 0; modal = 1; nameBox.setValue(""); nameBox.setFocused(true); }
			case 2 -> {
				OrdealAnimPlayback.invalidate();
				if (data() != null && OrdealAnimStore.save(OrdealAnimatorClient.clipName, data()))
					flash(OrdealAnimStore.inMod(OrdealAnimatorClient.clipName)
							? "Saved " + OrdealAnimatorClient.clipName + " into the mod's assets"
							: "Saved " + OrdealAnimatorClient.clipName + " (config only - no source tree found)");
				else
					flash("Save failed");
			}
			case 3 -> openLoad();
			case 4 -> pxPerTick = Math.max(2f, pxPerTick - 2f);
			case 5 -> pxPerTick = Math.min(24f, pxPerTick + 2f);
			case 6 -> resetWindows();
			case 7 -> darkTheme = !darkTheme;
			case 8 -> { // save, close, and run the clip on yourself for real
				if (data() != null) {
					OrdealAnimPlayback.invalidate();
					OrdealAnimStore.save(OrdealAnimatorClient.clipName, data());
					net.neoforged.neoforge.network.PacketDistributor.sendToServer(
							new net.mcreator.ordeal.core.OrdealActionMessage(
									"anim", OrdealAnimatorClient.clipName, 1));
					this.onClose();
				}
			}
			case 9 -> { mirrorClip(true); OrdealAnimatorClient.selBone = OrdealAnimatorClient.firstPerson ? "fp_left" : "left_arm"; flash("Mirrored L→R"); }
			case 10 -> { mirrorClip(false); OrdealAnimatorClient.selBone = OrdealAnimatorClient.firstPerson ? "fp_right" : "right_arm"; flash("Mirrored R→L"); }
			case 11 -> OrdealAnimatorClient.rotateMode = false; // Grab = move gizmo
			case 12 -> { // Straight = selected keys -> linear easing
				for (Sel s : selection)
					s.key.ease = Ease.LINEAR;
				flash("Easing → Linear");
			}
			case 21 -> { flipSides(); }
			case 13 -> resetSelectedBoneAtPlayhead();
			case 14 -> { OrdealOrbitCam.center(); flash("Camera centered on the dummy"); }
			case 15 -> {
				// a Blockbench file usually holds several animations - pick which
				OrdealBlockbench.Pick pick = OrdealBlockbench.importPick();
				if (pick == null) {
					// cancelled, or a single-clip format that imported straight in
				} else if (pick.message != null) {
					flash(pick.message);
				} else {
					importPick = pick;
					importSel = new boolean[pick.names.size()];
					if (importSel.length == 1) importSel[0] = true;
					importScroll = 0;
					modal = 3;
				}
			}
			case 16 -> { String m = OrdealBlockbench.exportFile(); if (m != null) flash(m); }
			case 17 -> { // living-motion level, saved with the clip
				OrdealAnimData nd = data();
				if (nd != null) {
					nd.noise = OrdealAnimNoise.next(nd.noise);
					flash("Living motion: " + OrdealAnimNoise.name(nd.noise));
				}
			}
			case 18 -> { // swap between the body tracks and the first-person ones
				OrdealAnimatorClient.firstPerson = !OrdealAnimatorClient.firstPerson;
				String msg;
				if (OrdealAnimatorClient.firstPerson) {
					boolean seeded = OrdealAnimatorClient.seedFirstPerson();
					OrdealAnimatorClient.selBone = "fp_right";
					msg = seeded ? "First person - laid out from the body swing, now edit it"
							: "First person - its own tracks. Drag to look.";
				} else {
					OrdealAnimatorClient.selBone = "right_arm";
					msg = "Third person";
				}
				OrdealOrbitCam.center();
				flash(msg);
			}
			case 19 -> { // WASD lean level, saved with the clip
				OrdealAnimData nd = data();
				if (nd != null) {
					nd.wobble = OrdealAnimLean.next(nd.wobble);
					flash("Wobble: " + OrdealAnimLean.name(nd.wobble));
				}
			}
		}
	}

	private void flash(String msg) {
		flash = msg;
		flashUntil = System.currentTimeMillis() + 1600;
	}

	// ------------------------------------------------------------------
	// Transport + status
	// ------------------------------------------------------------------

	private final List<int[]> transportHits = new ArrayList<>();

	private void renderTransport(GuiGraphics g, int mx, int my) {
		transportHits.clear();
		int y = dsTop - 20;
		g.fill(0, y, this.width, dsTop, colPanel());
		g.fill(0, y, this.width, y + 1, colBorder());

		OrdealAnimData d = data();
		String status = d == null ? "" : d.keyCount() + " keyframes · "
				+ String.format("%.2fs", OrdealAnimatorClient.time / d.fps) + " · "
				+ (int) d.fps + " fps · speed " + String.format("%.2fx", d.speed);
		g.drawString(this.font, status, 6, y + 6, colDim());
		int sx = 6 + this.font.width(status) + 6;
		sx = trBtn(g, sx, y, "−", 20, mx, my, false);
		sx = trBtn(g, sx, y, "+", 21, mx, my, false);

		int cx = this.width / 2 - 90;
		cx = trBtn(g, cx, y, "|«", 30, mx, my, false);
		cx = trBtn(g, cx, y, "«", 31, mx, my, false);
		cx = trBtn(g, cx, y, "‹", 32, mx, my, false);
		cx = trBtn(g, cx, y, playing ? "❚❚" : "►", 33, mx, my, playing);
		cx = trBtn(g, cx, y, "›", 34, mx, my, false);
		cx = trBtn(g, cx, y, "»", 35, mx, my, false);
		cx = trBtn(g, cx, y, "»|", 36, mx, my, false);
		cx = trBtn(g, cx, y, "■", 37, mx, my, false);

		int rx = this.width - 4;
		rx = trBtnRightToggle(g, rx, y, "Auto-Key", 42, autoKey, COL_AUTOKEY, mx, my);
		rx = trBtnRightToggle(g, rx, y, "Snap", 41, snap, colBtnOn(), mx, my);
		// one button, three states - a clip either runs once, repeats, or stops
		// on its final pose and stays there
		int em = endMode();
		rx = trBtnRightToggle(g, rx, y, OrdealAnimData.endName(em), 40, em != 0,
				em == 1 ? colBtnOn() : COL_AUTOKEY, mx, my);
	}

	private int trBtn(GuiGraphics g, int x, int y, String label, int id, int mx, int my, boolean on) {
		int w = Math.max(16, this.font.width(label) + 8);
		boolean hot = mx >= x && mx <= x + w && my >= y + 3 && my <= y + 17;
		g.fill(x, y + 3, x + w, y + 17, on ? colBtnOn() : hot ? colBtnHot() : colBtn());
		g.drawCenteredString(this.font, label, x + w / 2, y + 6, colText());
		transportHits.add(new int[]{x, y + 3, x + w, y + 17, id});
		return x + w + 2;
	}

	private int trBtnRightToggle(GuiGraphics g, int rightX, int y, String label, int id, boolean on, int onColor, int mx, int my) {
		int w = this.font.width(label) + 10;
		int x = rightX - w;
		boolean hot = mx >= x && mx <= x + w && my >= y + 3 && my <= y + 17;
		g.fill(x, y + 3, x + w, y + 17, on ? onColor : hot ? colBtnHot() : colBtn());
		g.drawCenteredString(this.font, label, x + w / 2, y + 6, colText());
		transportHits.add(new int[]{x, y + 3, x + w, y + 17, id});
		return x - 3;
	}

	private void transportClick(int id) {
		OrdealAnimData d = data();
		if (d == null)
			return;
		switch (id) {
			case 20 -> d.speed = Math.max(0.25f, d.speed - 0.25f);
			case 21 -> d.speed = Math.min(3f, d.speed + 0.25f);
			case 30 -> OrdealAnimatorClient.time = 0;
			case 31 -> jumpKey(-1);
			case 32 -> stepFrame(-1);
			case 33 -> { playing = !playing; lastNanos = System.nanoTime(); }
			case 34 -> stepFrame(1);
			case 35 -> jumpKey(1);
			case 36 -> OrdealAnimatorClient.time = d.length();
			case 37 -> { playing = false; OrdealAnimatorClient.time = 0; }
			case 40 -> {
				OrdealAnimData ld = data();
				if (ld != null) {
					pushUndo();
					int next = (ld.endMode() + 1) % 3;
					ld.endMode(next);
					flash(switch (next) {
						case 1 -> "Loop - repeats until something stops it";
						case 2 -> "Hold Last - freezes on the final pose until stopped";
						default -> "Play Once - runs through, then hands back";
					});
				}
			}
			case 41 -> snap = !snap;
			case 42 -> autoKey = !autoKey;
		}
	}

	private void stepFrame(int dir) {
		float t = OrdealAnimatorClient.time + dir;
		OrdealAnimatorClient.time = Math.max(0, snap ? Math.round(t) : t);
		playing = false;
	}

	private void jumpKey(int dir) {
		OrdealAnimData d = data();
		if (d == null)
			return;
		float cur = OrdealAnimatorClient.time;
		Float best = null;
		for (List<Key> keys : d.bones.values())
			for (Key k : keys) {
				if (dir < 0 && k.t < cur - 0.01f && (best == null || k.t > best))
					best = k.t;
				if (dir > 0 && k.t > cur + 0.01f && (best == null || k.t < best))
					best = k.t;
			}
		if (best != null) {
			OrdealAnimatorClient.time = best;
			playing = false;
		}
	}

	// ------------------------------------------------------------------
	// Dope sheet
	// ------------------------------------------------------------------

	private float timeToX(float t) {
		return dsLabelW + (t - scrollTicks) * pxPerTick;
	}

	private float xToTime(double x) {
		return (float) ((x - dsLabelW) / pxPerTick + scrollTicks);
	}

	private void renderDopeSheet(GuiGraphics g, int mx, int my) {
		OrdealAnimData d = data();
		int bottom = this.height;
		g.fill(0, dsTop, this.width, bottom, colPanel());
		g.fill(0, dsTop, this.width, dsTop + 1, colBorder());

		// ruler
		int rulerY = dsTop + 2;
		g.fill(dsLabelW, rulerY, this.width, rulerY + rulerH, darkTheme ? 0xFF15151A : 0xFF232B3B);
		int fps = d == null ? 20 : (int) d.fps;
		int startT = Math.max(0, (int) Math.floor(scrollTicks));
		int endT = (int) Math.ceil(xToTime(this.width));
		for (int t = startT; t <= endT; t++) {
			float x = timeToX(t);
			if (x < dsLabelW)
				continue;
			boolean second = t % fps == 0;
			boolean five = t % 5 == 0;
			if (second) {
				g.fill((int) x, rulerY, (int) x + 1, rulerY + rulerH, colDim());
				g.drawString(this.font, (t / fps) + ":00", (int) x + 2, rulerY + 2, colDim());
			} else if (five && pxPerTick >= 4) {
				g.fill((int) x, rulerY + 5, (int) x + 1, rulerY + rulerH, 0xFF55555F);
				if (pxPerTick >= 8)
					g.drawString(this.font, (t / fps) + ":" + String.format("%02d", t % fps), (int) x + 2, rulerY + 2, 0xFF6A6A76);
			} else if (pxPerTick >= 6) {
				g.fill((int) x, rulerY + 9, (int) x + 1, rulerY + rulerH, 0xFF3A3A44);
			}
		}

		// tracks
		for (int i = 0; i < OrdealAnimatorClient.bones().length; i++) {
			String bone = OrdealAnimatorClient.bones()[i];
			int ty = dsTop + 2 + rulerH + i * trackH;
			boolean selRow = bone.equals(OrdealAnimatorClient.selBone);
			g.fill(0, ty, this.width, ty + trackH, (i % 2 == 0) ? (darkTheme ? 0xFF17171C : 0xFF20283A)
					: (darkTheme ? 0xFF131318 : 0xFF1C2434));
			if (selRow)
				g.fill(0, ty, this.width, ty + trackH, 0x2033AAFF);
			g.fill(0, ty, dsLabelW, ty + trackH, colPanelHead());
			g.fill(3, ty + 4, 8, ty + 9, TRACK_COLOR[i]);
			g.drawString(this.font, OrdealAnimatorClient.boneLabels()[i], 11, ty + 3, selRow ? 0xFFFFFFFF : colDim());

			if (d == null)
				continue;
			List<Key> keys = d.bones.getOrDefault(bone, List.of());
			g.enableScissor(dsLabelW, ty, this.width, ty + trackH);
			// gap labels
			for (int k = 0; k + 1 < keys.size(); k++) {
				float x0 = timeToX(keys.get(k).t), x1 = timeToX(keys.get(k + 1).t);
				if (x1 - x0 >= 26 && pxPerTick >= 4) {
					int gap = Math.round(keys.get(k + 1).t - keys.get(k).t);
					String lbl = gap + "f";
					g.drawString(this.font, lbl, (int) ((x0 + x1) / 2 - this.font.width(lbl) / 2f), ty + 3, 0xFF60606C);
				}
				g.fill((int) x0 + 3, ty + trackH / 2, (int) x1 - 2, ty + trackH / 2 + 1, 0x50FFFFFF & TRACK_COLOR[i] | 0x50000000);
			}
			for (Key k : keys) {
				float x = timeToX(k.t);
				boolean isSel = isSelected(bone, k);
				drawDiamond(g, (int) x, ty + trackH / 2, isSel ? 0xFFFFFFFF : TRACK_COLOR[i], isSel);
			}
			g.disableScissor();
		}

		// playhead
		float px = timeToX(OrdealAnimatorClient.time);
		if (px >= dsLabelW) {
			g.fill((int) px, rulerY, (int) px + 1, bottom, COL_PLAYHEAD);
			g.fill((int) px - 3, rulerY, (int) px + 4, rulerY + 4, COL_PLAYHEAD);
		}
	}

	private void drawDiamond(GuiGraphics g, int cx, int cy, int color, boolean big) {
		int r = big ? 4 : 3;
		for (int i = -r; i <= r; i++) {
			int w = r - Math.abs(i);
			g.fill(cx - w, cy + i, cx + w + 1, cy + i + 1, color);
		}
	}

	private boolean isSelected(String bone, Key k) {
		for (Sel s : selection)
			if (s.key == k && s.bone.equals(bone))
				return true;
		return false;
	}

	private String trackAt(double my) {
		int base = dsTop + 2 + rulerH;
		int i = (int) ((my - base) / trackH);
		if (my < base || i < 0 || i >= OrdealAnimatorClient.bones().length)
			return null;
		return OrdealAnimatorClient.bones()[i];
	}

	private Sel keyAt(double mx, double my) {
		OrdealAnimData d = data();
		if (d == null)
			return null;
		String bone = trackAt(my);
		if (bone == null)
			return null;
		List<Key> keys = d.bones.getOrDefault(bone, List.of());
		Sel best = null;
		double bestDist = 6;
		for (Key k : keys) {
			double dist = Math.abs(timeToX(k.t) - mx);
			if (dist < bestDist) {
				bestDist = dist;
				best = new Sel(bone, k);
			}
		}
		return best;
	}

	// ------------------------------------------------------------------
	// Windows: BONE + EASING
	// ------------------------------------------------------------------

	private void renderWin(GuiGraphics g, Win win, int mx, int my) {
		if (win.hidden)
			return;
		g.fill(win.x, win.y, win.x + win.w, win.y + win.drawH(), colPanel());
		g.fill(win.x, win.y, win.x + win.w, win.y + 12, colPanelHead());
		// border
		g.fill(win.x, win.y, win.x + win.w, win.y + 1, colBorder());
		g.fill(win.x, win.y + win.drawH() - 1, win.x + win.w, win.y + win.drawH(), colBorder());
		g.fill(win.x, win.y, win.x + 1, win.y + win.drawH(), colBorder());
		g.fill(win.x + win.w - 1, win.y, win.x + win.w, win.y + win.drawH(), colBorder());

		String t = win.title + (win == boneWin ? " · " + label(OrdealAnimatorClient.selBone) : "");
		g.drawString(this.font, t, win.x + 4, win.y + 2, 0xFFB9C6FF);
		g.drawString(this.font, win.minimized ? "□" : "—", win.x + win.w - 20, win.y + 2, colDim());
		g.drawString(this.font, "✕", win.x + win.w - 10, win.y + 2, colDim());

		if (win.minimized)
			return;
		if (win == boneWin)
			renderBonePanel(g, mx, my);
		else
			renderEasingPanel(g, mx, my);
	}

	private String label(String bone) {
		String[] bs = OrdealAnimatorClient.bones();
		String[] ls = OrdealAnimatorClient.boneLabels();
		for (int i = 0; i < bs.length && i < ls.length; i++)
			if (bs[i].equals(bone))
				return ls[i];
		return bone;
	}

	private static final String[] SLIDER_LABEL = {"Pitch", "Yaw", "Roll", "X", "Y", "Z"};

	private void renderBonePanel(GuiGraphics g, int mx, int my) {
		int x = boneWin.x + 4, y = boneWin.y + 15;
		// bone tabs, 3 per row
		for (int i = 0; i < OrdealAnimatorClient.bones().length; i++) {
			int bx = x + (i % 3) * 48;
			int by = y + (i / 3) * 15;
			boolean on = OrdealAnimatorClient.bones()[i].equals(OrdealAnimatorClient.selBone);
			boolean hot = mx >= bx && mx <= bx + 45 && my >= by && my <= by + 13;
			g.fill(bx, by, bx + 45, by + 13, on ? colBtnOn() : hot ? colBtnHot() : colBtn());
			g.drawCenteredString(this.font, OrdealAnimatorClient.boneLabels()[i], bx + 22, by + 3, colText());
		}
		y += 3 * 15 + 4;

		Pose p = currentPose();
		float[] vals = {p.rx, p.ry, p.rz, p.x, p.y, p.z};
		for (int i = 0; i < 6; i++) {
			int ry = y + i * 15;
			g.drawString(this.font, SLIDER_LABEL[i], x, ry + 3, colDim());
			int bx = x + 32, bw = boneWin.w - 40;
			g.fill(bx, ry + 1, bx + bw, ry + 12, darkTheme ? 0xFF15151A : 0xFF1C2434);
			// first-person offsets are blocks and live within about a metre;
			// the body's are model pixels and want a much wider throw
			float range = i < 3 ? 180f : (OrdealAnimatorClient.firstPerson ? 1.5f : 16f);
			float frac = Math.max(0f, Math.min(1f, (vals[i] + range) / (2 * range)));
			int hx = bx + (int) (frac * (bw - 3));
			g.fill(hx, ry + 1, hx + 3, ry + 12, 0xFF7FA8E8);
			String v = String.format(i < 3 ? "%.1f" : "%.2f", vals[i]);
			g.drawString(this.font, v, bx + bw - this.font.width(v) - 3, ry + 3, colText());
		}
		y += 6 * 15 + 3;

		boolean h1 = mx >= x && mx <= x + 68 && my >= y && my <= y + 14;
		boolean h2 = mx >= x + 72 && mx <= x + 140 && my >= y && my <= y + 14;
		g.fill(x, y, x + 68, y + 14, h1 ? colBtnHot() : colBtn());
		g.drawCenteredString(this.font, "Key Bone", x + 34, y + 3, colText());
		g.fill(x + 72, y, x + 140, y + 14, h2 ? colBtnHot() : colBtn());
		g.drawCenteredString(this.font, "Key All", x + 106, y + 3, colText());
	}

	/** Current pose of the selected bone (live drag > sampled > zeros). */
	private Pose currentPose() {
		Pose p = OrdealAnimatorClient.poseFor(OrdealAnimatorClient.selBone);
		return p != null ? p : new Pose();
	}

	private static final Ease[] EASE_ORDER = {
			Ease.LINEAR, Ease.EASE_IN, Ease.EASE_OUT, Ease.EASE_IN_OUT, Ease.SMOOTH, Ease.BACK, Ease.HOLD
	};
	private static final String[] EASE_LABEL = {
			"Linear", "Ease In", "Ease Out", "Ease In-Out", "Smooth", "Back", "Hold"
	};

	private void renderEasingPanel(GuiGraphics g, int mx, int my) {
		int x = easeWin.x + 4, y = easeWin.y + 15;
		Ease active = selection.isEmpty() ? null : selection.get(0).key.ease;
		for (int i = 0; i < EASE_ORDER.length; i++) {
			int bx = x + (i % 2) * 80;
			int by = y + (i / 2) * 15;
			boolean on = EASE_ORDER[i] == active;
			boolean hot = mx >= bx && mx <= bx + 77 && my >= by && my <= by + 13;
			g.fill(bx, by, bx + 77, by + 13, on ? colBtnOn() : hot ? colBtnHot() : colBtn());
			g.drawCenteredString(this.font, EASE_LABEL[i], bx + 38, by + 3, colText());
		}
		y += 4 * 15 + 4;

		// curve graph
		int gw = easeWin.w - 8, gh = 90;
		g.fill(x, y, x + gw, y + gh, darkTheme ? 0xFF0C0C10 : 0xFF161E2C);
		g.fill(x, y + gh - 1, x + gw, y + gh, 0xFF33333C);
		g.fill(x, y, x + 1, y + gh, 0xFF33333C);
		Ease curve = active != null ? active : Ease.LINEAR;
		int prevX = x + 1, prevY = y + gh - 2;
		for (int i = 1; i <= gw - 2; i++) {
			float fx = i / (float) (gw - 2);
			float fy = curve == Ease.HOLD ? (fx >= 1f ? 1f : 0f) : curve.apply(fx);
			int pxx = x + 1 + i;
			int pyy = y + gh - 2 - (int) (fy * (gh - 24)) - 10;
			g.fill(Math.min(prevX, pxx), Math.min(prevY, pyy), Math.max(prevX, pxx) + 1, Math.max(prevY, pyy) + 1, 0xFF6FA8FF);
			prevX = pxx;
			prevY = pyy;
		}

		String info;
		if (selection.isEmpty())
			info = "no key selected";
		else {
			Sel s = selection.get(0);
			int fps = data() == null ? 20 : (int) data().fps;
			int tt = Math.round(s.key.t);
			info = label(s.bone) + " @ " + (tt / fps) + ":" + String.format("%02d", tt % fps) + " · " + s.key.ease.id();
		}
		g.drawString(this.font, info, x, y + gh + 4, colDim());
	}

	// ------------------------------------------------------------------
	// Modals
	// ------------------------------------------------------------------

	private void renderNameModal(GuiGraphics g, int mx, int my) {
		g.fill(0, 0, this.width, this.height, 0x90000000);
		int w = 180, h = 58;
		int x = this.width / 2 - w / 2, y = this.height / 2 - h / 2 - 10;
		g.fill(x, y, x + w, y + h, colPanel());
		g.fill(x, y, x + w, y + 12, colPanelHead());
		g.drawString(this.font, nameMode == 1 ? "Rename \"" + nameTarget + "\""
				: nameMode == 2 ? "Copy \"" + nameTarget + "\" as"
				: "New animation", x + 4, y + 2, 0xFFB9C6FF);
		nameBox.setX(x + 10);
		nameBox.setY(y + 20);
		nameBox.render(g, mx, my, 0);
		g.drawCenteredString(this.font, "Enter = create · Esc = cancel", x + w / 2, y + 42, colDim());
	}

	private static final int LOAD_ROWS = 12;

	/** {x, y, w, h, rows} — one source of truth for drawing and hit-testing. */
	private int[] loadGeom() {
		int rows = Math.min(LOAD_ROWS, Math.max(1, loadRows.size()));
		int w = 170;
		// two rows of buttons, then the hint line - which used to run off the
		// bottom edge and land on top of the bone panel underneath
		int h = 16 + rows * 13 + 4 + 16 + 16 + 24;
		return new int[]{10, TOOLBAR_H + 6, w, h, rows};
	}

	/**
	 * Rebuild the display rows from loadList, third person first.
	 *
	 * A first-person clip and the third-person clip of the same move are two
	 * different things that often share a name shape, so they get their own
	 * headings instead of one flat alphabetical list.
	 */
	private void rebuildLoadRows() {
		loadRows = new ArrayList<>();
		List<String> tp = new ArrayList<>(), fp = new ArrayList<>();
		for (String nm : loadList)
			(OrdealAnimStore.firstPerson(nm) ? fp : tp).add(nm);
		if (!tp.isEmpty()) {
			loadRows.add(HDR + "THIRD PERSON");
			loadRows.addAll(tp);
		}
		if (!fp.isEmpty()) {
			loadRows.add(HDR + "FIRST PERSON");
			loadRows.addAll(fp);
		}
		loadScroll = Math.max(0, Math.min(loadScroll, Math.max(0, loadRows.size() - LOAD_ROWS)));
	}

	private static boolean isHeader(String row) {
		return row.startsWith(HDR);
	}

	private void openLoad() {
		OrdealAnimData d = data();
		backupJson = d == null ? null : d.toJson();
		backupName = OrdealAnimatorClient.clipName;
		backupTime = OrdealAnimatorClient.time;
		previewName = null;
		deleteArm = false;
		loadList = OrdealAnimStore.list();
		rebuildLoadRows();
		loadScroll = 0;
		modal = 2;
	}

	/** Highlighted clip goes live on the dummy and loops until you pick. */
	private void preview(String name) {
		OrdealAnimData d = OrdealAnimStore.load(name);
		if (d == null) { flash("Could not read " + name); return; }
		previewName = name;
		deleteArm = false;
		OrdealAnimatorClient.data = d;
		OrdealAnimatorClient.clipName = name;
		OrdealAnimatorClient.time = 0;
		OrdealAnimatorClient.livePose.clear();
		selection.clear();
		playing = true;
		lastNanos = System.nanoTime();
	}

	/** Put the clip that was open before the browser back. */
	private void restorePreview() {
		if (backupJson != null) {
			OrdealAnimatorClient.data = OrdealAnimData.fromJson(backupJson);
			OrdealAnimatorClient.clipName = backupName;
			OrdealAnimatorClient.time = backupTime;
			OrdealAnimatorClient.livePose.clear();
		}
		previewName = null;
		playing = false;
	}

	private void closeLoad(boolean keep) {
		if (!keep) restorePreview();
		else {
			previewName = null;
			playing = false;
			undoStack.clear();
			redoStack.clear();
		}
		selection.clear();
		backupJson = null;
		deleteArm = false;
		modal = 0;
	}

	private void deletePreviewed() {
		if (previewName == null) return;
		String name = previewName;
		boolean gone = OrdealAnimStore.delete(name);
		restorePreview();
		loadList = OrdealAnimStore.list();
		rebuildLoadRows();
		deleteArm = false;
		flash(!gone ? "Could not delete " + name
				: loadList.contains(name) ? "Deleted your copy - a built-in " + name + " is still shipped"
				: "Deleted " + name);
	}

	private void renderLoadModal(GuiGraphics g, int mx, int my) {
		int[] geo = loadGeom();
		int x = geo[0], y = geo[1], w = geo[2], h = geo[3], rows = geo[4];
		// the browser is a modal: everything behind it goes dark and its own
		// panel is fully opaque, so the editor's labels stop reading through it
		g.fill(0, 0, this.width, this.height, 0x99000000);
		g.fill(x, y, x + w, y + h, opaque(colPanel()));
		g.fill(x, y, x + w, y + 12, opaque(colPanelHead()));
		g.fill(x, y, x + w, y + 1, colBorder());
		g.fill(x, y + h - 1, x + w, y + h, colBorder());
		g.fill(x, y, x + 1, y + h, colBorder());
		g.fill(x + w - 1, y, x + w, y + h, colBorder());
		g.drawString(this.font, "ANIMATIONS (" + loadList.size() + ")", x + 4, y + 2, 0xFFB9C6FF);

		if (loadList.isEmpty())
			g.drawCenteredString(this.font, "nothing saved yet", x + w / 2, y + 22, colDim());
		for (int i = 0; i < rows; i++) {
			int idx = loadScroll + i;
			if (idx >= loadRows.size()) break;
			String name = loadRows.get(idx);
			int ry = y + 16 + i * 13;
			if (isHeader(name)) {
				g.drawString(this.font, name.substring(1), x + 6, ry + 3, 0xFF7ED8F5);
				g.fill(x + 6, ry + 12, x + w - 6, ry + 13, 0x407ED8F5);
				continue;
			}
			boolean sel = name.equals(previewName);
			boolean hot = mx >= x + 4 && mx <= x + w - 4 && my >= ry && my <= ry + 12;
			if (sel) g.fill(x + 4, ry, x + w - 4, ry + 12, colBtnOn());
			else if (hot) g.fill(x + 4, ry, x + w - 4, ry + 12, colBtnHot());
			if (name.equals(backupName)) g.fill(x + 5, ry + 5, x + 8, ry + 8, 0xFF7ED8F5);
			// green tick = saved into the mod's assets, so it ships with a build
			if (OrdealAnimStore.inMod(name)) g.fill(x + w - 10, ry + 5, x + w - 7, ry + 8, COL_AUTOKEY);
			g.drawString(this.font, name, x + 11, ry + 2, sel ? 0xFFFFFFFF : colText());
		}

		int by = y + 16 + rows * 13 + 4;
		boolean armed = previewName != null;
		loadBtn(g, x + 4, by, 44, "Load", armed, mx, my, colBtnOn());
		loadBtn(g, x + 50, by, 52, "Rename", armed, mx, my, colBtn());
		loadBtn(g, x + 104, by, 44, "Copy", armed, mx, my, colBtn());
		loadBtn(g, x + 4, by + 16, 60, deleteArm ? "Sure?" : "Delete", armed, mx, my, 0xFFB03030);
		loadBtn(g, x + 66, by + 16, 50, "Close", true, mx, my, colBtn());
		g.drawString(this.font, armed ? "previewing - Load keeps it" : "click a name to preview",
				x + 5, by + 35, colDim());
	}

	// ------------------------------------------------------------------
	// Import picker
	// ------------------------------------------------------------------

	private int[] importGeom() {
		int count = importPick == null ? 0 : importPick.names.size();
		int rows = Math.min(IMPORT_ROWS, Math.max(1, count));
		int w = 220;
		int h = 16 + rows * 13 + 6 + 16 + 24;
		return new int[]{(this.width - w) / 2, Math.max(TOOLBAR_H + 8, (this.height - h) / 2), w, h, rows};
	}

	/**
	 * One row per animation in the file, each with a tick box.
	 *
	 * Blockbench files routinely hold a dozen animations for one rig, and the
	 * importer used to take every last one and dump them into the clip list
	 * under whatever names the file used. Now nothing lands until it is ticked.
	 */
	private void renderImportModal(GuiGraphics g, int mx, int my) {
		if (importPick == null) { modal = 0; return; }
		int[] geo = importGeom();
		int x = geo[0], y = geo[1], w = geo[2], h = geo[3], rows = geo[4];

		g.fill(0, 0, this.width, this.height, 0x99000000);
		g.fill(x, y, x + w, y + h, opaque(colPanel()));
		g.fill(x, y, x + w, y + 12, opaque(colPanelHead()));
		g.fill(x, y, x + w, y + 1, colBorder());
		g.fill(x, y + h - 1, x + w, y + h, colBorder());
		g.fill(x, y, x + 1, y + h, colBorder());
		g.fill(x + w - 1, y, x + w, y + h, colBorder());
		g.drawString(this.font, "IMPORT - " + importPick.names.size() + " animation(s)",
				x + 4, y + 2, 0xFFB9C6FF);

		for (int i = 0; i < rows; i++) {
			int idx = importScroll + i;
			if (idx >= importPick.names.size()) break;
			int ry = y + 16 + i * 13;
			boolean hot = mx >= x + 4 && mx <= x + w - 4 && my >= ry && my <= ry + 12;
			if (hot) g.fill(x + 4, ry, x + w - 4, ry + 12, colBtnHot());
			// tick box
			g.fill(x + 6, ry + 2, x + 15, ry + 11, 0xFF16161C);
			if (importSel[idx]) g.fill(x + 8, ry + 4, x + 13, ry + 9, COL_AUTOKEY);
			String nm = importPick.names.get(idx);
			boolean clash = OrdealAnimStore.exists(nm);
			g.drawString(this.font, nm, x + 20, ry + 2, importSel[idx] ? 0xFFFFFFFF : colText());
			// a name already in the list would be quietly replaced - say so
			if (clash)
				g.drawString(this.font, "overwrites",
						x + w - this.font.width("overwrites") - 6, ry + 2, 0xFFE0A050);
		}

		int by = y + 16 + rows * 13 + 6;
		int picked = 0;
		for (boolean b : importSel) if (b) picked++;
		loadBtn(g, x + 4, by, 56, "All", true, mx, my, colBtn());
		loadBtn(g, x + 62, by, 56, "None", true, mx, my, colBtn());
		loadBtn(g, x + 120, by, 44, "Cancel", true, mx, my, colBtn());
		loadBtn(g, x + w - 62, by, 58, "Import " + picked, picked > 0, mx, my, colBtnOn());
		g.drawString(this.font, "click a row to tick it", x + 5, by + 18, colDim());
	}

	private boolean importModalClick(double mx, double my) {
		if (importPick == null) { modal = 0; return true; }
		int[] geo = importGeom();
		int x = geo[0], y = geo[1], w = geo[2], h = geo[3], rows = geo[4];

		for (int i = 0; i < rows; i++) {
			int idx = importScroll + i;
			if (idx >= importPick.names.size()) break;
			int ry = y + 16 + i * 13;
			if (mx >= x + 4 && mx <= x + w - 4 && my >= ry && my <= ry + 12) {
				importSel[idx] = !importSel[idx];
				return true;
			}
		}

		int by = y + 16 + rows * 13 + 6;
		if (my >= by && my <= by + 14) {
			if (mx >= x + 4 && mx <= x + 60) {
				java.util.Arrays.fill(importSel, true);
				return true;
			}
			if (mx >= x + 62 && mx <= x + 118) {
				java.util.Arrays.fill(importSel, false);
				return true;
			}
			if (mx >= x + 120 && mx <= x + 164) {
				modal = 0;
				importPick = null;
				return true;
			}
			if (mx >= x + w - 62 && mx <= x + w - 4) {
				runImport();
				return true;
			}
		}
		if (!(mx >= x && mx <= x + w && my >= y && my <= y + h)) {
			modal = 0;
			importPick = null;
		}
		return true;
	}

	private void runImport() {
		if (importPick == null) { modal = 0; return; }
		java.util.List<String> want = new ArrayList<>();
		for (int i = 0; i < importSel.length; i++)
			if (importSel[i]) want.add(importPick.names.get(i));
		if (want.isEmpty()) { flash("Nothing ticked"); return; }
		String msg = OrdealBlockbench.importChosen(importPick, want);
		modal = 0;
		importPick = null;
		flash(msg);
	}

	/** Opens the name box for a rename or a copy, prefilled and selected. */
	private void askName(int mode, String target, String suggested) {
		nameMode = mode;
		nameTarget = target;
		nameBox.setValue(suggested);
		nameBox.setFocused(true);
		modal = 1;
	}

	/** Re-reads the clip list after a rename, copy or delete, keeping a preview. */
	private void refreshLoadList(String select) {
		loadList = OrdealAnimStore.list();
		rebuildLoadRows();
		previewName = loadList.contains(select) ? select : null;
		deleteArm = false;
	}

	private void loadBtn(GuiGraphics g, int x, int y, int w, String label, boolean on, int mx, int my, int col) {
		boolean hot = on && mx >= x && mx <= x + w && my >= y && my <= y + 14;
		g.fill(x, y, x + w, y + 14, !on ? 0x40202028 : hot ? col : colBtn());
		g.drawCenteredString(this.font, label, x + w / 2, y + 3, on ? colText() : colDim());
	}

	/** Returns true when the click was inside the browser. */
	private boolean loadModalClick(double mx, double my) {
		int[] geo = loadGeom();
		int x = geo[0], y = geo[1], w = geo[2], h = geo[3], rows = geo[4];
		for (int i = 0; i < rows; i++) {
			int idx = loadScroll + i;
			if (idx >= loadRows.size()) break;
			int ry = y + 16 + i * 13;
			if (mx >= x + 4 && mx <= x + w - 4 && my >= ry && my <= ry + 12) {
				String name = loadRows.get(idx);
				if (isHeader(name)) return true;
				if (name.equals(previewName)) closeLoad(true); // click it again = load
				else preview(name);
				return true;
			}
		}
		int by = y + 16 + rows * 13 + 4;
		if (my >= by && my <= by + 14) {
			if (previewName != null && mx >= x + 4 && mx <= x + 48) {
				String name = previewName;
				closeLoad(true);
				flash("Loaded " + name);
				return true;
			}
			if (previewName != null && mx >= x + 50 && mx <= x + 102) {
				askName(1, previewName, previewName);
				return true;
			}
			if (previewName != null && mx >= x + 104 && mx <= x + 148) {
				askName(2, previewName, OrdealAnimStore.freeName(previewName));
				return true;
			}
		}
		if (my >= by + 16 && my <= by + 30) {
			if (previewName != null && mx >= x + 4 && mx <= x + 64) {
				if (deleteArm) deletePreviewed();
				else { deleteArm = true; flash("Click again to delete " + previewName); }
				return true;
			}
			if (mx >= x + 66 && mx <= x + 116) { closeLoad(false); return true; }
		}
		if (!(mx >= x && mx <= x + w && my >= y && my <= y + h))
			closeLoad(false);
		return true;
	}

	// ==================================================================
	// INPUT
	// ==================================================================

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (modal == 1) {
			nameBox.mouseClicked(mx, my, button);
			return true;
		}
		if (modal == 2)
			return loadModalClick(mx, my);
		if (modal == 3)
			return importModalClick(mx, my);

		if (ctxOpen) {
			int idx = ctxHit(mx, my);
			ctxOpen = false;
			if (idx >= 0) ctxAction(idx);
			return true;
		}
		if (button == 1) {
			Sel hit = keyAt(mx, my);
			if (hit != null) {
				if (!isSelected(hit.bone, hit.key)) {
					selection.clear();
					selection.add(hit);
				}
				ctxOpen = true;
				ctxXp = (int) mx;
				ctxYp = (int) my;
				ctxTime = xToTime(mx);
				return true;
			}
		}

		lastMx = mx;
		lastMy = my;

		// windows first (topmost interaction layer)
		for (Win win : new Win[]{easeWin, boneWin}) {
			if (win.hidden)
				continue;
			if (win.overTitle(mx, my)) {
				if (mx >= win.x + win.w - 12) {
					win.hidden = true;
					return true;
				}
				if (mx >= win.x + win.w - 22) {
					win.minimized = !win.minimized;
					return true;
				}
				dragMode = DRAG_WIN;
				dragWin = win;
				dragOffX = (int) (mx - win.x);
				dragOffY = (int) (my - win.y);
				return true;
			}
			if (win.over(mx, my)) {
				if (win == boneWin && !win.minimized && bonePanelClick(mx, my))
					return true;
				if (win == easeWin && !win.minimized && easingPanelClick(mx, my))
					return true;
				return true; // clicks inside a window never fall through
			}
		}

		// toolbar
		for (int[] hitZone : toolbarHits)
			if (mx >= hitZone[0] && mx <= hitZone[2] && my >= hitZone[1] && my <= hitZone[3]) {
				toolbarClick(hitZone[4]);
				return true;
			}
		// transport
		for (int[] hitZone : transportHits)
			if (mx >= hitZone[0] && mx <= hitZone[2] && my >= hitZone[1] && my <= hitZone[3]) {
				transportClick(hitZone[4]);
				return true;
			}

		// dope sheet
		if (my >= dsTop) {
			int rulerY = dsTop + 2;
			if (my <= rulerY + rulerH && mx >= dsLabelW) {
				dragMode = DRAG_PLAYHEAD;
				setPlayhead(xToTime(mx));
				return true;
			}
			String rowBone = trackAt(my);
			if (rowBone != null && mx < dsLabelW) {
				OrdealAnimatorClient.selBone = rowBone;
				return true;
			}
			Sel hit = keyAt(mx, my);
			if (hit != null) {
				if (!isSelected(hit.bone, hit.key)) {
					if (!hasShiftDown())
						selection.clear();
					selection.add(hit);
				}
				OrdealAnimatorClient.selBone = hit.bone;
				OrdealAnimatorClient.time = hit.key.t;
				dragMode = DRAG_KEYS;
				keysDragAccum = 0f;
				return true;
			}
			if (rowBone != null) {
				long now = System.currentTimeMillis();
				boolean dbl = now - lastClickMs < 280 && Math.abs(mx - lastClickX) < 5 && Math.abs(my - lastClickY) < 5;
				lastClickMs = now;
				lastClickX = mx;
				lastClickY = my;
				if (dbl && mx >= dsLabelW && data() != null) {
					// double-click empty track spot = add a key with the current sampled pose
					float t = xToTime(mx);
					if (snap)
						t = Math.round(t);
					Key k = keyFromPose(rowBone, Math.max(0, t));
					data().putKey(rowBone, k);
					selection.clear();
					selection.add(new Sel(rowBone, k));
					return true;
				}
				dragMode = DRAG_BOX;
				boxX0 = boxX1 = mx;
				boxY0 = boxY1 = my;
				return true;
			}
			return true;
		}

		// right-drag = free look, middle-drag = pan
		if (button != 0) {
			dragMode = button == 1 ? DRAG_LOOK : DRAG_PAN;
			return true;
		}

		// gizmo handles
		int axis = gizmoAxisAt(mx, my);
		if (axis >= 0) {
			gizmoAxis = axis;
			if (OrdealAnimatorClient.rotateMode) {
				dragMode = DRAG_GIZMO;
				float[] pv = OrdealAnimatorClient.pivotScreen;
				if (pv != null)
					gizmoLastAngle = Math.atan2(my - pv[1], mx - pv[0]);
			} else {
				dragMode = DRAG_MOVEAXIS;
			}
			beginLiveEdit();
			return true;
		}

		// bone pick on the model
		String picked = nearestBone(mx, my, 26);
		if (picked != null) {
			OrdealAnimatorClient.selBone = picked;
			return true;
		}

		// empty space: orbit the camera around the dummy
		dragMode = DRAG_ORBIT;
		return true;
	}

	private boolean bonePanelClick(double mx, double my) {
		int x = boneWin.x + 4, y = boneWin.y + 15;
		for (int i = 0; i < OrdealAnimatorClient.bones().length; i++) {
			int bx = x + (i % 3) * 48;
			int by = y + (i / 3) * 15;
			if (mx >= bx && mx <= bx + 45 && my >= by && my <= by + 13) {
				OrdealAnimatorClient.selBone = OrdealAnimatorClient.bones()[i];
				return true;
			}
		}
		y += 3 * 15 + 4;
		for (int i = 0; i < 6; i++) {
			int ry = y + i * 15;
			int bx = x + 32, bw = boneWin.w - 40;
			if (mx >= bx && mx <= bx + bw && my >= ry && my <= ry + 13) {
				long now = net.minecraft.Util.getMillis();
				boolean twice = i == lastSlider && now - lastSliderClick < 350;
				lastSlider = i;
				lastSliderClick = now;

				beginLiveEdit();
				if (twice) {
					// double-click a slider to put it back to zero. Faster than
					// dragging back to centre and it lands exactly on 0.
					setValue(liveEditPose(), i, 0f);
					commitLiveEdit();
					dragMode = DRAG_NONE;
					dragSlider = -1;
					lastSlider = -1;
					flash(SLIDER_LABEL[i] + " reset");
					return true;
				}
				dragMode = DRAG_SLIDER;
				dragSlider = i;
				return true;
			}
		}
		y += 6 * 15 + 3;
		if (my >= y && my <= y + 14) {
			if (mx >= x && mx <= x + 68) {
				keyBone(OrdealAnimatorClient.selBone);
				return true;
			}
			if (mx >= x + 72 && mx <= x + 140) {
				for (String b : OrdealAnimatorClient.bones())
					keyBone(b);
				flash("Keyed all bones");
				return true;
			}
		}
		return false;
	}

	private boolean easingPanelClick(double mx, double my) {
		int x = easeWin.x + 4, y = easeWin.y + 15;
		for (int i = 0; i < EASE_ORDER.length; i++) {
			int bx = x + (i % 2) * 80;
			int by = y + (i / 2) * 15;
			if (mx >= bx && mx <= bx + 77 && my >= by && my <= by + 13) {
				for (Sel s : selection)
					s.key.ease = EASE_ORDER[i];
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		switch (dragMode) {
			case DRAG_WIN -> {
				dragWin.x = (int) (mx - dragOffX);
				dragWin.y = (int) Math.max(TOOLBAR_H, my - dragOffY);
			}
			case DRAG_ORBIT -> OrdealOrbitCam.drag(dx, dy);
			case DRAG_PAN -> OrdealOrbitCam.pan(dx, dy);
			case DRAG_LOOK -> OrdealOrbitCam.look(dx, dy);
			case DRAG_PLAYHEAD -> setPlayhead(xToTime(mx));
			case DRAG_SLIDER -> {
				Pose p = liveEditPose();
				float sens = dragSlider < 3 ? 0.5f : 0.08f;
				float v = (float) (value(p, dragSlider) + dx * sens);
				setValue(p, dragSlider, v);
			}
			case DRAG_GIZMO -> {
				float[] pv = OrdealAnimatorClient.pivotScreen;
				if (pv != null) {
					double a = Math.atan2(my - pv[1], mx - pv[0]);
					double delta = Math.toDegrees(wrapAngle(a - gizmoLastAngle));
					gizmoLastAngle = a;
					Pose p = liveEditPose();
					int sign = gizmoDragSign(gizmoAxis);
					if (gizmoAxis == OrdealAnimatorClient.AXIS_X)
						p.rx += (float) delta * sign;
					else if (gizmoAxis == OrdealAnimatorClient.AXIS_Y)
						p.ry += (float) delta * sign;
					else
						p.rz += (float) delta * sign;
				}
			}
			case DRAG_MOVEAXIS -> {
				float[] pts = OrdealAnimatorClient.gizmoScreen.get(gizmoAxis);
				if (pts != null && pts.length >= 4 && !Float.isNaN(pts[0]) && !Float.isNaN(pts[2])) {
					double ax = pts[2] - pts[0], ay = pts[3] - pts[1];
					double len = Math.sqrt(ax * ax + ay * ay);
					if (len > 1) {
						double dot = (dx * ax + dy * ay) / len;
						// world length of the arrow ≈ radius+0.15
						double worldLen = ("root".equals(OrdealAnimatorClient.selBone) ? 0.7 : 0.5);
						double worldPerPx = worldLen / len;
						float deltaPx = (float) (dot * worldPerPx * 16.0);
						// flip a sign here if an axis ever drags inverted
						float[] SIGN = {-1f, 1f, 1f};
						Pose p = liveEditPose();
						if (gizmoAxis == OrdealAnimatorClient.AXIS_X)
							p.x += deltaPx * SIGN[0];
						else if (gizmoAxis == OrdealAnimatorClient.AXIS_Y)
							p.y += deltaPx * SIGN[1];
						else
							p.z += deltaPx * SIGN[2];
					}
				}
			}
			case DRAG_KEYS -> {
				keysDragAccum += (float) (dx / pxPerTick);
				float apply = snap ? (float) Math.floor(Math.abs(keysDragAccum)) * Math.signum(keysDragAccum) : keysDragAccum;
				if (Math.abs(apply) >= (snap ? 1f : 0.01f)) {
					for (Sel s : selection)
						s.key.t = Math.max(0, s.key.t + apply);
					keysDragAccum -= apply;
					for (Sel s : selection)
						data().sortChannel(s.bone);
				}
			}
			case DRAG_BOX -> {
				boxX1 = mx;
				boxY1 = my;
			}
		}
		lastMx = mx;
		lastMy = my;
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (dragMode == DRAG_BOX) {
			double x0 = Math.min(boxX0, boxX1), x1 = Math.max(boxX0, boxX1);
			double y0 = Math.min(boxY0, boxY1), y1 = Math.max(boxY0, boxY1);
			if (!hasShiftDown())
				selection.clear();
			OrdealAnimData d = data();
			if (d != null)
				for (int i = 0; i < OrdealAnimatorClient.bones().length; i++) {
					int ty = dsTop + 2 + rulerH + i * trackH + trackH / 2;
					if (ty < y0 || ty > y1)
						continue;
					String bone = OrdealAnimatorClient.bones()[i];
					for (Key k : d.bones.getOrDefault(bone, List.of())) {
						float x = timeToX(k.t);
						if (x >= x0 && x <= x1 && !isSelected(bone, k))
							selection.add(new Sel(bone, k));
					}
				}
		}
		if ((dragMode == DRAG_SLIDER || dragMode == DRAG_GIZMO || dragMode == DRAG_MOVEAXIS))
			commitLiveEdit();
		dragMode = DRAG_NONE;
		dragWin = null;
		dragSlider = -1;
		gizmoAxis = -1;
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (modal == 2) {
			loadScroll = Math.max(0, Math.min(Math.max(0, loadRows.size() - LOAD_ROWS), loadScroll - (int) sy));
			return true;
		}
		if (modal == 3) {
			int max = importPick == null ? 0 : Math.max(0, importPick.names.size() - IMPORT_ROWS);
			importScroll = Math.max(0, Math.min(max, importScroll - (int) sy));
			return true;
		}
		if (my >= dsTop) {
			if (hasControlDown()) {
				pxPerTick = Math.max(2f, Math.min(24f, pxPerTick + (float) sy * 2f));
			} else {
				scrollTicks = Math.max(0, scrollTicks - (float) sy * 5f);
			}
			return true;
		}
		if (boneWin.over(mx, my) && !boneWin.minimized) {
			// scroll a slider row = fine adjust
			int x = boneWin.x + 4, y = boneWin.y + 15 + 3 * 15 + 4;
			for (int i = 0; i < 6; i++) {
				int ry = y + i * 15;
				if (my >= ry && my <= ry + 13 && mx >= x + 32) {
					Pose p = liveEditPose();
					setValue(p, i, value(p, i) + (float) sy * (i < 3 ? 1f : 0.25f));
					commitLiveEdit();
					return true;
				}
			}
			return true;
		}
		if (!easeWin.over(mx, my) && my < dsTop - 20) {
			OrdealOrbitCam.zoom(sy);
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (modal == 1) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				modal = 0;
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				String name = nameBox.getValue().trim();
				if (!name.isEmpty()) {
					switch (nameMode) {
						case 1 -> {
							// rename carries the clip over and takes the old one
							// away, and follows it if it is the one open
							boolean ok = OrdealAnimStore.rename(nameTarget, name);
							if (ok && nameTarget.equals(OrdealAnimatorClient.clipName))
								OrdealAnimatorClient.clipName = name;
							refreshLoadList(ok ? name : nameTarget);
							flash(ok ? "Renamed to " + name : "Could not rename " + nameTarget);
						}
						case 2 -> {
							boolean ok = OrdealAnimStore.duplicate(nameTarget, name);
							refreshLoadList(ok ? name : nameTarget);
							flash(ok ? "Copied to " + name : "Could not copy " + nameTarget);
						}
						default -> {
							// New keeps the work in front of you and gives it a
							// name of its own - it is a "save as", not a wipe.
							// Nothing you spent time on disappears behind a
							// button you might have hit by accident.
							OrdealAnimData cur = data();
							OrdealAnimData d;
							if (cur != null) {
								d = cur.copy();
							} else {
								d = new OrdealAnimData();
								OrdealAnimatorClient.seed(d);
							}
							OrdealAnimatorClient.data = d;
							OrdealAnimatorClient.clipName = name;
							selection.clear();
							undoStack.clear();
							redoStack.clear();
							boolean saved = OrdealAnimStore.save(name, d);
							flash(saved ? "New clip \"" + name + "\" - carried your work over"
									: "New: " + name + " (not saved yet)");
						}
					}
				}
				modal = 0;
				nameMode = 0;
				return true;
			}
			return nameBox.keyPressed(keyCode, scanCode, modifiers);
		}
		if (modal == 3) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) { modal = 0; importPick = null; return true; }
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				runImport();
				return true;
			}
			return true;
		}
		if (modal == 2) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeLoad(false); return true; }
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				if (previewName != null) { String n = previewName; closeLoad(true); flash("Loaded " + n); }
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
				int step = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1;
				int i = previewName == null ? -1 : loadRows.indexOf(previewName);
				// walk past the headings so arrowing never lands on one
				for (int guard = 0; guard < loadRows.size(); guard++) {
					i += step;
					if (i < 0 || i >= loadRows.size()) { i = -1; break; }
					if (!isHeader(loadRows.get(i))) break;
				}
				if (i >= 0) {
					preview(loadRows.get(i));
					if (i < loadScroll) loadScroll = i;
					if (i >= loadScroll + LOAD_ROWS) loadScroll = i - LOAD_ROWS + 1;
				}
				return true;
			}
			return true;
		}

		boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
			if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) redo(); else undo();
			return true;
		}
		if (ctrl && keyCode == GLFW.GLFW_KEY_Y) {
			redo();
			return true;
		}

		switch (keyCode) {
			case GLFW.GLFW_KEY_SPACE -> {
				playing = !playing;
				lastNanos = System.nanoTime();
				return true;
			}
			case GLFW.GLFW_KEY_R -> {
				OrdealAnimatorClient.rotateMode = true;
				return true;
			}
			case GLFW.GLFW_KEY_T -> {
				OrdealAnimatorClient.rotateMode = false;
				return true;
			}
			case GLFW.GLFW_KEY_LEFT -> {
				if (hasControlDown()) jumpKey(-1); else stepFrame(-1);
				return true;
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				if (hasControlDown()) jumpKey(1); else stepFrame(1);
				return true;
			}
			case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
				deleteSelected();
				return true;
			}
			case GLFW.GLFW_KEY_C -> {
				if (hasControlDown()) {
					copySelected();
				} else {
					OrdealOrbitCam.center();
					flash("Camera centered on the dummy");
				}
				return true;
			}
			case GLFW.GLFW_KEY_X -> {
				if (hasControlDown()) {
					copySelected();
					deleteSelected();
					flash("Cut");
					return true;
				}
			}
			case GLFW.GLFW_KEY_V -> {
				if (hasControlDown()) {
					pasteAtPlayhead();
					return true;
				}
			}
			case GLFW.GLFW_KEY_S -> {
				if (hasControlDown()) {
					toolbarClick(2);
					return true;
				}
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (modal == 1)
			return nameBox.charTyped(chr, modifiers);
		return super.charTyped(chr, modifiers);
	}

	// ------------------------------------------------------------------
	// Edit ops
	// ------------------------------------------------------------------

	private void setPlayhead(float t) {
		if (snap)
			t = Math.round(t);
		OrdealAnimatorClient.time = Math.max(0, t);
		playing = false;
		OrdealAnimatorClient.livePose.clear();
	}

	/** Begin a live edit on the selected bone: pin its current sampled pose as the working copy. */
	private void beginLiveEdit() {
		String bone = OrdealAnimatorClient.selBone;
		Pose base = OrdealAnimatorClient.poseFor(bone);
		Pose live = new Pose();
		if (base != null) {
			live.rx = base.rx; live.ry = base.ry; live.rz = base.rz;
			live.x = base.x; live.y = base.y; live.z = base.z;
		}
		OrdealAnimatorClient.livePose.put(bone, live);
	}

	private Pose liveEditPose() {
		Pose p = OrdealAnimatorClient.livePose.get(OrdealAnimatorClient.selBone);
		if (p == null) {
			beginLiveEdit();
			p = OrdealAnimatorClient.livePose.get(OrdealAnimatorClient.selBone);
		}
		return p;
	}

	/** On release: auto-key writes the live pose as a key at the playhead; otherwise it stays as a preview. */
	private void commitLiveEdit() {
		if (!autoKey)
			return;
		String bone = OrdealAnimatorClient.selBone;
		if (OrdealAnimatorClient.livePose.containsKey(bone))
			keyBone(bone);
	}

	private void keyBone(String bone) {
		OrdealAnimData d = data();
		if (d == null)
			return;
		pushUndo();
		float t = snap ? Math.round(OrdealAnimatorClient.time) : OrdealAnimatorClient.time;
		Key k = keyFromPose(bone, t);
		d.putKey(bone, k);
		OrdealAnimatorClient.livePose.remove(bone);
		selection.clear();
		selection.add(new Sel(bone, k));
	}

	private Key keyFromPose(String bone, float t) {
		Pose p = OrdealAnimatorClient.poseFor(bone);
		Key k = new Key(t);
		if (p != null) {
			k.rot[0] = p.rx; k.rot[1] = p.ry; k.rot[2] = p.rz;
			k.pos[0] = p.x; k.pos[1] = p.y; k.pos[2] = p.z;
		}
		// inherit easing from the key we're replacing/nearest before
		List<Key> keys = data().bones.getOrDefault(bone, List.of());
		for (Key existing : keys)
			if (existing.t <= t)
				k.ease = existing.ease;
		return k;
	}

	private void deleteSelected() {
		OrdealAnimData d = data();
		if (d == null || selection.isEmpty())
			return;
		pushUndo();
		for (Sel s : selection) {
			List<Key> keys = d.bones.get(s.bone);
			if (keys != null)
				keys.remove(s.key);
		}
		selection.clear();
	}

	private void copySelected() {
		if (selection.isEmpty())
			return;
		float minT = Float.MAX_VALUE;
		for (Sel s : selection)
			minT = Math.min(minT, s.key.t);
		Map<String, List<Key>> clip = new LinkedHashMap<>();
		for (Sel s : selection) {
			Key c = s.key.copy();
			c.t -= minT;
			clip.computeIfAbsent(s.bone, b -> new ArrayList<>()).add(c);
		}
		clipboard = clip;
		flash("Copied " + selection.size() + " key(s)");
	}

	private void pasteAtPlayhead() {
		OrdealAnimData d = data();
		if (d == null || clipboard == null)
			return;
		pushUndo();
		float base = snap ? Math.round(OrdealAnimatorClient.time) : OrdealAnimatorClient.time;
		selection.clear();
		for (Map.Entry<String, List<Key>> e : clipboard.entrySet())
			for (Key src : e.getValue()) {
				Key k = src.copy();
				k.t = base + src.t;
				d.putKey(e.getKey(), k);
				selection.add(new Sel(e.getKey(), k));
			}
		flash("Pasted");
	}

	/** Mirror the whole clip. toLeft=true copies right→left, else left→right; flips yaw/roll and X. */
	/**
	 * Swap the two sides outright, the way Invincible's Flip does.
	 *
	 * Unlike L to R, which copies one side onto the other, this exchanges them -
	 * a punch thrown with the right becomes the same punch with the left, and
	 * pressing it twice puts everything back.
	 */
	private void flipSides() {
		OrdealAnimData d = data();
		if (d == null) return;
		pushUndo();
		if (OrdealAnimatorClient.firstPerson) {
			swapPair(d, "fp_right", "fp_left");
		} else {
			swapPair(d, "right_arm", "left_arm");
			swapPair(d, "right_leg", "left_leg");
		}
		selection.clear();
		flash("Flipped sides");
	}

	private void swapPair(OrdealAnimData d, String a, String b) {
		List<Key> ka = new ArrayList<>(d.bones.getOrDefault(a, List.of()));
		List<Key> kb = new ArrayList<>(d.bones.getOrDefault(b, List.of()));
		put(d, a, kb);
		put(d, b, ka);
	}

	/** Mirrored across the body: yaw, roll and sideways offset all invert. */
	private void put(OrdealAnimData d, String bone, List<Key> src) {
		List<Key> dst = d.channel(bone);
		dst.clear();
		for (Key k : src) {
			Key m = k.copy();
			m.rot[1] = -m.rot[1];
			m.rot[2] = -m.rot[2];
			m.pos[0] = -m.pos[0];
			dst.add(m);
		}
	}

	private void mirrorClip(boolean toLeft) {
		OrdealAnimData d = data();
		if (d == null)
			return;
		pushUndo();
		if (OrdealAnimatorClient.firstPerson) {
			// in first person there is only the pair of hands to mirror
			mirrorPair(d, toLeft ? "fp_right" : "fp_left", toLeft ? "fp_left" : "fp_right");
		} else {
			mirrorPair(d, toLeft ? "right_arm" : "left_arm", toLeft ? "left_arm" : "right_arm");
			mirrorPair(d, toLeft ? "right_leg" : "left_leg", toLeft ? "left_leg" : "right_leg");
		}
		selection.clear();
	}

	private void mirrorPair(OrdealAnimData d, String from, String to) {
		List<Key> src = d.bones.getOrDefault(from, List.of());
		List<Key> dst = d.channel(to);
		dst.clear();
		for (Key k : src) {
			Key m = k.copy();
			m.rot[1] = -m.rot[1];
			m.rot[2] = -m.rot[2];
			m.pos[0] = -m.pos[0];
			dst.add(m);
		}
	}

	private void resetSelectedBoneAtPlayhead() {
		OrdealAnimData d = data();
		if (d == null)
			return;
		pushUndo();
		Key k = new Key(snap ? Math.round(OrdealAnimatorClient.time) : OrdealAnimatorClient.time);
		d.putKey(OrdealAnimatorClient.selBone, k);
		OrdealAnimatorClient.livePose.remove(OrdealAnimatorClient.selBone);
		flash("Reset " + label(OrdealAnimatorClient.selBone));
	}

	// ------------------------------------------------------------------
	// Gizmo helpers
	// ------------------------------------------------------------------

	private int gizmoAxisAt(double mx, double my) {
		int best = -1;
		double bestDist = 8;
		for (Map.Entry<Integer, float[]> e : OrdealAnimatorClient.gizmoScreen.entrySet()) {
			float[] pts = e.getValue();
			for (int i = 0; i + 3 < pts.length; i += 2) {
				if (Float.isNaN(pts[i]) || Float.isNaN(pts[i + 2]))
					continue;
				double dist = distToSegment(mx, my, pts[i], pts[i + 1], pts[i + 2], pts[i + 3]);
				if (dist < bestDist) {
					bestDist = dist;
					best = e.getKey();
				}
			}
		}
		return best;
	}

	private String nearestBone(double mx, double my, double maxDist) {
		String best = null;
		double bestDist = maxDist;
		for (Map.Entry<String, float[]> e : OrdealAnimatorClient.boneScreen.entrySet()) {
			double dx = mx - e.getValue()[0], dy = my - e.getValue()[1];
			double dist = Math.sqrt(dx * dx + dy * dy);
			if (dist < bestDist) {
				bestDist = dist;
				best = e.getKey();
			}
		}
		return best;
	}

	/** +1/−1 so ring dragging follows the mouse regardless of which side the camera is on. */
	private int gizmoDragSign(int axis) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
		Vec3 pivot = OrdealAnimatorClient.dummyPos.add(0, 1.2, 0);
		Vec3 toCam = camPos.subtract(pivot);
		double comp = axis == OrdealAnimatorClient.AXIS_X ? toCam.x
				: axis == OrdealAnimatorClient.AXIS_Y ? toCam.y : toCam.z;
		return comp >= 0 ? -1 : 1;
	}

	private static double wrapAngle(double a) {
		while (a > Math.PI)
			a -= Math.PI * 2;
		while (a < -Math.PI)
			a += Math.PI * 2;
		return a;
	}

	private static double distToSegment(double px, double py, double x0, double y0, double x1, double y1) {
		double dx = x1 - x0, dy = y1 - y0;
		double len2 = dx * dx + dy * dy;
		double t = len2 <= 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (py - y0) * dy) / len2));
		double cx = x0 + t * dx, cy = y0 + t * dy;
		return Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy));
	}

	private static float value(Pose p, int idx) {
		return switch (idx) {
			case 0 -> p.rx; case 1 -> p.ry; case 2 -> p.rz;
			case 3 -> p.x; case 4 -> p.y; default -> p.z;
		};
	}

	private static void setValue(Pose p, int idx, float v) {
		switch (idx) {
			case 0 -> p.rx = v; case 1 -> p.ry = v; case 2 -> p.rz = v;
			case 3 -> p.x = v; case 4 -> p.y = v; default -> p.z = v;
		}
	}
}