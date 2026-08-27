package net.mcreator.ordeal;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Chain window. Landing anything opens a window; landing again inside it links.
 * Links make hits harder and cheaper, so Agility and Chi Control — the two stats
 * that let you act more often — decide how long a chain you can hold.
 */
@EventBusSubscriber(modid = "ordeal")
public final class OrdealCombo {

	private OrdealCombo() {}

	public static int WINDOW_TICKS = OrdealTuning.i("combo.window_ticks", 40);
	public static double DAMAGE_PER_LINK = OrdealTuning.d("combo.damage_per_link", 0.06);
	public static double COST_PER_LINK   = OrdealTuning.d("combo.cost_per_link", 0.10);
	public static int MAX_LINKS = OrdealTuning.i("combo.max_links", 20);

	private static final String COUNT = "ordeal_combo";
	private static final String TIMER = "ordeal_combo_t";

	public static int count(Player p) {
		return p == null ? 0 : p.getPersistentData().getInt(COUNT);
	}

	public static int remaining(Player p) {
		return p == null ? 0 : p.getPersistentData().getInt(TIMER);
	}

	public static double damageMult(Player p) {
		return 1.0 + Math.min(MAX_LINKS, count(p)) * DAMAGE_PER_LINK;
	}

	public static double costMult(Player p) {
		return Math.max(0.35, 1.0 - Math.min(MAX_LINKS, count(p)) * COST_PER_LINK);
	}

	/** A hit landed on {@code target}. Extends the window and adds a link. */
	public static void land(Player p, net.minecraft.world.entity.Entity target) {
		if (p == null || p.level().isClientSide()) return;
		p.getPersistentData().putInt(COUNT, Math.min(999, count(p) + 1));
		p.getPersistentData().putInt(TIMER, WINDOW_TICKS);
		sync(p, target);
	}

	/** Guard broken, or the chain otherwise dropped. */
	public static void drop(Player p) {
		if (p == null) return;
		p.getPersistentData().putInt(COUNT, 0);
		p.getPersistentData().putInt(TIMER, 0);
		sync(p, null);
	}

	/**
	 * Combo lives in persistentData, which never leaves the server on its own —
	 * the HUD reads the client copy, so every change is mirrored down in a tiny
	 * COMBO payload. The client then runs its own timer for a smooth drain bar.
	 */
	private static void sync(Player p, net.minecraft.world.entity.Entity target) {
		if (p instanceof net.minecraft.server.level.ServerPlayer sp)
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
					new OrdealVfxPayload(OrdealVfxPayload.COMBO,
							target == null ? -1 : target.getId(), 0, 0, count(p)));
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		int t = p.getPersistentData().getInt(TIMER);
		if (t <= 0) return;
		if (--t <= 0) drop(p);
		else p.getPersistentData().putInt(TIMER, t);
	}
}