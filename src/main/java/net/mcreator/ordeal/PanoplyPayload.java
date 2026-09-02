package net.mcreator.ordeal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The panoply lives in persistent data, which never reaches a client on its
 * own - so the sixteen points, the shown mask, the drawn index and the packed
 * equip bar come across here. Without this the screen and the HUD would both
 * draw an empty rack.
 *
 * Sent on every change and on join, death and dimension change.
 */
@EventBusSubscriber
public record PanoplyPayload(List<ItemStack> items, int active, int drawn, int bar)
		implements CustomPacketPayload {

	// NOTE the interface above is net.mcreator.ordeal.CustomPacketPayload - the
	// bridge in this package, NOT the vanilla one. It is deliberately NOT
	// imported: OrdealMod.addNetworkMessage is declared <T extends
	// CustomPacketPayload> and, being in this package, that means the bridge.
	// Import the vanilla interface here and the generic bound stops matching,
	// which is the "inference variable T has incompatible bounds" error.

	public static final Type<PanoplyPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath("ordeal", "panoply"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PanoplyPayload> STREAM_CODEC =
			StreamCodec.composite(
					// built from the single-stack codec rather than the list constant,
					// so it does not depend on which list helper a given mapping
					// exposes - OPTIONAL_STREAM_CODEC is always there
					ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), PanoplyPayload::items,
					ByteBufCodecs.VAR_INT, PanoplyPayload::active,
					ByteBufCodecs.VAR_INT, PanoplyPayload::drawn,
					// six slots, five bits each - see Panoply.packBar
					ByteBufCodecs.VAR_INT, PanoplyPayload::bar,
					PanoplyPayload::new);

	@Override
	public Type<PanoplyPayload> type() { return TYPE; }

	/**
	 * Registered the way every other Ordeal payload is - through OrdealMod's own
	 * collector on FMLCommonSetupEvent. The RegisterPayloadHandlersEvent route
	 * works too but needs the deprecated Bus.MOD annotation, and this keeps the
	 * mod's networking in one place.
	 */
	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		OrdealMod.addNetworkMessage(TYPE, STREAM_CODEC, PanoplyPayload::handle);
	}

	private static void handle(PanoplyPayload p, IPayloadContext ctx) {
		ctx.enqueueWork(() -> { if (FMLEnvironment.dist.isClient()) Client.accept(p); });
	}

	public static void sync(ServerPlayer p) {
		if (p == null) return;
		ItemStack[] a = Panoply.all(p);
		List<ItemStack> list = new ArrayList<>(Panoply.POINTS);
		for (ItemStack s : a) list.add(s == null ? ItemStack.EMPTY : s);
		PacketDistributor.sendToPlayer(p,
				new PanoplyPayload(list, Panoply.mask(p), Panoply.drawnPoint(p),
						Panoply.rawBarValue(p)));
	}

	// ==================== CLIENT MIRROR ====================

	/** What the screen and the HUD read. Empty until the first packet lands. */
	@OnlyIn(Dist.CLIENT)
	public static final class Client {
		private Client() {}

		public static ItemStack[] ITEMS = blank();
		public static int ACTIVE = 0;
		public static int DRAWN  = -1;
		/**
		 * The stored word: slots plus Panoply.BAR_SET. Resolve it through BAR,
		 * not this - bit 30 is not a slot.
		 */
		public static int RAW_BAR = 0;
		/** The six bar slots as they should draw, holes already removed. */
		public static int[] BAR = emptyBar();

		private static int[] emptyBar() {
			int[] b = new int[Panoply.BAR_SLOTS];
			for (int i = 0; i < b.length; i++) b[i] = -1;   // 0 is a real point
			return b;
		}

		private static ItemStack[] blank() {
			ItemStack[] a = new ItemStack[Panoply.POINTS];
			for (int i = 0; i < a.length; i++) a[i] = ItemStack.EMPTY;
			return a;
		}

		static void accept(PanoplyPayload p) {
			ItemStack[] a = blank();
			for (int i = 0; i < a.length && i < p.items().size(); i++) {
				ItemStack s = p.items().get(i);
				a[i] = s == null ? ItemStack.EMPTY : s;
			}
			ITEMS = a;
			ACTIVE = p.active();
			DRAWN = p.drawn();
			RAW_BAR = p.bar();
			BAR = Panoply.resolveBar(RAW_BAR, ITEMS);
		}

		/** Flat point in bar slot i, or -1. */
		public static int barAt(int i) {
			return (i < 0 || i >= BAR.length) ? -1 : BAR[i];
		}

		/** Which bar slot holds this point, or -1. */
		public static int barSlotOf(int pt) {
			for (int i = 0; i < BAR.length; i++) if (BAR[i] == pt) return i;
			return -1;
		}

		public static ItemStack at(int pt) {
			return (pt < 0 || pt >= ITEMS.length) ? ItemStack.EMPTY : ITEMS[pt];
		}

		public static boolean isActive(int pt) {
			return pt >= 0 && pt < ITEMS.length && !ITEMS[pt].isEmpty()
					&& (ACTIVE & (1 << pt)) != 0;
		}

		public static int carried() {
			int n = 0;
			for (ItemStack s : ITEMS) if (!s.isEmpty()) n++;
			return n;
		}

		public static int activeCount() {
			int n = 0;
			for (int i = 0; i < ITEMS.length; i++) if (isActive(i)) n++;
			return n;
		}
	}
}