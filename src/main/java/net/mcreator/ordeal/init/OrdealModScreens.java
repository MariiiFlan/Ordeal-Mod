/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.ordeal.init;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.mcreator.ordeal.client.gui.KodeFieldTerminalGUIScreen;

@EventBusSubscriber(Dist.CLIENT)
public class OrdealModScreens {
	@SubscribeEvent
	public static void clientLoad(RegisterMenuScreensEvent event) {
		event.register(OrdealModMenus.KODE_FIELD_TERMINAL_GUI.get(), KodeFieldTerminalGUIScreen::new);
	}

	public interface ScreenAccessor {
		void updateMenuState(int elementType, String name, Object elementState);
	}
}