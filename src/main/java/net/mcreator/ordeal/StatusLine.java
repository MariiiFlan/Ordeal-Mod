package net.mcreator.ordeal;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "ordeal")
public final class StatusLine {

	public static boolean ENABLED   = OrdealTuning.i("status.enabled", 1) != 0;
	public static int     EVERY     = OrdealTuning.i("status.every_ticks", 20);
	public static boolean SHOW_SYNC = OrdealTuning.i("status.show_sync", 1) != 0;
	public static int     HUSH_TICKS = OrdealTuning.i("status.hush_ticks", 40);

	private static final Map<UUID, Long> QUIET = new java.util.concurrent.ConcurrentHashMap<>();

	private StatusLine() {}

	public static void hush(Player p) {
		hush(p, HUSH_TICKS);
	}

	public static void hush(Player p, int ticks) {
		if (p == null || ticks <= 0) return;
		QUIET.merge(p.getUUID(), p.level().getGameTime() + ticks, Math::max);
	}

	public static boolean quiet(Player p) {
		Long until = QUIET.get(p.getUUID());
		if (until == null) return false;
		if (p.level().getGameTime() < until) return true;
		QUIET.remove(p.getUUID());
		return false;
	}

	@SubscribeEvent
	public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
		QUIET.remove(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		if (!ENABLED) return;
		Player p = event.getEntity();
		if (p.level().isClientSide()) return;
		if (p.tickCount % Math.max(2, EVERY) != 0) return;
		if (quiet(p)) return;

		String line = build(p);
		if (line.isEmpty()) return;
		if (Synchronisation.DEBUG && Sentinel.cooldownLeft(p) > 0)
			line = line + " §8· SENTINEL CD " + (Sentinel.cooldownLeft(p) / 20) + "s";
		p.displayClientMessage(Component.literal(line), true);
	}

	public static String build(Player p) {
		StringBuilder b = new StringBuilder();

		if (Sentinel.active(p)) {
			add(b, "§6§lSENTINEL §r§7" + Sentinel.secondsLeft(p) + "s");
			return b.toString();
		}

		if (Invincible.on(p)) add(b, "§b§lINTANGIBLE");

		if (Overdrive.on(p))
			add(b, "§c§lOVERDRIVE §r§7" + (Overdrive.ticksLeft(p) / 20) + "s");

		if (SHOW_SYNC && Synchronisation.open(p)) {
			int pct = (int) Synchronisation.percent(p);
			boolean full = Synchronisation.full(p);
			add(b, (full ? "§6" : "§b") + "SYNC " + Synchronisation.bar(p) + " §7" + pct + "%"
					+ (full ? " §6§lSENTINEL READY" : ""));
		}

		return b.toString();
	}

	private static void add(StringBuilder b, String part) {
		if (b.length() > 0) b.append(" §8· ");
		b.append(part);
	}
}