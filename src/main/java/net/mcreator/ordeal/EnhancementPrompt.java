package net.mcreator.ordeal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public final class EnhancementPrompt {

	public static int SNOOZE_TICKS = OrdealTuning.i("enhancement.snooze_ticks", 600);
	public static int CHECK_EVERY  = OrdealTuning.i("enhancement.check_every_ticks", 20);

	private static long quietUntil = 0;
	private static int ticks = 0;

	private EnhancementPrompt() {}

	public static void snooze() {
		quietUntil = ticks + Math.max(20, SNOOZE_TICKS);
	}

	public static void openNow() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) return;
		int slot = Enhancements.slotNeedingPick(p);
		if (slot == 0) return;
		EnhancementScreen.open(Enhancements.talentAt(p, slot), Enhancements.strengthAt(p, slot));
	}

	private static boolean isTalentMenu(net.minecraft.client.gui.screens.Screen s) {
		if (s == null) return false;
		String n = s.getClass().getName().toLowerCase(java.util.Locale.ROOT);
		return n.contains("terminal") || n.contains("kodefield");
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		ticks++;
		if (!Enhancements.PROMPT_ON_REACH) return;
		if (ticks % Math.max(5, CHECK_EVERY) != 0) return;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) return;

		int slot = Enhancements.slotNeedingPick(p);
		if (slot == 0) return;

		if (isTalentMenu(mc.screen)) {
			EnhancementScreen.open(Enhancements.talentAt(p, slot), Enhancements.strengthAt(p, slot));
			return;
		}
		if (mc.screen != null || ticks < quietUntil) return;
		EnhancementScreen.open(Enhancements.talentAt(p, slot), Enhancements.strengthAt(p, slot));
	}
}