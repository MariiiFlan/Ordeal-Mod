package net.mcreator.ordeal;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Client-side opener, kept apart so the payload class stays server-safe. */
@OnlyIn(Dist.CLIENT)
public final class IliosStatePrompt {

	private IliosStatePrompt() {}

	public static void openNow() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.screen instanceof IliosStateScreen) return;
			mc.setScreen(new IliosStateScreen());
		});
	}
}