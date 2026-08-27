package net.mcreator.ordeal.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Camera shake driven by a "screen_shake" mob effect. Amplifier sets strength, and it tapers
 * over the last few ticks so it never cuts off hard.
 *
 * Found by registry path, not by importing the generated class - compiles whether or not the
 * effect element exists yet.
 */
@EventBusSubscriber(modid = "ordeal", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class CameraShake {

	private CameraShake() {}

	private static final String EFFECT_PATH = "screen_shake";

	/** Degrees at amplifier 0. Each amplifier level adds this again. */
	public static float PER_LEVEL = 0.7f;
	public static float MAX_SHAKE = 3.0f;
	/** Ease rate toward the target each frame. Lower is heavier. */
	public static float SMOOTH = 0.25f;
	/** Ticks of taper at the end of the effect. */
	public static int FADE_TICKS = 8;

	private static float smooth = 0f;

	@SubscribeEvent
	public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
		Player p = Minecraft.getInstance().player;
		if (p == null) return;

		float target = 0f;
		MobEffectInstance shake = find(p);
		if (shake != null) {
			target = Math.min(MAX_SHAKE, (shake.getAmplifier() + 1) * PER_LEVEL);
			int left = shake.getDuration();
			if (left < FADE_TICKS) target *= (float) left / FADE_TICKS;
		}

		smooth += (target - smooth) * SMOOTH;
		if (smooth < 0.001f) return;

		float t = (float) (p.tickCount + event.getPartialTick());
		float sx = Mth.sin(t * 2.7f) + 0.5f * Mth.sin(t * 5.3f);
		float sy = Mth.sin(t * 3.1f + 1.3f) + 0.5f * Mth.sin(t * 6.1f);
		float sz = Mth.sin(t * 2.3f + 2.1f) + 0.5f * Mth.sin(t * 4.7f);

		event.setPitch(event.getPitch() + sx * smooth);
		event.setYaw(event.getYaw() + sy * smooth);
		event.setRoll(event.getRoll() + sz * smooth * 1.6f);
	}

	private static MobEffectInstance find(Player p) {
		for (MobEffectInstance e : p.getActiveEffects()) {
			var key = e.getEffect().unwrapKey().orElse(null);
			if (key != null
					&& key.location().getNamespace().equals("ordeal")
					&& key.location().getPath().equals(EFFECT_PATH))
				return e;
		}
		return null;
	}
}