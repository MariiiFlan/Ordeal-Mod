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
				ABILITY_1_LASTPRESS = System.currentTimeMillis();
			} else if (isDownOld != isDown && !isDown) {
				int dt = (int) (System.currentTimeMillis() - ABILITY_1_LASTPRESS);
				PacketDistributor.sendToServer(new Ability1Message(1, dt));
				Ability1Message.pressAction(Minecraft.getInstance().player, 1, dt);
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
				ABILITY_2_LASTPRESS = System.currentTimeMillis();
			} else if (isDownOld != isDown && !isDown) {
				int dt = (int) (System.currentTimeMillis() - ABILITY_2_LASTPRESS);
				PacketDistributor.sendToServer(new Ability2Message(1, dt));
				Ability2Message.pressAction(Minecraft.getInstance().player, 1, dt);
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
				ABILITY_3_LASTPRESS = System.currentTimeMillis();
			} else if (isDownOld != isDown && !isDown) {
				int dt = (int) (System.currentTimeMillis() - ABILITY_3_LASTPRESS);
				PacketDistributor.sendToServer(new Ability3Message(1, dt));
				Ability3Message.pressAction(Minecraft.getInstance().player, 1, dt);
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
				ABILITY_4_LASTPRESS = System.currentTimeMillis();
			} else if (isDownOld != isDown && !isDown) {
				int dt = (int) (System.currentTimeMillis() - ABILITY_4_LASTPRESS);
				PacketDistributor.sendToServer(new Ability4Message(1, dt));
				Ability4Message.pressAction(Minecraft.getInstance().player, 1, dt);
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
				ABILITY_5_LASTPRESS = System.currentTimeMillis();
			} else if (isDownOld != isDown && !isDown) {
				int dt = (int) (System.currentTimeMillis() - ABILITY_5_LASTPRESS);
				PacketDistributor.sendToServer(new Ability5Message(1, dt));
				Ability5Message.pressAction(Minecraft.getInstance().player, 1, dt);
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
	public static final KeyMapping PANOPLY_EQUIP = new KeyMapping("key.ordeal.panoply_equip", GLFW.GLFW_KEY_N, "key.categories.ordeal");
	private static long ABILITY_1_LASTPRESS = 0;
	private static long ABILITY_2_LASTPRESS = 0;
	private static long ABILITY_3_LASTPRESS = 0;
	private static long ABILITY_4_LASTPRESS = 0;
	private static long ABILITY_5_LASTPRESS = 0;

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
		event.register(PANOPLY_EQUIP);
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