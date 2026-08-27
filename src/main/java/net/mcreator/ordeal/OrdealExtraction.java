package net.mcreator.ordeal.core;

import java.util.UUID;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.mcreator.ordeal.network.OrdealModVariables;

/**
 * Forced extraction. One point of talent strength moves per pulse — a full
 * 100-point steal is two minutes uninterrupted. Taking the last point kills
 * the victim. Past the majority mark the talent starts leaking to the thief
 * on its own, and only drops back when the thief's share falls to half or less.
 */
@EventBusSubscriber(modid = "ordeal")
public class OrdealExtraction {

	/** 120s / 100 points. */
	public static final int PULSE_TICKS = 24;
	public static final double RANGE = 5.0;
	public static final int MAJORITY = 50;
	/** The passive leak is slow: one point every ten seconds. */
	public static final int LEAK_TICKS = 200;

	private static final String EX_VICTIM = "ordeal_ext_victim";
	private static final String EX_SLOT   = "ordeal_ext_slot";
	private static final String EX_TIMER  = "ordeal_ext_t";
	private static final String EX_TAKEN  = "ordeal_ext_taken";
	private static final String LEAK_TO   = "ordeal_leak_to";
	private static final String LEAK_SLOT = "ordeal_leak_slot";
	private static final String LEAK_TIMER = "ordeal_leak_t";

	// ---- API (call from procedures / talent items) --------------------------

	public static OrdealTalentFlow.Result begin(Player thief, Player victim, int slot) {
		if (thief == victim) return OrdealTalentFlow.Result.no("cannot extract from yourself");
		OrdealModVariables.PlayerVariables vv = victim.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (!OrdealTalentFlow.has(vv, slot)) return OrdealTalentFlow.Result.no("victim has nothing in slot " + slot);

		String id = OrdealTalentFlow.talentIn(vv, slot);
		OrdealModVariables.PlayerVariables tv = thief.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (OrdealTalentFlow.slotOf(tv, id) == 0 && OrdealTalentFlow.freeSlot(tv) == 0)
			return OrdealTalentFlow.Result.no("no free talent slot");

		thief.getPersistentData().putString(EX_VICTIM, victim.getStringUUID());
		thief.getPersistentData().putInt(EX_SLOT, slot);
		thief.getPersistentData().putInt(EX_TIMER, 0);
		thief.getPersistentData().putInt(EX_TAKEN, 0);
		OrdealTalentFlow.tell(thief, "§6Extraction started — hold it for two minutes.");
		OrdealTalentFlow.tell(victim, "§cSomething is pulling your talent out of you.");
		return OrdealTalentFlow.Result.OK;
	}

	public static void stop(Player thief) {
		if (!thief.getPersistentData().contains(EX_VICTIM)) return;
		thief.getPersistentData().remove(EX_VICTIM);
		thief.getPersistentData().remove(EX_SLOT);
		thief.getPersistentData().remove(EX_TIMER);
	}

	public static boolean channeling(Player thief) {
		return thief.getPersistentData().contains(EX_VICTIM);
	}

	// ---- tick ---------------------------------------------------------------

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player p = event.getEntity();
		if (p.level().isClientSide()) return;
		if (p.getPersistentData().contains(EX_VICTIM)) channelTick(p);
		if (p.getPersistentData().contains(LEAK_TO)) leakTick(p);
	}

	private static void channelTick(Player thief) {
		Player victim = find(thief, thief.getPersistentData().getString(EX_VICTIM));
		int slot = thief.getPersistentData().getInt(EX_SLOT);

		if (victim == null || victim.isDeadOrDying()
				|| thief.distanceTo(victim) > RANGE
				|| !OrdealTalentFlow.has(victim.getData(OrdealModVariables.PLAYER_VARIABLES), slot)) {
			interrupt(thief, victim, slot);
			return;
		}

		int t = thief.getPersistentData().getInt(EX_TIMER) + 1;
		thief.getPersistentData().putInt(EX_TIMER, t);
		if (t % PULSE_TICKS != 0) return;

		if (!movePoint(victim, slot, thief)) interrupt(thief, victim, slot);
	}

	/** Channel broke. Past the majority the talent keeps leaking on its own. */
	private static void interrupt(Player thief, Player victim, int slot) {
		stop(thief);
		if (victim == null) return;
		OrdealModVariables.PlayerVariables vv = victim.getData(OrdealModVariables.PLAYER_VARIABLES);
		if (!OrdealTalentFlow.has(vv, slot)) return;
		String id = OrdealTalentFlow.talentIn(vv, slot);
		OrdealModVariables.PlayerVariables tv = thief.getData(OrdealModVariables.PLAYER_VARIABLES);
		int tSlot = OrdealTalentFlow.slotOf(tv, id);
		if (tSlot != 0 && OrdealTalentFlow.strengthIn(tv, tSlot) > MAJORITY) {
			victim.getPersistentData().putString(LEAK_TO, thief.getStringUUID());
			victim.getPersistentData().putInt(LEAK_SLOT, slot);
			victim.getPersistentData().putInt(LEAK_TIMER, 0);
			OrdealTalentFlow.tell(victim, "§cThey hold more of it than you now. It keeps slipping away.");
		}
	}

	private static void leakTick(Player victim) {
		OrdealModVariables.PlayerVariables vv = victim.getData(OrdealModVariables.PLAYER_VARIABLES);
		int slot = victim.getPersistentData().getInt(LEAK_SLOT);
		Player thief = find(victim, victim.getPersistentData().getString(LEAK_TO));

		if (thief == null || !OrdealTalentFlow.has(vv, slot)) { clearLeak(victim); return; }

		String id = OrdealTalentFlow.talentIn(vv, slot);
		OrdealModVariables.PlayerVariables tv = thief.getData(OrdealModVariables.PLAYER_VARIABLES);
		int tSlot = OrdealTalentFlow.slotOf(tv, id);
		// Steal it back below the majority mark and the leak dies.
		if (tSlot == 0 || OrdealTalentFlow.strengthIn(tv, tSlot) <= MAJORITY) { clearLeak(victim); return; }

		int t = victim.getPersistentData().getInt(LEAK_TIMER) + 1;
		victim.getPersistentData().putInt(LEAK_TIMER, t);
		if (t % LEAK_TICKS != 0) return;

		if (!movePoint(victim, slot, thief)) clearLeak(victim);
		else if (!OrdealTalentFlow.has(vv, slot)) clearLeak(victim);
	}

	private static void clearLeak(Player victim) {
		victim.getPersistentData().remove(LEAK_TO);
		victim.getPersistentData().remove(LEAK_SLOT);
		victim.getPersistentData().remove(LEAK_TIMER);
	}

	/** One point, victim to thief. Kills the victim when the last point goes. */
	private static boolean movePoint(Player victim, int slot, Player thief) {
		OrdealModVariables.PlayerVariables vv = victim.getData(OrdealModVariables.PLAYER_VARIABLES);
		String id = OrdealTalentFlow.talentIn(vv, slot);
		double left = OrdealTalentFlow.strengthIn(vv, slot);

		OrdealModVariables.PlayerVariables tv = thief.getData(OrdealModVariables.PLAYER_VARIABLES);
		int tSlot = OrdealTalentFlow.slotOf(tv, id);
		if (tSlot == 0) {
			if (OrdealTalentFlow.room(tv) < 1 || OrdealTalentFlow.freeSlot(tv) == 0) return false;
			OrdealTalentFlow.grant(thief, id, 0, "extract:" + victim.getGameProfile().getName());
			tSlot = OrdealTalentFlow.slotOf(tv, id);
			if (tSlot == 0) return false;
		} else if (OrdealTalentFlow.strengthIn(tv, tSlot) >= 150 || OrdealTalentFlow.room(tv) < 1) {
			return false;
		}

		OrdealTalentFlow.setStrength(tv, tSlot, OrdealTalentFlow.strengthIn(tv, tSlot) + 1);
		tv.markSyncDirty();

		int taken = thief.getPersistentData().getInt(EX_TAKEN) + 1;
		thief.getPersistentData().putInt(EX_TAKEN, taken);

		if (taken > 50) {
			// crossing the halfway mark of a talent is what kills — the rest
			// of it follows the thief as the body lets go
			double rest = Math.max(0, left - 1);
			double roomLeft = Math.min(150 - OrdealTalentFlow.strengthIn(tv, tSlot), OrdealTalentFlow.room(tv));
			double bonus = Math.max(0, Math.min(rest, roomLeft));
			if (bonus > 0) {
				OrdealTalentFlow.setStrength(tv, tSlot, OrdealTalentFlow.strengthIn(tv, tSlot) + bonus);
				tv.markSyncDirty();
			}
			OrdealTalentFlow.strip(victim, slot);
			victim.hurt(victim.damageSources().magic(), Float.MAX_VALUE);
			OrdealTalentFlow.tell(thief, "§6" + id.toUpperCase() + " is yours entirely.");
		} else if (left - 1 <= 0) {
			// drained dry under the halfway mark: they lose the talent but live
			OrdealTalentFlow.strip(victim, slot);
			OrdealTalentFlow.tell(thief, "§6" + id.toUpperCase() + " is yours — they had little left to take.");
			OrdealTalentFlow.tell(victim, "§cYour talent is gone.");
		} else {
			OrdealTalentFlow.setStrength(vv, slot, left - 1);
			vv.markSyncDirty();
		}
		return true;
	}

	private static Player find(Player near, String uuid) {
		if (uuid == null || uuid.isEmpty()) return null;
		try {
			if (near.level() instanceof net.minecraft.server.level.ServerLevel sl) {
				var e = sl.getServer().getPlayerList().getPlayer(UUID.fromString(uuid));
				return e != null && e.level() == near.level() ? e : (Player) null;
			}
		} catch (IllegalArgumentException ignored) {}
		return null;
	}
}