package net.mcreator.ordeal.core.client;

import net.mcreator.ordeal.core.OrdealInputPayload;
import net.mcreator.ordeal.init.OrdealModKeyMappings;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends key state only when it changes. */
@EventBusSubscriber(modid = "ordeal", value = Dist.CLIENT)
public class OrdealInputSync {

	private static OrdealInputPayload lastSent = null;

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		OrdealInputPayload current = new OrdealInputPayload(
				mc.options.keyAttack.isDown(),
				OrdealModKeyMappings.ABILITY_1.isDown(),
				OrdealModKeyMappings.ABILITY_2.isDown(),
				OrdealModKeyMappings.ABILITY_3.isDown(),
				OrdealModKeyMappings.ABILITY_4.isDown(),
				OrdealModKeyMappings.ABILITY_5.isDown(),
				OrdealModKeyMappings.COMBAT_MODE.isDown(),
				mc.options.keyUp.isDown(),
				mc.options.keyDown.isDown(),
				mc.options.keyLeft.isDown(),
				mc.options.keyRight.isDown(),
				mc.options.keyJump.isDown(),
				mc.options.keyShift.isDown(),
				mc.options.keySprint.isDown());

		if (!current.equals(lastSent)) {
			PacketDistributor.sendToServer(current);
			lastSent = current;
		}
	}
}