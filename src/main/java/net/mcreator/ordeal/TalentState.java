package net.mcreator.ordeal;

import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "ordeal")
public final class TalentState {

	public static double CEILING = OrdealTuning.d("talentstate.ceiling", 6.0);

	private static final Map<UUID, Map<String, Double>> SOURCES = new HashMap<>();

	private TalentState() {}

	public static void set(Entity e, String source, double multiplier) {
		if (!(e instanceof Player p) || p.level().isClientSide() || source == null) return;
		Map<String, Double> m = SOURCES.computeIfAbsent(p.getUUID(), k -> new LinkedHashMap<>());
		if (multiplier == 1.0) m.remove(source);
		else m.put(source, multiplier);
		push(p);
	}

	public static void clear(Entity e, String source) {
		set(e, source, 1.0);
	}

	public static void clearAll(Entity e) {
		if (!(e instanceof Player p)) return;
		SOURCES.remove(p.getUUID());
		if (!p.level().isClientSide()) push(p);
	}

	public static double of(Entity e) {
		if (!(e instanceof Player p)) return 1.0;
		Map<String, Double> m = SOURCES.get(p.getUUID());
		if (m == null || m.isEmpty()) return 1.0;
		double out = 1.0;
		for (double d : m.values()) out *= d;
		return Math.min(CEILING, Math.max(0.0, out));
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		SOURCES.remove(event.getEntity().getUUID());
	}

	private static void push(Player p) {
		double value = of(p);
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (v.talentState == value) return;
		v.talentState = value;
		v.markSyncDirty();
	}
}