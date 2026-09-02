package net.mcreator.ordeal.core;

import net.mcreator.ordeal.AbilityHold;
import net.mcreator.ordeal.OrdealTuning;
import net.mcreator.ordeal.core.client.OrdealTalents;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class OrdealTalentChi {

	private OrdealTalentChi() {}

	public static final int PLAYER_ONLY = 0, PLAYER_FIRST = 1, TALENT_FIRST = 2;

	private static final int REGEN_EVERY = 20;

	private static final String[] LOCK_KEY = { "", "ordeal_talentChiLock1", "ordeal_talentChiLock2" };

	public static int lockTicks() { return OrdealCombatState.TICKS; }

	public static void lock(Player p, int slot) {
		if (p == null || slot < 1 || slot > 2) return;
		p.getPersistentData().putLong(LOCK_KEY[slot], p.level().getGameTime() + lockTicks());
	}

	public static boolean locked(Player p, int slot) {
		if (p == null || slot < 1 || slot > 2) return false;
		return p.getPersistentData().getLong(LOCK_KEY[slot]) > p.level().getGameTime();
	}

	public static int slotOf(OrdealModVariables.PlayerVariables v, String talentId) {
		if (talentId == null || talentId.isEmpty() || talentId.equals("none")) return 0;
		if (talentId.equals(v.talent1_id)) return 1;
		if (talentId.equals(v.talent2_id)) return 2;
		return 0;
	}

	public static double get(OrdealModVariables.PlayerVariables v, int slot) {
		return slot == 1 ? v.talent1_Chi : slot == 2 ? v.talent2_Chi : 0;
	}

	public static void set(OrdealModVariables.PlayerVariables v, int slot, double value) {
		double m = max(v, slot);
		double clamped = Math.max(0, Math.min(m, value));
		if (slot == 1) v.talent1_Chi = clamped;
		else if (slot == 2) v.talent2_Chi = clamped;
	}

	public static double max(OrdealModVariables.PlayerVariables v, int slot) {
		return slot == 1 ? v.talent1_ChiMax : slot == 2 ? v.talent2_ChiMax : 0;
	}

	public static double base(OrdealModVariables.PlayerVariables v, int slot) {
		return slot == 1 ? v.talent1_chiBase : slot == 2 ? v.talent2_chiBase : 0;
	}

	public static String idAt(OrdealModVariables.PlayerVariables v, int slot) {
		return slot == 1 ? v.talent1_id : slot == 2 ? v.talent2_id : "none";
	}

	public static double strengthAt(OrdealModVariables.PlayerVariables v, int slot) {
		return slot == 1 ? v.talent1_strength : slot == 2 ? v.talent2_strength : 0;
	}

	public static boolean has(OrdealModVariables.PlayerVariables v, int slot) {
		return max(v, slot) > 0;
	}

	public static boolean recompute(Player p, OrdealModVariables.PlayerVariables v) {
		boolean dirty = false;
		for (int slot = 1; slot <= 2; slot++) {
			OrdealTalents.Talent t = OrdealTalents.get(idAt(v, slot));
			OrdealTalents.TalentChi c = t == null ? null : t.chi;

			double nBase = c == null ? 0 : c.base;
			double nMax  = c == null ? 0 : c.max(strengthAt(v, slot));
			boolean wasEmpty = max(v, slot) <= 0;

			if (base(v, slot) != nBase) {
				if (slot == 1) v.talent1_chiBase = nBase; else v.talent2_chiBase = nBase;
				dirty = true;
			}
			if (max(v, slot) != nMax) {
				if (slot == 1) v.talent1_ChiMax = nMax; else v.talent2_ChiMax = nMax;
				dirty = true;
			}

			if (nMax <= 0) {

				if (get(v, slot) != 0) {
					if (slot == 1) v.talent1_Chi = 0; else v.talent2_Chi = 0;
					dirty = true;
				}
				continue;
			}

			if (wasEmpty && c != null && c.grantFullOnAcquire) {
				if (slot == 1) v.talent1_Chi = nMax; else v.talent2_Chi = nMax;
				dirty = true;
				continue;
			}
			if (get(v, slot) > nMax) {
				if (slot == 1) v.talent1_Chi = nMax; else v.talent2_Chi = nMax;
				dirty = true;
			}
		}
		return dirty;
	}

	public static double OOC_PER_CONTROL = OrdealTuning.d("chi.talent_regen_ooc_per_control", 0.01);

	public static double OOC_MAX_BONUS = OrdealTuning.d("chi.talent_regen_ooc_max_bonus", 1.0);

	public static double IC_PER_STRENGTH = OrdealTuning.d("chi.talent_regen_ic_per_strength", 0.004);

	public static double IC_MAX = OrdealTuning.d("chi.talent_regen_ic_max", 0.6);

	public static double regenRateOutOfCombat(OrdealModVariables.PlayerVariables v, OrdealTalents.Talent t) {
		if (t == null || t.chi == null) return 0;
		double bonus = Math.min(OOC_MAX_BONUS, Math.max(0, v.statChiControl) * OOC_PER_CONTROL);
		return t.chi.regenOutOfCombat * (1.0 + bonus);
	}

	public static double regenRateInCombat(OrdealModVariables.PlayerVariables v, OrdealTalents.Talent t, int slot) {
		if (t == null || t.chi == null) return 0;
		double add = Math.min(IC_MAX, Math.max(0, strengthAt(v, slot)) * IC_PER_STRENGTH);
		return t.chi.regenInCombat + add;
	}

	public static void regen(Player p, OrdealModVariables.PlayerVariables v) {
		if (p.tickCount % REGEN_EVERY != 0) return;
		boolean fighting = OrdealCombatState.inCombat(p);
		boolean dirty = false;
		for (int slot = 1; slot <= 2; slot++) {
			double m = max(v, slot);
			if (m <= 0) continue;
			double cur = get(v, slot);
			if (cur >= m) continue;

			if (locked(p, slot)) continue;
			OrdealTalents.Talent t = OrdealTalents.get(idAt(v, slot));
			if (t == null || t.chi == null) continue;
			double rate = fighting
					? regenRateInCombat(v, t, slot)
					: regenRateOutOfCombat(v, t);
			if (rate <= 0) continue;
			set(v, slot, cur + rate);
			dirty = true;
		}
		if (dirty) v.markSyncDirty();
	}

	public static void slideSlot2ToSlot1(OrdealModVariables.PlayerVariables v) {
		v.talent1_Chi = v.talent2_Chi;
		v.talent1_chiBase = v.talent2_chiBase;
		v.talent1_ChiMax = v.talent2_ChiMax;
		v.talent2_Chi = 0;
		v.talent2_chiBase = 0;
		v.talent2_ChiMax = 0;
	}

	public static void onRespawn(Player p, OrdealModVariables.PlayerVariables v) {
		for (int slot = 1; slot <= 2; slot++) {
			OrdealTalents.Talent t = OrdealTalents.get(idAt(v, slot));
			if (t != null && t.chi != null && t.chi.refillOnDeath) set(v, slot, max(v, slot));
		}
	}

	public static int payMode(OrdealTalents.Ability ab, boolean holding) {
		if (ab == null) return PLAYER_ONLY;
		if (holding && ab.hold != null && ab.hold.pays >= 0) return ab.hold.pays;
		return ab.pays;
	}

	public static boolean pay(Entity e, String abilityName, double amount) {
		if (!(e instanceof Player p) || amount <= 0) return false;
		if (p.level().isClientSide()) return false;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);

		OrdealTalents.Ability ab = OrdealTalents.abilityByName(abilityName);
		int mode = payMode(ab, abilityName.equals(AbilityHold.heldAbility(p)));
		int slot = slotFor(v, abilityName);

		if (mode == PLAYER_ONLY || slot == 0 || max(v, slot) <= 0) {
			if (v.chi < amount) return false;
			v.chi -= amount;
			v.markSyncDirty();
			return true;
		}

		double fromPlayer, fromTalent;
		if (mode == TALENT_FIRST) {
			fromTalent = Math.min(get(v, slot), amount);
			fromPlayer = amount - fromTalent;
		} else {
			fromPlayer = Math.min(v.chi, amount);
			fromTalent = amount - fromPlayer;
		}
		if (fromPlayer > v.chi || fromTalent > get(v, slot)) return false;

		v.chi -= fromPlayer;
		if (fromTalent > 0) { set(v, slot, get(v, slot) - fromTalent); lock(p, slot); }
		v.markSyncDirty();
		return true;
	}

	public static boolean canPay(Entity e, String abilityName, double amount) {
		if (!(e instanceof Player p) || amount <= 0) return false;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(abilityName);
		int mode = payMode(ab, abilityName.equals(AbilityHold.heldAbility(p)));
		int slot = slotFor(v, abilityName);
		if (mode == PLAYER_ONLY || slot == 0) return v.chi >= amount;
		return v.chi + get(v, slot) >= amount;
	}

	public static int slotFor(OrdealModVariables.PlayerVariables v, String abilityName) {
		OrdealTalents.Talent owner = OrdealTalents.ownerOfName(abilityName);
		return owner == null ? 0 : slotOf(v, owner.id);
	}

	public static void prefund(Player p, String abilityName) {
		if (p == null || p.level().isClientSide()) return;
		if (abilityName == null || abilityName.isEmpty()) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);

		OrdealTalents.Ability ab = OrdealTalents.abilityByName(abilityName);
		if (ab == null) return;
		int mode = payMode(ab, abilityName.equals(AbilityHold.heldAbility(p)));
		if (mode == PLAYER_ONLY) return;

		if (AbilityHold.onCooldown(p, abilityName)) return;

		int slot = slotFor(v, abilityName);
		if (slot == 0) return;
		double reserve = get(v, slot);
		if (reserve <= 0) return;

		double cost = effectiveCost(ab, v);
		if (cost <= 0) return;

		double need = mode == TALENT_FIRST ? cost : Math.max(0, cost - v.chi);
		double room = Math.max(0, v.chiMax - v.chi);
		double move = Math.min(Math.min(need, reserve), room);
		if (move <= 0.0001) return;

		set(v, slot, reserve - move);
		v.chi += move;
		v.markSyncDirty();

		var tag = p.getPersistentData();
		tag.putInt(PF_SLOT, slot);
		tag.putDouble(PF_AMT, move);
		tag.putDouble(PF_CHI, v.chi);
		tag.putLong(PF_AT, p.level().getGameTime());
	}

	private static final String PF_SLOT = "ordeal_pfSlot";
	private static final String PF_AMT  = "ordeal_pfAmt";
	private static final String PF_CHI  = "ordeal_pfChi";
	private static final String PF_AT   = "ordeal_pfAt";

	private static final int PF_GRACE = 3;

	public static void reclaim(Player p, OrdealModVariables.PlayerVariables v) {
		var tag = p.getPersistentData();
		int slot = tag.getInt(PF_SLOT);
		if (slot < 1 || slot > 2) return;
		if (p.level().getGameTime() - tag.getLong(PF_AT) < PF_GRACE) return;

		double amount = tag.getDouble(PF_AMT);
		double after = tag.getDouble(PF_CHI);
		tag.putInt(PF_SLOT, 0);
		if (amount <= 0) return;

		if (v.chi < after - 0.0001) {
			lock(p, slot);
			return;
		}
		double back = Math.min(amount, v.chi);
		if (back <= 0) return;
		v.chi -= back;
		set(v, slot, get(v, slot) + back);
		v.markSyncDirty();
	}

	public static double effectiveCost(OrdealTalents.Ability ab, OrdealModVariables.PlayerVariables v) {
		if (ab == null) return 0;
		double cut = Math.min(0.40, v.statChiControl * 0.004);
		return Math.round(ab.chi * (1.0 - cut));
	}

	public static boolean canDraw(Player p, String abilityName, double amount) {
		if (p == null || amount <= 0) return false;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		OrdealTalents.Ability ab = OrdealTalents.abilityByName(abilityName);
		if (ab == null || payMode(ab, true) == PLAYER_ONLY) return false;
		int slot = slotFor(v, abilityName);
		return slot != 0 && get(v, slot) >= amount;
	}

	public static double draw(Player p, String abilityName, double amount) {
		if (!canDraw(p, abilityName, amount)) return 0;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		int slot = slotFor(v, abilityName);
		set(v, slot, get(v, slot) - amount);
		lock(p, slot);
		v.markSyncDirty();
		return amount;
	}

	public static double talentChi(Entity e, double slot) {
		if (!(e instanceof Player p)) return 0;
		return get(p.getData(OrdealModVariables.PLAYER_VARIABLES), (int) slot);
	}

	public static double talentChiMax(Entity e, double slot) {
		if (!(e instanceof Player p)) return 0;
		return max(p.getData(OrdealModVariables.PLAYER_VARIABLES), (int) slot);
	}

	public static double reserveFor(Entity e, String abilityName) {
		if (!(e instanceof Player p)) return 0;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		return get(v, slotFor(v, abilityName));
	}

	public static void grant(Entity e, double slot, double amount) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		set(v, (int) slot, get(v, (int) slot) + amount);
		v.markSyncDirty();
	}
}