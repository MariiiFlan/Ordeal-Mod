package net.mcreator.ordeal;

import net.mcreator.ordeal.core.OrdealActionMessage;
import net.mcreator.ordeal.init.OrdealModKeyMappings;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/**
 * THE PANOPLY EQUIP KEY.
 *
 * Reads YOUR keybind - the PanoplyEquip you made in MCreator, currently on N.
 * Nothing to wire: it is a plain KeyMapping with no procedure attached, so this
 * polls isDown() and works out press and release itself. Rebind it in the
 * controls menu and everything here follows.
 *
 * It must stay OUT of the consumeClick() list in OrdealModKeyMappings - which
 * is how MCreator generated it. consumeClick eats a press per tick, and this is
 * a HELD key, not a tapped one.
 *
 * WHAT IT DOES
 *   hold                    the equip bar lights up and numbers itself
 *   hold + 1..9             draw that entry into your main hand
 *   hold + SCROLL           step along the bar, drawing as you go - and the
 *                           vanilla hotbar does NOT move while you do it
 *   the same number again   stow it, your original item comes back
 *   SNEAK + hold + 1..9     take that item OUT into your inventory
 *   release without a number, something drawn -> stows it
 *
 * Nothing here decides anything: it reads keys and asks. The server does every
 * move, so a client cannot conjure an item by lying about a point.
 */
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class PanoplyKeys {

	private PanoplyKeys() {}

	private static boolean held = false;
	private static boolean usedWhileHeld = false;
	private static long lastScrollTick = -1;

	/** True while the equip key is down. The HUD reads this to light up. */
	public static boolean equipHeld() { return held; }

	/**
	 * Poll the key and turn it into press / release edges.
	 *
	 * Also the safety net: with a screen open or no player, the key is treated
	 * as released, so alt-tabbing or opening the inventory mid-press cannot
	 * latch the HUD on forever.
	 */
	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		boolean down;
		try {
			down = mc.player != null && mc.screen == null
					&& OrdealModKeyMappings.PANOPLY_EQUIP.isDown();
		} catch (Throwable t) {
			down = false;
		}
		if (down == held) return;

		held = down;
		if (!down) {
			// released without picking a number: stow whatever is out
			if (!usedWhileHeld && PanoplyPayload.Client.DRAWN >= 0) send("panoply_stow", 0);
			usedWhileHeld = false;
		}
	}

	/**
	 * Optional override, if you ever want a procedure to drive this instead of
	 * the key - hold(true) on press, hold(false) on release. The tick above will
	 * take it back over the moment the real key changes.
	 */
	public static void hold(boolean down) {
		if (down == held) return;
		held = down;
		if (!down) {
			if (!usedWhileHeld && PanoplyPayload.Client.DRAWN >= 0) send("panoply_stow", 0);
			usedWhileHeld = false;
		}
	}

	@SubscribeEvent
	public static void onKey(InputEvent.Key event) {
		if (!held) return;
		if (event.getAction() != GLFW.GLFW_PRESS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null) return;

		int idx = numberFor(event.getKey());
		if (idx < 0) return;

		int pt = PanoplyHud.pointForBarIndex(idx);
		if (pt < 0) return;

		send(mc.player.isShiftKeyDown() ? "panoply_take" : "panoply_draw", pt);
		usedWhileHeld = true;
	}

	/**
	 * SCROLL WHILE HELD - step along the equip bar instead of your hotbar.
	 *
	 * Cancelled, so the vanilla hotbar selection stays exactly where it was:
	 * you come out of a draw holding what you were holding, not three slots
	 * over. Scrolling up goes to the previous entry, matching the hotbar.
	 *
	 * Rate-limited to one step per client tick. A notched wheel can fire
	 * several events in a frame, and without this a flick would rifle through
	 * six items and send six packets.
	 */
	@SubscribeEvent
	public static void onScroll(InputEvent.MouseScrollingEvent event) {
		if (!held) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null) return;

		double dy = event.getScrollDeltaY();
		if (dy == 0) return;
		event.setCanceled(true);            // the hotbar must not move

		long now = mc.player.tickCount;
		if (now == lastScrollTick) return;
		lastScrollTick = now;

		int n = PanoplyHud.barCount();
		if (n <= 0) return;

		int cur = PanoplyHud.barIndexOfDrawn();
		int step = dy > 0 ? -1 : 1;         // up = previous, like the hotbar
		int next = cur < 0 ? (step > 0 ? 0 : n - 1) : ((cur + step) % n + n) % n;

		int pt = PanoplyHud.pointForBarIndex(next);
		if (pt < 0) return;

		send("panoply_draw", pt);
		usedWhileHeld = true;
	}

	/** GLFW key -> bar index 0..8, or -1. Top-row numbers and the numpad. */
	private static int numberFor(int key) {
		if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) return key - GLFW.GLFW_KEY_1;
		if (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_9) return key - GLFW.GLFW_KEY_KP_1;
		return -1;
	}

	private static void send(String action, int point) {
		PacketDistributor.sendToServer(new OrdealActionMessage(action, "", point));
	}

	// ---- called by PanoplyScreen ----

	/** Click a point with something on the cursor: place it. */
	public static void place(int point)  { send("panoply_place", point); }
	/** Click a filled point with an empty cursor: wear it / just carry it. */
	public static void toggle(int point) { send("panoply_active", point); }
	/** Shift-click a point: take it out into the inventory. */
	public static void take(int point)   { send("panoply_take", point); }
	/** Right-click a point: draw it to hand. */
	public static void draw(int point)   { send("panoply_draw", point); }

	/** Drag a point onto equip bar slot i. Point -1 empties the slot. */
	public static void bindBar(int slotIdx, int point) {
		PacketDistributor.sendToServer(
				new OrdealActionMessage("panoply_bar", String.valueOf(slotIdx), point));
	}

	/** Drag one equip bar slot onto another. */
	public static void swapBar(int a, int b) {
		PacketDistributor.sendToServer(
				new OrdealActionMessage("panoply_bar_swap", String.valueOf(a), b));
	}

	/** Throw the arrangement away and go back to auto-filling from what you carry. */
	public static void resetBar() { send("panoply_bar_reset", 0); }

	/**
	 * Place out of the inventory grid on the panoply page: pick a slot, click a
	 * point. The slot number rides in the arg field and the point in the value,
	 * and the server reads the stack out of its own inventory - the client never
	 * says WHAT is being placed, only where it came from.
	 */
	public static void placeFrom(int invSlot, int point) {
		PacketDistributor.sendToServer(
				new OrdealActionMessage("panoply_place_inv", String.valueOf(invSlot), point));
	}
}