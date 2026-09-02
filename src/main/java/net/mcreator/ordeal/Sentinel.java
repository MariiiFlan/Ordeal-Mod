package net.mcreator.ordeal;

import net.mcreator.ordeal.core.OrdealTalentChi;
import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "ordeal")
public final class Sentinel {

	public static int     SECONDS        = OrdealTuning.i("sentinel.seconds", 10);
	public static int     COOLDOWN       = OrdealTuning.i("sentinel.cooldown_ticks", 12000);
	public static boolean ABILITIES_LOCKED = OrdealTuning.i("sentinel.abilities_locked", 1) != 0;
	public static boolean EMPTY_CHI_ON_EXIT = OrdealTuning.i("sentinel.empty_chi_on_exit", 0) != 0;
	public static boolean CONSUMES_SYNC  = OrdealTuning.i("sentinel.consumes_sync", 0) != 0;
	public static String  FX             = "photon:ilios_phoenixflames_startup";
	public static String  ANIM_3P        = "";

	private static final String UNTIL    = "ordeal_sentinel_until";
	private static final String READY_AT = "ordeal_sentinel_ready_at";

	private Sentinel() {}

	public static boolean active(Entity e) {
		return e instanceof Player p && p.level().getGameTime() < p.getPersistentData().getLong(UNTIL);
	}

	public static int secondsLeft(Entity e) {
		if (!(e instanceof Player p)) return 0;
		return (int) Math.max(0, (p.getPersistentData().getLong(UNTIL) - p.level().getGameTime()) / 20);
	}

	public static int cooldownLeft(Entity e) {
		if (!(e instanceof Player p)) return 0;
		return (int) Math.max(0, p.getPersistentData().getLong(READY_AT) - p.level().getGameTime());
	}

	public static boolean unlocked(Entity e) {
		return Synchronisation.full(e);
	}

	public static boolean abilitiesBlocked(Entity e) {
		return ABILITIES_LOCKED && active(e);
	}

	public static boolean enter(Entity e) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return false;
		if (active(p)) return true;
		if (!unlocked(p)) {
			StatusLine.hush(p);
			p.displayClientMessage(Component.literal("§4§lNot synchronised · "
					+ (int) Synchronisation.percent(p) + "%"), true);
			return false;
		}
		if (cooldownLeft(p) > 0) {
			p.displayClientMessage(Component.literal("§4§lSentinel is still cooling · "
					+ (cooldownLeft(p) / 20) + "s"), true);
			return false;
		}
		var data = p.getPersistentData();
		data.putLong(UNTIL, p.level().getGameTime() + Math.max(20L, SECONDS * 20L));
		data.putLong(READY_AT, p.level().getGameTime() + COOLDOWN + Math.max(20L, SECONDS * 20L));
		if (CONSUMES_SYNC) Synchronisation.reset(p);
		p.sendSystemMessage(Component.literal("§6§lSENTINEL §7· you cannot be touched, and you cannot act"));
		Fx.at(p, FX);
		if (!ANIM_3P.isEmpty()) AnimFx.play(p, ANIM_3P);
		return true;
	}

	public static void exit(Entity e) {
		if (!(e instanceof Player p)) return;
		if (p.getPersistentData().getLong(UNTIL) == 0) return;
		p.getPersistentData().putLong(UNTIL, 0);
		if (EMPTY_CHI_ON_EXIT && !p.level().isClientSide()) {
			OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
			v.chi = 0;
			int slot = OrdealTalentChi.slotOf(v, v.talent1_id);
			if (slot != 0) OrdealTalentChi.set(v, slot, 0);
			v.markSyncDirty();
		}
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (p.level().isClientSide()) return;
		long until = p.getPersistentData().getLong(UNTIL);
		if (until == 0 || p.level().getGameTime() < until) return;
		exit(p);
		p.displayClientMessage(Component.literal("§7the fusion ends"), true);
	}

	@SubscribeEvent
	public static void onIncoming(LivingIncomingDamageEvent event) {
		if (active(event.getEntity())) event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		var from = event.getOriginal().getPersistentData();
		var to = event.getEntity().getPersistentData();
		to.putLong(READY_AT, from.getLong(READY_AT));
	}

}