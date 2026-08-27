package net.mcreator.ordeal;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;


public final class OrdealAnim {

	private OrdealAnim() {}

	public static void play(Entity target, String name, int loops) {
		play(target, name, loops, -1);
	}

	public static void play(Entity target, String name, int loops, int blend) {
		if (!(target instanceof ServerPlayer sp) || target.level().isClientSide()) return;
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
				new OrdealAnimPayload(false, sp.getId(), name, loops, blend));
	}

	/** Play clips back to back, cross-faded. */
	public static void combo(Entity target, String... names) {
		if (!(target instanceof ServerPlayer sp) || target.level().isClientSide()) return;
		if (names == null || names.length == 0) return;
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
				new OrdealAnimPayload(false, sp.getId(), String.join(",", names), 1, -1));
	}

	public static void stop(Entity target) {
		stopWith(target, "");
	}

	/** Stop, playing a one-shot wind-down first when that clip exists. */
	public static void stopWith(Entity target, String endName) {
		if (!(target instanceof ServerPlayer sp) || target.level().isClientSide()) return;
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
				new OrdealAnimPayload(true, sp.getId(), endName == null ? "" : endName, 1, -1));
	}
}