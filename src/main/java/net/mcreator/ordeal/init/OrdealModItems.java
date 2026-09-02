/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.ordeal.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

import net.minecraft.world.item.Item;

import net.mcreator.ordeal.item.IliosClaymoreItem;
import net.mcreator.ordeal.item.FruitOfPureChiItem;
import net.mcreator.ordeal.item.AkonitoItem;
import net.mcreator.ordeal.OrdealMod;

public class OrdealModItems {
	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(OrdealMod.MODID);
	public static final DeferredItem<Item> AKONTIO;
	public static final DeferredItem<Item> ILIOS_CLAYMORE;
	public static final DeferredItem<Item> FRUIT_OF_PURE_CHI;
	static {
		AKONTIO = REGISTRY.register("akontio", AkonitoItem::new);
		ILIOS_CLAYMORE = REGISTRY.register("ilios_claymore", IliosClaymoreItem::new);
		FRUIT_OF_PURE_CHI = REGISTRY.register("fruit_of_pure_chi", FruitOfPureChiItem::new);
	}
	// Start of user code block custom items
	// End of user code block custom items
}