package dev.emi.emi.screen.highlight;

import java.util.List;

import com.google.common.collect.Lists;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.mixin.accessor.HandledScreenAccessor;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.registry.EmiExternalInventoryProviders;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.search.EmiSearch.CompiledQuery;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

public final class StorageHighlightService {
	private static final StorageHighlightPolicy POLICY = new DefaultStorageHighlightPolicy();

	private StorageHighlightService() {
	}

	public static void render(EmiDrawContext context, HandledScreen<?> screen, HandledScreenAccessor accessor,
			CompiledQuery query) {
		HighlightContext highlightContext = HighlightContext.create();
		context.push();
		context.matrices().translate(accessor.getX(), accessor.getY(), 0);
		for (Slot slot : screen.getScreenHandler().slots) {
			if (!slot.isEnabled()) {
				continue;
			}
			ItemStack itemStack = slot.getStack();
			EmiStack stack = EmiStack.of(itemStack);
			context.push();
			context.matrices().translate(0, 0, 300);
			if (query != null) {
				if (!query.test(stack)) {
					context.fill(slot.x - 1, slot.y - 1, 18, 18, 0x77000000);
				}
			} else if (highlightContext.enabled) {
				boolean matched = matchesAny(resolve(slot, itemStack, screen), highlightContext);
				if (POLICY.shouldHighlight(slot, highlightContext, matched)) {
					context.fill(slot.x - 1, slot.y - 1, 18, 18, 0x7700BBFF);
				}
			}
			context.pop();
		}
		context.pop();
	}

	private static List<EmiStack> resolve(Slot slot, ItemStack itemStack, HandledScreen<?> screen) {
		List<EmiStack> candidates = Lists.newArrayList();
		addCandidate(candidates, EmiStack.of(itemStack));
		for (EmiStack fluid : EmiAgnos.getFluidContents(itemStack)) {
			addCandidate(candidates, fluid);
		}
		for (EmiStack external : EmiExternalInventoryProviders.collectSlotCandidates(screen, slot, itemStack)) {
			addCandidate(candidates, external);
		}
		return candidates;
	}

	private static void addCandidate(List<EmiStack> candidates, EmiStack stack) {
		if (stack != null && !stack.isEmpty()) {
			candidates.add(stack);
		}
	}

	private static boolean matchesAny(List<EmiStack> candidates, HighlightContext context) {
		for (EmiStack candidate : candidates) {
			if (context.syntheticRequirements.contains(candidate) || context.treeRequirements.contains(candidate)) {
				return true;
			}
		}
		return false;
	}

	private interface StorageHighlightPolicy {
		boolean shouldHighlight(Slot slot, HighlightContext context, boolean matched);
	}

	private static final class DefaultStorageHighlightPolicy implements StorageHighlightPolicy {
		@Override
		public boolean shouldHighlight(Slot slot, HighlightContext context, boolean matched) {
			if (!matched) {
				return false;
			}
			if (slot.inventory instanceof PlayerInventory) {
				return false;
			}
			return !context.ignoredSlots.contains(slot);
		}
	}
}
