package dev.emi.emi.platform.forge.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;

import dev.emi.emi.api.recipe.EmiExternalInventoryProvider;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.Slot;
import net.minecraftforge.fml.ModList;

/**
 * Reflection-based integration that surfaces the contents of an open AE2 ME
 * terminal as part of the EMI player inventory and storage highlighting.
 */
public class Ae2InventoryProvider implements EmiExternalInventoryProvider {

	private static final String SCREEN_CLASS = "appeng.client.gui.me.common.MEStorageScreen";
	private static final String REPO_CLASS = "appeng.client.gui.me.common.Repo";
	private static final String ENTRY_CLASS = "appeng.menu.me.common.GridInventoryEntry";
	private static final String REPO_SLOT_CLASS = "appeng.client.gui.me.common.RepoSlot";
	private static final String ITEM_KEY_CLASS = "appeng.api.stacks.AEItemKey";
	private static final String FLUID_KEY_CLASS = "appeng.api.stacks.AEFluidKey";
	private static final String GENERIC_STACK_CLASS = "appeng.api.stacks.GenericStack";

	private static boolean initialized = false;
	private static boolean available = false;
	private static boolean loggedFirstRead = false;

	private static Class<?> screenClass;
	private static Field repoField;
	private static Method getAllEntries;
	private static Method getWhat;
	private static Method getStoredAmount;
	private static Class<?> itemKeyClass;
	private static Method itemKeyGetItem;
	private static Method itemKeyGetTag;
	private static Class<?> fluidKeyClass;
	private static Method fluidKeyGetFluid;
	private static Method fluidKeyGetTag;

	private static boolean repoSlotInitialized = false;
	private static boolean repoSlotAvailable = false;
	private static Class<?> repoSlotClass;
	private static Method repoSlotGetEntry;

	private static boolean genericWrapInitialized = false;
	private static boolean genericWrapAvailable = false;
	private static Method unwrapItemStack;
	private static Method genericWhat;
	private static Method genericAmount;

	public static void tryRegister(java.util.function.Consumer<EmiExternalInventoryProvider> registrar) {
		boolean ae2Loaded = ModList.get().isLoaded("ae2") || ModList.get().isLoaded("appliedenergistics2");
		if (!ae2Loaded) {
			EmiLog.info("AE2 mod not detected, skipping ME terminal integration.");
			return;
		}
		EmiLog.info("AE2 mod detected, attempting reflection-based ME terminal integration...");
		if (!init()) {
			return;
		}
		registrar.accept(new Ae2InventoryProvider());
		EmiLog.info("AE2 ME inventory provider registered.");
	}

	private static synchronized boolean init() {
		if (initialized) {
			return available;
		}
		initialized = true;
		try {
			screenClass = Class.forName(SCREEN_CLASS);
			repoField = findField(screenClass, "repo");
			repoField.setAccessible(true);

			Class<?> repoClass = Class.forName(REPO_CLASS);
			getAllEntries = repoClass.getMethod("getAllEntries");

			Class<?> entryClass = Class.forName(ENTRY_CLASS);
			getWhat = entryClass.getMethod("getWhat");
			getStoredAmount = entryClass.getMethod("getStoredAmount");

			itemKeyClass = Class.forName(ITEM_KEY_CLASS);
			itemKeyGetItem = itemKeyClass.getMethod("getItem");
			itemKeyGetTag = itemKeyClass.getMethod("getTag");

			fluidKeyClass = Class.forName(FLUID_KEY_CLASS);
			fluidKeyGetFluid = fluidKeyClass.getMethod("getFluid");
			fluidKeyGetTag = fluidKeyClass.getMethod("getTag");

			available = true;
			EmiLog.info("AE2 ME integration initialized successfully; terminal contents will feed the EMI recipe tree.");
		} catch (Throwable t) {
			available = false;
			EmiLog.warn("AE2 inventory integration disabled: " + t.getClass().getSimpleName() + " — " + t.getMessage());
			EmiLog.error("AE2 integration init failure details:", t);
		}
		return available;
	}

	private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
		Class<?> c = clazz;
		while (c != null) {
			try {
				return c.getDeclaredField(name);
			} catch (NoSuchFieldException ignored) {
				c = c.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name + " on " + clazz.getName() + " or its superclasses");
	}

	@Override
	public List<EmiStack> getStacks(HandledScreen<?> screen, PlayerEntity player) {
		if (!available || !isAe2Screen(screen)) {
			return List.of();
		}
		List<EmiStack> result = Lists.newArrayList();
		int items = 0;
		int fluids = 0;
		try {
			Object repo = repoField.get(screen);
			if (repo == null) {
				return List.of();
			}
			Object entries = getAllEntries.invoke(repo);
			if (!(entries instanceof Collection<?> coll)) {
				return List.of();
			}
			for (Object entry : coll) {
				Object key = getWhat.invoke(entry);
				long amount = (long) getStoredAmount.invoke(entry);
				EmiStack decoded = keyToEmiStack(key, amount);
				if (decoded == null || decoded.isEmpty()) {
					continue;
				}
				result.add(decoded);
				if (decoded.getKeyOfType(Item.class) != null) {
					appendContainedFluids(decoded, result);
					items++;
				} else {
					fluids++;
				}
			}
			if (!loggedFirstRead && (items > 0 || fluids > 0)) {
				loggedFirstRead = true;
				EmiLog.info("AE2 ME read: " + items + " item stacks, " + fluids + " fluid stacks from terminal.");
			}
		} catch (Throwable t) {
			EmiLog.warn("Failed to read AE2 ME terminal contents: " + t.getMessage());
			EmiLog.error("AE2 read failure details:", t);
		}
		return result;
	}

	@Override
	public boolean handlesSlot(HandledScreen<?> screen, Slot slot) {
		return available && isAe2Screen(screen) && slot != null && initRepoSlot() && repoSlotClass.isInstance(slot);
	}

	@Override
	public List<EmiStack> getSlotCandidates(HandledScreen<?> screen, Slot slot, ItemStack displayedStack) {
		if (!available || !isAe2Screen(screen)) {
			return List.of();
		}
		List<EmiStack> out = Lists.newArrayList();
		try {
			if (slot != null && initRepoSlot() && repoSlotClass.isInstance(slot)) {
				Object entry = repoSlotGetEntry.invoke(slot);
				if (entry != null) {
					EmiStack decoded = keyToEmiStack(getWhat.invoke(entry), (long) getStoredAmount.invoke(entry));
					if (decoded != null && !decoded.isEmpty()) {
						out.add(decoded);
						appendContainedFluids(decoded, out);
					}
				}
			}
			if (displayedStack != null && !displayedStack.isEmpty() && initGenericWrap()) {
				Object generic = unwrapItemStack.invoke(null, displayedStack);
				if (generic != null) {
					EmiStack wrapped = keyToEmiStack(genericWhat.invoke(generic), (long) genericAmount.invoke(generic));
					if (wrapped != null && !wrapped.isEmpty()) {
						out.add(wrapped);
						appendContainedFluids(wrapped, out);
					}
				}
			}
		} catch (Throwable t) {
			EmiLog.warn("Failed to resolve AE2 slot candidate: " + t.getMessage());
		}
		return out;
	}

	private static boolean isAe2Screen(HandledScreen<?> screen) {
		return screen != null && screenClass.isInstance(screen);
	}

	private static synchronized boolean initRepoSlot() {
		if (repoSlotInitialized) {
			return repoSlotAvailable;
		}
		repoSlotInitialized = true;
		try {
			repoSlotClass = Class.forName(REPO_SLOT_CLASS);
			repoSlotGetEntry = repoSlotClass.getMethod("getEntry");
			repoSlotAvailable = true;
		} catch (Throwable t) {
			repoSlotAvailable = false;
		}
		return repoSlotAvailable;
	}

	private static synchronized boolean initGenericWrap() {
		if (genericWrapInitialized) {
			return genericWrapAvailable;
		}
		genericWrapInitialized = true;
		try {
			Class<?> genericStackClass = Class.forName(GENERIC_STACK_CLASS);
			unwrapItemStack = genericStackClass.getMethod("unwrapItemStack", ItemStack.class);
			genericWhat = genericStackClass.getMethod("what");
			genericAmount = genericStackClass.getMethod("amount");
			genericWrapAvailable = true;
		} catch (Throwable t) {
			genericWrapAvailable = false;
		}
		return genericWrapAvailable;
	}

	private static void appendContainedFluids(EmiStack stack, List<EmiStack> out) {
		Item item = stack.getKeyOfType(Item.class);
		if (item == null) {
			return;
		}
		ItemStack is = new ItemStack(item, 1);
		NbtCompound tag = stack.getNbt();
		if (tag != null) {
			is.setNbt(tag.copy());
		}
		long multiplier = Math.max(1L, stack.getAmount());
		for (EmiStack fluid : EmiAgnos.getFluidContents(is)) {
			if (!fluid.isEmpty() && fluid.getAmount() > 0) {
				out.add(fluid.copy().setAmount(fluid.getAmount() * multiplier));
			}
		}
	}

	private static EmiStack keyToEmiStack(Object key, long amount) throws Exception {
		if (key == null || amount <= 0 || itemKeyClass == null || fluidKeyClass == null) {
			return null;
		}
		if (itemKeyClass.isInstance(key)) {
			Item item = (Item) itemKeyGetItem.invoke(key);
			NbtCompound tag = normalizeTag((NbtCompound) itemKeyGetTag.invoke(key));
			if (item == null) {
				return null;
			}
			ItemStack is = new ItemStack(item, 1);
			if (tag != null) {
				is.setNbt(tag.copy());
			}
			return EmiStack.of(is, amount);
		}
		if (fluidKeyClass.isInstance(key)) {
			Fluid fluid = (Fluid) fluidKeyGetFluid.invoke(key);
			NbtCompound tag = normalizeTag((NbtCompound) fluidKeyGetTag.invoke(key));
			if (fluid == null) {
				return null;
			}
			return EmiStack.of(fluid, tag, amount);
		}
		return null;
	}

	private static NbtCompound normalizeTag(NbtCompound tag) {
		if (tag == null || tag.isEmpty()) {
			return null;
		}
		return tag;
	}
}
