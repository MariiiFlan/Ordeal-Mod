package net.mcreator.ordeal;

import net.mcreator.ordeal.core.OrdealFx;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Chi dash. Double-tap W/A/S/D and you burst that way, trailing the
 * misc_chidash photon effect in your chi colour.
 *
 * Three charges. Spend one and it's back in 2.5 seconds; run completely dry
 * and the whole set only comes back after 8 seconds — so chaining all three
 * is a commitment, not a rhythm.
 */
public final class OrdealDash {

	private OrdealDash() {}

	// ---- TUNING (mirrored into config/ordeal-tuning.json) -------------------
	public static int MAX_CHARGES        = OrdealTuning.i("dash.charges", 4);
	public static int REFILL_TICKS       = OrdealTuning.i("dash.refill_ticks", 50);        // 2.5s per charge
	public static int EMPTY_REFILL_TICKS = OrdealTuning.i("dash.empty_refill_ticks", 160); // 8s when fully spent
	public static double POWER           = OrdealTuning.d("dash.power", 2.0);
	/** Taps needed to fire a dash, and the window they have to land in. */
	public static int TAPS               = OrdealTuning.i("dash.taps", 3);
	/** Ticks allowed BETWEEN consecutive taps, not across the whole burst. */
	public static int DOUBLE_TAP_TICKS   = OrdealTuning.i("dash.double_tap_window", 7);
	/** How much of your look pitch a forward dash follows. 1 = straight up. */
	public static double VERTICAL        = OrdealTuning.d("dash.vertical", 1.0);
	/** Lift given to a flat dash, so it clears a block instead of scraping it. */
	public static double HOP             = OrdealTuning.d("dash.hop", 0.2);
	/** Photon effect played on every dash (make it in the ordeal fx project). */
	public static final String FX = "ordeal:misc_chidash";

	// ---- server -------------------------------------------------------------

	public static void execute(ServerPlayer p, String dir) {
		Vec3 look = p.getLookAngle();
		Vec3 fwd = new Vec3(look.x, 0, look.z).normalize();
		if (fwd.lengthSqr() < 1.0e-4) fwd = new Vec3(0, 0, 1);
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);

		// forward follows where you are actually looking, so looking up dashes
		// up and looking down drives you into the floor. The other three stay
		// flat - strafing skyward because you happened to glance up is nobody's
		// idea of a dash
		double rise;
		Vec3 d;
		if (dir.equals("forward")) {
			d = look.lengthSqr() < 1.0e-4 ? fwd : look.normalize();
			rise = d.y * POWER * VERTICAL + HOP * (1 - Math.abs(d.y));
			d = new Vec3(d.x, 0, d.z);
		} else {
			d = switch (dir) {
				case "back" -> fwd.scale(-1);
				case "left" -> right.scale(-1);
				case "right" -> right;
				default -> fwd;
			};
			rise = HOP;
		}
		p.setDeltaMovement(d.x * POWER, rise, d.z * POWER);
		p.hurtMarked = true;
		OrdealFx.spawnAccent(p, FX);
		p.level().playSound(null, p.blockPosition(),
				SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.7f, 1.5f);
	}

	// ---- client: double-tap detection + charge bookkeeping ------------------

	@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
	@OnlyIn(Dist.CLIENT)
	public static final class Client {

		public static int charges = MAX_CHARGES;
		private static int rechargeTicks = 0;
		private static final boolean[] wasDown = new boolean[4];
		private static final long[] lastTap = new long[4];
		/** Taps banked on each key so far, cleared when the window lapses. */
		private static final int[] taps = new int[4];
		private static long ticks = 0;

		private static final String[] DIRS = { "forward", "back", "left", "right" };

		@SubscribeEvent
		public static void onTick(ClientTickEvent.Post event) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) { charges = MAX_CHARGES; rechargeTicks = 0; return; }
			ticks++;

			// recharge: one every 2.5s, or an 8s wait for the whole set when empty
			if (charges < MAX_CHARGES) {
				rechargeTicks++;
				if (charges == 0) {
					if (rechargeTicks >= EMPTY_REFILL_TICKS) { charges = MAX_CHARGES; rechargeTicks = 0; }
				} else if (rechargeTicks >= REFILL_TICKS) {
					charges++; rechargeTicks = 0;
				}
			} else rechargeTicks = 0;

			// a screen eats the keys, so forget any half-finished attempt rather
			// than letting it complete on the far side of a menu
			if (mc.screen != null) {
				java.util.Arrays.fill(taps, 0);
				java.util.Arrays.fill(wasDown, false);
				return;
			}
			boolean[] down = {
					mc.options.keyUp.isDown(), mc.options.keyDown.isDown(),
					mc.options.keyLeft.isDown(), mc.options.keyRight.isDown() };
			int need = Math.max(2, TAPS);
			for (int i = 0; i < 4; i++) {
				if (down[i] && !wasDown[i]) {
					// a tap that lands too late is not a failure - it becomes
					// the first tap of the next attempt, so a mistimed press
					// never eats an input
					taps[i] = (ticks - lastTap[i] <= DOUBLE_TAP_TICKS) ? taps[i] + 1 : 1;
					lastTap[i] = ticks;

					if (taps[i] >= need) {
						taps[i] = 0;
						lastTap[i] = -100;
						if (charges > 0) {
							charges--;
							rechargeTicks = 0;
							PacketDistributor.sendToServer(
									new net.mcreator.ordeal.core.OrdealActionMessage("dash", DIRS[i], 0));
						}
					}
				}
				// the window lapsing clears the bank, so taps from one attempt
				// can never join up with the next into a dash you did not ask for
				if (taps[i] > 0 && ticks - lastTap[i] > DOUBLE_TAP_TICKS) taps[i] = 0;
				wasDown[i] = down[i];
			}
		}
	}
}