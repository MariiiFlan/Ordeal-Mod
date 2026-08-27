package net.mcreator.ordeal.client;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.Minecraft;

public class ClientHelper {
	public static void openInventory() {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(new InventoryScreen(mc.player));
	}
}