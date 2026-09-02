package net.mcreator.ordeal;

import net.mcreator.ordeal.core.OrdealTalentChi;
import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "ordeal")
public final class Invincible {

	public static double CHI_PER_TICK = OrdealTuning.d("invincible.chi_per_tick", 1.2);
	public static int    MIN_TICKS    = OrdealTuning.i("invincible.min_ticks", 20);
	public static String FX           = "photon:ilios_phoenixflames";

	private static final String ON   = "ordeal_invincible_on";
	private static final String DEBT = "ordeal_invincible_debt";
	private static final String SINCE = "ordeal_invincible_since";

	private Invincible() {}

	public static boolean on(Entity e) {
		return e instanceof Player p && p.getPersistentData().getBoolean(ON);
	}

	public static boolean toggle(Entity e) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return false;
		if (!Enhancements.has(p, Enhancements.INVINCIBLE)) {
			p.displayClientMessage(Component.literal("§4§lYour enhancement is not Invincible."), true);
			return false;
		}
		if (on(p)) {
			if (p.level().getGameTime() - p.getPersistentData().getLong(SINCE) < MIN_TICKS) return true;
			stop(p);
			return false;
		}
		p.getPersistentData().putBoolean(ON, true);
		p.getPersistentData().putDouble(DEBT, 0);
		p.getPersistentData().putLong(SINCE, p.level().getGameTime());
		p.displayClientMessage(Component.literal("§b§lINTANGIBLE §7· nothing reaches you"), true);
		Fx.at(p, FX);
		return true;
	}

	public static void stop(Entity e) {
		if (!(e instanceof Player p)) return;
		if (!p.getPersistentData().getBoolean(ON)) return;
		p.getPersistentData().putBoolean(ON, false);
		if (!p.level().isClientSide())
			p.displayClientMessage(Component.literal("§7you are solid again"), true);
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (p.level().isClientSide() || !on(p)) return;
		if (!drain(p)) stop(p);
	}

	private static boolean drain(Player p) {
		if (CHI_PER_TICK <= 0) return true;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		var data = p.getPersistentData();
		double debt = data.getDouble(DEBT) + CHI_PER_TICK;
		if (debt < 1) { data.putDouble(DEBT, debt); return true; }
		double take = Math.floor(debt);
		data.putDouble(DEBT, debt - take);

		if (v.chi >= take) {
			v.chi -= take;
			v.markSyncDirty();
			return true;
		}
		double rest = take - v.chi;
		int slot = OrdealTalentChi.slotOf(v, v.talent1_id);
		if (slot == 0) slot = OrdealTalentChi.slotOf(v, v.talent2_id);
		double reserve = slot == 0 ? 0 : OrdealTalentChi.get(v, slot);
		if (reserve < rest) return false;
		v.chi = 0;
		OrdealTalentChi.set(v, slot, reserve - rest);
		v.markSyncDirty();
		return true;
	}

	@SubscribeEvent
	public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
		Player p = event.getEntity();
		if (p.getPersistentData().getBoolean(ON)) p.getPersistentData().putBoolean(ON, false);
	}

	@SubscribeEvent
	public static void onIncoming(LivingIncomingDamageEvent event) {
		if (on(event.getEntity())) event.setCanceled(true);
	}
}