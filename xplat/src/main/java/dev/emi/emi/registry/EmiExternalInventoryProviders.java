package dev.emi.emi.registry;

import java.util.List;

import com.google.common.collect.Lists;

import dev.emi.emi.api.recipe.EmiExternalInventoryProvider;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

public class EmiExternalInventoryProviders {
	public static final List<EmiExternalInventoryProvider> providers = Lists.newArrayList();

	public static void clear() {
		providers.clear();
	}

	public static List<EmiStack> collect(HandledScreen<?> screen, PlayerEntity player) {
		if (providers.isEmpty() || player == null) {
			return List.of();
		}
		List<EmiStack> out = Lists.newArrayList();
		for (EmiExternalInventoryProvider provider : providers) {
			try {
				List<EmiStack> stacks = provider.getStacks(screen, player);
				if (stacks != null && !stacks.isEmpty()) {
					out.addAll(stacks);
				}
			} catch (Throwable t) {
				EmiLog.warn("External inventory provider " + provider.getClass().getName() + " threw: " + t.getMessage());
			}
		}
		return out;
	}

	public static boolean handlesSlot(HandledScreen<?> screen, Slot slot) {
		if (providers.isEmpty() || slot == null) {
			return false;
		}
		for (EmiExternalInventoryProvider provider : providers) {
			try {
				if (provider.handlesSlot(screen, slot)) {
					return true;
				}
			} catch (Throwable t) {
				EmiLog.warn("External inventory provider " + provider.getClass().getName()
						+ " failed in handlesSlot: " + t.getMessage());
			}
		}
		return false;
	}

	public static List<EmiStack> collectSlotCandidates(HandledScreen<?> screen, Slot slot, ItemStack displayedStack) {
		if (providers.isEmpty() || slot == null) {
			return List.of();
		}
		List<EmiStack> out = Lists.newArrayList();
		for (EmiExternalInventoryProvider provider : providers) {
			try {
				List<EmiStack> stacks = provider.getSlotCandidates(screen, slot, displayedStack);
				if (stacks != null && !stacks.isEmpty()) {
					out.addAll(stacks);
				}
			} catch (Throwable t) {
				EmiLog.warn("External inventory provider " + provider.getClass().getName()
						+ " failed in getSlotCandidates: " + t.getMessage());
			}
		}
		return out;
	}
}
