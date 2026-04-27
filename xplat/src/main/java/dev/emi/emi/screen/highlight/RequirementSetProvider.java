package dev.emi.emi.screen.highlight;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.registry.EmiExternalInventoryProviders;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;

public final class RequirementSetProvider {
	private static int treeRequirementsRevision = Integer.MIN_VALUE;
	private static Set<EmiStack> cachedTreeRequirements = Set.of();

	private RequirementSetProvider() {
	}

	public static Set<EmiStack> buildSyntheticRequirements() {
		Set<EmiStack> result = Sets.newHashSet();
		for (EmiFavorite.Synthetic fav : EmiFavorites.syntheticFavorites) {
			result.addAll(fav.getEmiStacks());
		}
		return result;
	}

	public static Set<EmiStack> buildTreeRequirements() {
		Set<EmiStack> result = Sets.newHashSet();
		for (var cost : BoM.combinedCost.costs.values()) {
			result.addAll(cost.ingredient.getEmiStacks());
		}
		for (var cost : BoM.combinedCost.chanceCosts.values()) {
			result.addAll(cost.ingredient.getEmiStacks());
		}
		for (EmiIngredient ing : BoM.combinedCost.neededIntermediates) {
			result.addAll(ing.getEmiStacks());
		}
		return result;
	}

	public static Set<EmiStack> getTreeRequirementsCached() {
		int revision = computeTreeRequirementsRevision();
		if (revision == treeRequirementsRevision) {
			return cachedTreeRequirements;
		}
		Set<EmiStack> requirements = buildTreeRequirements();
		cachedTreeRequirements = requirements;
		treeRequirementsRevision = revision;
		return requirements;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Set<Slot> buildIgnoredSlots(HandledScreen<?> screen) {
		if (screen == null) {
			return Set.of();
		}
		Set<Slot> ignored = Sets.newHashSet();
		try {
			for (EmiRecipeHandler handler : EmiRecipeFiller.getAllHandlers(screen)) {
				if (handler instanceof StandardRecipeHandler standard) {
					for (Slot slot : (List<Slot>) standard.getInputSources(screen.getScreenHandler())) {
						if (!EmiExternalInventoryProviders.handlesSlot(screen, slot)) {
							ignored.add(slot);
						}
					}
					for (Slot slot : (List<Slot>) standard.getCraftingSlots(screen.getScreenHandler())) {
						if (!EmiExternalInventoryProviders.handlesSlot(screen, slot)) {
							ignored.add(slot);
						}
					}
				}
			}
		} catch (Throwable t) {
			EmiLog.error("Recipe handler is throwing in renderSlotOverlays:", t);
		}
		return ignored;
	}

	private static int computeTreeRequirementsRevision() {
		int hash = 1;
		hash = 31 * hash + BoM.treeIndex;
		hash = 31 * hash + BoM.getTrees().size();
		hash = 31 * hash + BoM.combinedCost.costs.size();
		hash = 31 * hash + BoM.combinedCost.chanceCosts.size();
		hash = 31 * hash + BoM.combinedCost.neededIntermediates.size();
		for (MaterialTree tree : BoM.getTrees()) {
			if (tree == null || tree.goal == null) {
				continue;
			}
			hash = 31 * hash + System.identityHashCode(tree.goal);
			hash = 31 * hash + (int) tree.batches;
		}
		return hash;
	}
}
