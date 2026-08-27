/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.ordeal.init;

import org.lwjgl.glfw.GLFW;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

import net.mcreator.ordeal.network.*;

@EventBusSubscriber(Dist.CLIENT)
public class OrdealModKeyMappings {
	public static final KeyMapping KODE_FIELD_TERMINAL = new KeyMapping("key.ordeal.kode_field_terminal", GLFW.GLFW_KEY_0, "key.categories.ordeal") {
		private boolean isDownOld = false;

		@Override
		public void setDown(boolean isDown) {
			super.setDown(isDown);
			if (isDownOld != isDown && isDown) {
				PacketDistributor.sendToServer(new KodeFieldTerminalMessage(0, 0));
				KodeFieldTerminalMessage.pressAction(Minecraft.getInstance().player, 0, 0);
			}
			isDownOld = isDown;
		}
	};
	public static final KeyMapping COMBAT_MODE = new KeyMapping("key.ordeal.combat_mode", GLFW.GLFW_KEY_G, "key.categories.ordeal") {
		private boolean isDownOld = false;

		@Override
		public void setDown(boolean isDown) {
			super.setDown(isDown);
			if (isDownOld != isDown && isDown) {
				PacketDistributor.sendToServer(new CombatModeMessage(0, 0));
				CombatModeMessage.pressAction(Minecraft.getInstance().player, 0, 0);
			}
			isDownOld = isDown;
		}
	};
	public static final KeyMapping ABILITY_1 = new KeyMapping("key.ordeal.ability_1", GLFW.GLFW_KEY_Z, "key.categories.ordeal") {
		private boolean isDownOld = false;

		@Override
		public void setDown(boolean isDown) {
			super.setDown(isDown);
			if (isDownOld != isDown && isDown) {
				PacketDistributor.sendToServer(new Ability1Message(0, 0));
				Ability1Message.pressAction(Minecraft.getInstance().player, 0, 0);
			}
			isDownOld = isDown;
		}
	};
	public static final KeyMapping ABILITY_2 = new KeyMapping("key.ordeal.ability_2", GLFW.GLFW_KEY_X, "key.categories.ordeal") {
		private boolean isDownOld = false;

		@Override
		public void setDown(boolean isDown) {
			super.setDown(isDown);
			if (isDownOld != isDown && isDown) {
				PacketDistributor.sendToServer(new Ability2Message(0, 0));
				Ability2Message.pressAction(Minecraft.getInstance().player, 0, 0);
			}
			isDownOld = isDown;
		}
	};
	public static final KeyMapping ABILITY_3 = new KeyMapping("key.ordeal.ability_3", GLFW.GLFW_KEY_C, "key.categories.ordeal") {
		private boolean isDownOld = false;

		@Override
		public void setDown(boolean isDown) {
			super.setDown(isDown);
			if (isDownOld != isDown && isDown) {
				PacketDistributor.sendToServer(new Ability3Message(0, 0));
				Ability3Message.pressAction(Minecraft.getInstance().player, 0, 0);
			}
			isDownOld = isDown;
		}
	};
	public static final KeyMapping ABILITY_4 = new KeyMapping("key.ordeal.ability_4", GLFW.GLFW_KEY_V, "key.categories.ordeal") {
		private boolean isDownOld = false;

		@Override
		public void setDown(boolean isDown) {
			super.setDown(isDown);
			if (isDownOld != isDown && isDown) {
				PacketDistributor.sendToServer(new Ability4Message(0, 0));
				Ability4Message.pressAction(Minecraft.getInstance().player, 0, 0);
			}
			isDownOld = isDown;
		}
	};
	public static final KeyMapping ABILITY_5 = new KeyMapping("key.ordeal.ability_5", GLFW.GLFW_KEY_B, "key.categories.ordeal") {
		private boolean isDownOld = false;

		@Override
		public void setDown(boolean isDown) {
			super.setDown(isDown);
			if (isDownOld != isDown && isDown) {
				PacketDistributor.sendToServer(new Ability5Message(0, 0));
				Ability5Message.pressAction(Minecraft.getInstance().player, 0, 0);
			}
			isDownOld = isDown;
		}
	};
	public static final KeyMapping ABILITY_ROW_SWAP_KEY = new KeyMapping("key.ordeal.ability_row_swap_key", GLFW.GLFW_KEY_Z, "key.categories.ordeal") {
		private boolean isDownOld = false;

		@Override
		public void setDown(boolean isDown) {
			super.setDown(isDown);
			if (isDownOld != isDown && isDown) {
				PacketDistributor.sendToServer(new AbilityRowSwapKeyMessage(0, 0));
				AbilityRowSwapKeyMessage.pressAction(Minecraft.getInstance().player, 0, 0);
			}
			isDownOld = isDown;
		}
	};

	@SubscribeEvent
	public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(KODE_FIELD_TERMINAL);
		event.register(COMBAT_MODE);
		event.register(ABILITY_1);
		event.register(ABILITY_2);
		event.register(ABILITY_3);
		event.register(ABILITY_4);
		event.register(ABILITY_5);
		event.register(ABILITY_ROW_SWAP_KEY);
	}

	@EventBusSubscriber(Dist.CLIENT)
	public static class KeyEventListener {
		@SubscribeEvent
		public static void onClientTick(ClientTickEvent.Post event) {
			if (Minecraft.getInstance().screen == null) {
				KODE_FIELD_TERMINAL.consumeClick();
				COMBAT_MODE.consumeClick();
				ABILITY_1.consumeClick();
				ABILITY_2.consumeClick();
				ABILITY_3.consumeClick();
				ABILITY_4.consumeClick();
				ABILITY_5.consumeClick();
				ABILITY_ROW_SWAP_KEY.consumeClick();
			}
		}
	}
}