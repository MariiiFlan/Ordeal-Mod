package net.mcreator.ordeal;

import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "ordeal")
public final class Synchronisation {

	public static double START_STRENGTH = OrdealTuning.d("sync.start_strength", 150);
	public static double FULL_AT        = OrdealTuning.d("sync.full_at", 10000);
	public static double PER_CHI_SPENT  = OrdealTuning.d("sync.per_chi_spent", 1.0);
	public static double PER_DAMAGE_DEALT = OrdealTuning.d("sync.per_damage_dealt", 2.0);
	public static double PER_DAMAGE_TAKEN = OrdealTuning.d("sync.per_damage_taken", 3.0);
	public static int    ANNOUNCE_EVERY_PERCENT = OrdealTuning.i("sync.announce_every_percent", 25);
	public static boolean DEBUG = OrdealTuning.i("sync.debug", 0) != 0;

	private static final String BAR  = "ordeal_sync";
	private static final String MARK = "ordeal_sync_mark";

	private Synchronisation() {}

	public static boolean open(Entity e) {
		if (!(e instanceof Player p)) return false;
		if (DEBUG) return true;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);
		return Math.max(v.talent1_strength, v.talent2_strength) >= START_STRENGTH;
	}

	public static double value(Entity e) {
		return e instanceof Player p ? p.getPersistentData().getDouble(BAR) : 0;
	}

	public static double percent(Entity e) {
		return Math.min(100.0, value(e) / Math.max(1, FULL_AT) * 100.0);
	}

	public static boolean full(Entity e) {
		return value(e) >= FULL_AT;
	}

	public static void gain(Entity e, double amount) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return;
		if (amount <= 0 || !open(p) || full(p)) return;
		var data = p.getPersistentData();
		double before = data.getDouble(BAR);
		double after = Math.min(FULL_AT, before + amount);
		data.putDouble(BAR, after);

		int step = Math.max(1, ANNOUNCE_EVERY_PERCENT);
		int wasMark = data.getInt(MARK);
		int nowMark = (int) (after / Math.max(1, FULL_AT) * 100.0) / step;
		if (nowMark > wasMark) {
			data.putInt(MARK, nowMark);
			if (after >= FULL_AT)
				p.sendSystemMessage(Component.literal("§6§lSYNCHRONISED §7· the talent will answer as itself now"));
			else {
				StatusLine.hush(p);
				p.displayClientMessage(Component.literal("§eSYNCHRONISATION §7"
						+ (int) percent(p) + "%"), true);
			}
		}
	}

	public static void chiSpent(Entity e, double chi) { gain(e, chi * PER_CHI_SPENT); }

	public static void set(Entity e, double percent) {
		if (!(e instanceof Player p) || p.level().isClientSide()) return;
		double clamped = Math.max(0, Math.min(100, percent));
		p.getPersistentData().putDouble(BAR, FULL_AT * clamped / 100.0);
		p.getPersistentData().putInt(MARK, (int) clamped / Math.max(1, ANNOUNCE_EVERY_PERCENT));
		p.displayClientMessage(Component.literal("§eSYNCHRONISATION §7set to " + (int) clamped + "%"), true);
	}

	public static void fill(Entity e) {
		set(e, 100);
	}

	public static String bar(Entity e) {
		int filled = (int) Math.round(percent(e) / 10.0);
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < 10; i++) s.append(i < filled ? "▰" : "▱");
		return s.toString();
	}

	public static void reset(Entity e) {
		if (e instanceof Player p) {
			p.getPersistentData().putDouble(BAR, 0);
			p.getPersistentData().putInt(MARK, 0);
		}
	}

	@SubscribeEvent
	public static void onDamage(LivingIncomingDamageEvent event) {
		if (event.isCanceled()) return;
		if (event.getEntity() instanceof Player hurt && !hurt.level().isClientSide())
			gain(hurt, event.getAmount() * PER_DAMAGE_TAKEN);
		if (event.getSource().getEntity() instanceof Player dealt && !dealt.level().isClientSide())
			gain(dealt, event.getAmount() * PER_DAMAGE_DEALT);
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		var from = event.getOriginal().getPersistentData();
		var to = event.getEntity().getPersistentData();
		to.putDouble(BAR, from.getDouble(BAR));
		to.putInt(MARK, from.getInt(MARK));
	}
}