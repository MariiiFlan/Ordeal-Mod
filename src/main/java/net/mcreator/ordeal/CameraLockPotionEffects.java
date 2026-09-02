package net.mcreator.ordeal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class CameraLockPotionEffects {

	private CameraLockPotionEffects() {}

	private static MobEffect FX;
	private static boolean LOOKED = false;

	private static MobEffect effect() {
		if (FX != null) return FX;
		if (LOOKED) return null;
		LOOKED = true;
		for (var en : BuiltInRegistries.MOB_EFFECT.entrySet()) {
			if (!en.getKey().location().getNamespace().equals("ordeal")) continue;
			String path = en.getKey().location().getPath().replace("_", "").toLowerCase(java.util.Locale.ROOT);
			if (path.startsWith("cameralock")) {
				FX = en.getValue();
				break;
			}
		}
		if (FX == null)
			System.err.println("[Ordeal] camera lock asked for but no ordeal:camera_lock* effect is registered");
		return FX;
	}

	public static void lock(Entity e, int ticks) {
		if (!(e instanceof LivingEntity le) || le.level().isClientSide() || ticks <= 0) return;
		var fx = effect();
		if (fx == null) return;
		le.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(fx), ticks, 0, false, false));
	}

	public static void unlock(Entity e) {
		if (!(e instanceof LivingEntity le) || le.level().isClientSide()) return;
		var fx = effect();
		if (fx == null) return;
		le.removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(fx));
	}

	public static boolean locked(Entity e) {
		if (!(e instanceof LivingEntity le)) return false;
		var fx = effect();
		return fx != null && le.hasEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(fx));
	}

	@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
	public static final class Client {
		private static boolean wasLocked = false;
		private static float lockedYaw = 0;
		private static float lockedPitch = 0;

		@SubscribeEvent
		public static void onClientTick(ClientTickEvent.Post event) {
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer player = mc.player;
			if (player == null) return;
			if (locked(player)) {
				if (!wasLocked) {
					lockedYaw = player.getYRot();
					lockedPitch = player.getXRot();
					wasLocked = true;
				}
				player.setYRot(lockedYaw);
				player.setXRot(lockedPitch);
				player.setYHeadRot(lockedYaw);
				player.setYBodyRot(lockedYaw);
			} else {
				wasLocked = false;
			}
		}
	}
}