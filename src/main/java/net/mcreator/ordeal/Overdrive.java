package net.mcreator.ordeal;

import net.mcreator.ordeal.core.OrdealTalentChi;
import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "ordeal")
public final class Overdrive {

	public static double STRENGTH_BONUS = OrdealTuning.d("overdrive.strength_bonus", 50);
	public static double CHI_PER_TICK   = OrdealTuning.d("overdrive.chi_per_tick", 1.0);
	public static int    MAX_TICKS      = OrdealTuning.i("overdrive.max_ticks", 300);
	public static int    COOLDOWN       = OrdealTuning.i("overdrive.cooldown_ticks", 1800);
	public static String FX             = "";

	private static final String ON       = "ordeal_overdrive_on";
	private static final String SLOT     = "ordeal_overdrive_slot";
	private static final String BASE     = "ordeal_overdrive_base";
	private static final String UNTIL    = "ordeal_overdrive_until";
	private static final String READY_AT = "ordeal_overdrive_ready_at";
	private static final String DEBT     = "ordeal_overdrive_debt";

	private Overdrive() {}

	public static boolean on(Entity e) {
		return e instanceof Player p && p.getPersistentData().getBoolean(ON);
	}

	public static int cooldownLeft(Entity e) {
		if (!(e instanceof Player p)) return 0;
		return (int) Math.max(0, p.getPersistentData().getLong(READY_AT) - p.level().getGameTime());
	}

	public static int ticksLeft(Entity e) {
		if (!on(e) || !(e instanceof Player p)) return 0;
		return (int) Math.max(0, p.getPersistentData().getLong(UNTIL) - p.level().getGameTime());
	}

	public static boolean start(Entity e) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return false;
		if (on(p)) { stop(p); return false; }
		if (!Enhancements.has(p, Enhancements.OVERDRIVE)) {
			p.displayClientMessage(Component.literal("§4§lYour enhancement is not Overdrive."), true);
			return false;
		}
		if (cooldownLeft(p) > 0) {
			StatusLine.hush(p);
			p.displayClientMessage(Component.literal("§4§lOverdrive is still cooling · "
					+ (cooldownLeft(p) / 20) + "s"), true);
			return false;
		}

		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		int slot = v.talent1_strength >= v.talent2_strength ? 1 : 2;
		double base = slot == 1 ? v.talent1_strength : v.talent2_strength;

		var data = p.getPersistentData();
		data.putBoolean(ON, true);
		data.putInt(SLOT, slot);
		data.putDouble(BASE, base);
		data.putDouble(DEBT, 0);
		data.putLong(UNTIL, p.level().getGameTime() + Math.max(20, MAX_TICKS));

		if (slot == 1) v.talent1_strength = base + STRENGTH_BONUS;
		else v.talent2_strength = base + STRENGTH_BONUS;
		v.markSyncDirty();

		p.displayClientMessage(Component.literal("§c§lOVERDRIVE §7· +"
				+ (int) STRENGTH_BONUS + " talent strength"), true);
		Fx.at(p, FX);
		return true;
	}

	public static void stop(Entity e) {
		if (!(e instanceof Player p)) return;
		var data = p.getPersistentData();
		if (!data.getBoolean(ON)) return;
		data.putBoolean(ON, false);

		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		int slot = data.getInt(SLOT);
		double base = data.getDouble(BASE);
		if (slot == 1) v.talent1_strength = Math.max(base, v.talent1_strength - STRENGTH_BONUS);
		else if (slot == 2) v.talent2_strength = Math.max(base, v.talent2_strength - STRENGTH_BONUS);
		v.markSyncDirty();

		if (!p.level().isClientSide()) {
			data.putLong(READY_AT, p.level().getGameTime() + COOLDOWN);
			p.displayClientMessage(Component.literal("§7the form falls away"), true);
		}
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (p.level().isClientSide() || !on(p)) return;
		if (p.level().getGameTime() >= p.getPersistentData().getLong(UNTIL)) { stop(p); return; }
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

		if (v.chi >= take) { v.chi -= take; v.markSyncDirty(); return true; }
		double rest = take - v.chi;
		int slot = data.getInt(SLOT);
		if (slot != 1 && slot != 2) slot = OrdealTalentChi.slotOf(v, v.talent1_id);
		double reserve = slot == 0 ? 0 : OrdealTalentChi.get(v, slot);
		if (reserve < rest) return false;
		v.chi = 0;
		OrdealTalentChi.set(v, slot, reserve - rest);
		v.markSyncDirty();
		return true;
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		Player p = event.getEntity();
		if (p.getPersistentData().getBoolean(ON)) stop(p);
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		var from = event.getOriginal().getPersistentData();
		var to = event.getEntity().getPersistentData();
		to.putLong(READY_AT, from.getLong(READY_AT));
		if (from.getBoolean(ON)) {
			OrdealModVariables.PlayerVariables v =
					event.getEntity().getData(OrdealModVariables.PLAYER_VARIABLES);
			int slot = from.getInt(SLOT);
			double base = from.getDouble(BASE);
			if (slot == 1) v.talent1_strength = Math.min(v.talent1_strength, base);
			else if (slot == 2) v.talent2_strength = Math.min(v.talent2_strength, base);
			v.markSyncDirty();
		}
		to.putBoolean(ON, false);
	}
}