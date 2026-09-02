package net.mcreator.ordeal;

import net.mcreator.ordeal.network.OrdealModVariables;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * CAMERA LEAN — the view rolls with the body when you bank.
 *
 * Built the way Fisk's FlightMouseManager does it, because that is what "smooth
 * and works in both views" actually requires:
 *
 *   - the roll is advanced once per CLIENT TICK, keeping the previous tick's
 *     value, and the frame interpolates between the two by partial tick. Easing
 *     per frame instead makes the rate depend on framerate and reads as jitter.
 *   - it hangs off ViewportEvent.ComputeCameraAngles, which runs for the camera
 *     itself, so FIRST and THIRD person both get it. The old version leaned on
 *     state that only advanced while your model was being rendered — which never
 *     happens in first person, so the roll sat at zero there.
 *
 * The angle comes from OrdealFlightLean, the same number OrdealRootRender tilts
 * the body by, so the camera and the body can never disagree.
 *
 * CAMERA_ROLL is the whole dial. 0 turns it off without removing the file.
 */
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class OrdealFlightCamera {

	private OrdealFlightCamera() {}

	public static boolean ENABLED = true;

	/**
	 * Share of the body's roll the camera copies. 0.34 reads well — enough to
	 * feel the bank, not enough to be sick. 1.0 locks the horizon to your
	 * shoulders; 0 leaves the camera level.
	 */
	public static float CAMERA_ROLL = 0.34f;

	/** Camera roll is capped here regardless of how hard the body banks. */
	public static float MAX_ROLL = 35f;

	/** Ticks to ramp in on take-off and out on landing, so nothing snaps. */
	public static float FADE_TICKS = 6f;

	private static float fade, prevFade;

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) { fade = prevFade = 0f; return; }
		if (mc.isPaused()) return;

		prevFade = fade;

		boolean flying = false;
		try {
			OrdealModVariables.PlayerVariables v = mc.player.getData(OrdealModVariables.PLAYER_VARIABLES);
			flying = v.flightOn && (v.flightIdle || v.flightBoost) && !mc.player.onGround();
		} catch (Throwable ignored) {}

		float step = FADE_TICKS <= 0f ? 1f : 1f / FADE_TICKS;
		fade = flying ? Math.min(1f, fade + step) : Math.max(0f, fade - step);
	}

	@SubscribeEvent
	public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
		if (!ENABLED || CAMERA_ROLL == 0f) return;

		Player p = Minecraft.getInstance().player;
		if (p == null) return;
		if (OrdealAnimPlayback.overriddenByPlugin(p)) return; // an ability owns the body

		float f = Mth.lerp(partial(), prevFade, fade);
		if (f <= 0.001f) return;

		float roll = Mth.clamp(OrdealFlightLean.roll(p), -MAX_ROLL, MAX_ROLL);
		event.setRoll(event.getRoll() - roll * CAMERA_ROLL * f);
	}

	private static float partial() {
		try {
			return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		} catch (Throwable t) {
			return 1f;
		}
	}
}