package net.mcreator.ordeal.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class OrdealFx {

	private OrdealFx() {}

	public static void spawn(Entity target, String colorHex, String fxId) {
		if (!valid(target, fxId)) return;
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
				new OrdealFxPayload(ResourceLocation.parse(fxId), target.getId(), argb(colorHex)));
	}

	/** Tints with the target's talent accent, resolved client-side. White for humans. */
	public static void spawnAccent(Entity target, String fxId) {
		if (!valid(target, fxId)) return;
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
				new OrdealFxPayload(ResourceLocation.parse(fxId), target.getId(), OrdealFxPayload.TALENT_ACCENT));
	}

	public static void spawnTrail(Entity target, String colorHex, String fxId) {
		if (!valid(target, fxId)) return;
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
				new OrdealFxPayload(ResourceLocation.parse(fxId), target.getId(), argb(colorHex),
						0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f,
						0, false, true, "none", OrdealFxPayload.TINT_TRAIL));
	}

	public static void spawn(Entity target, String colorHex, String fxId,
			double offX, double offY, double offZ,
			double rotX, double rotY, double rotZ,
			double scaleX, double scaleY, double scaleZ,
			int delay, boolean forceDeath, boolean allowMulti, String autoRotate) {
		if (!valid(target, fxId)) return;
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
				new OrdealFxPayload(ResourceLocation.parse(fxId), target.getId(), argb(colorHex),
						(float) offX, (float) offY, (float) offZ,
						(float) rotX, (float) rotY, (float) rotZ,
						(float) scaleX, (float) scaleY, (float) scaleZ,
						delay, forceDeath, allowMulti, autoRotate == null ? "none" : autoRotate,
						OrdealFxPayload.TINT_START));
	}

	private static boolean valid(Entity target, String fxId) {
		return target != null && fxId != null && !fxId.isEmpty()
				&& target.level() instanceof ServerLevel;
	}

	/** "#RRGGBB" / "RRGGBB" -> opaque; "#AARRGGBB" as-is; blank/none/bad -> white. */
	public static int argb(String colorHex) {
		String s = colorHex == null ? "" : colorHex.trim();
		if (s.isEmpty() || s.equalsIgnoreCase("none")) return 0xFFFFFFFF;
		if (s.startsWith("#")) s = s.substring(1);
		try {
			long v = Long.parseLong(s, 16);
			if (s.length() <= 6) return 0xFF000000 | (int) (v & 0xFFFFFFL);
			return (int) v;
		} catch (NumberFormatException e) {
			return 0xFFFFFFFF;
		}
	}
}