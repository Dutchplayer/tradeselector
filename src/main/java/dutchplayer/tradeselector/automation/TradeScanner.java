package dutchplayer.tradeselector.automation;

import dutchplayer.tradeselector.config.ConfigManager;
import dutchplayer.tradeselector.config.ModConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class TradeScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeSelector");
    private static final ResourceLocation TRADEABLE_ENCHANTMENT_TAG_ID = ResourceLocation.tryParse("minecraft:tradeable");
    private static final Set<String> FALLBACK_NON_LIBRARIAN_ENCHANTMENTS = Set.of(
            "minecraft:soul_speed",
            "minecraft:swift_sneak",
            "minecraft:wind_burst"
    );
    private static boolean supportsDirectStoredEnchantmentsGetOrDefault = true;

    public boolean checkForMatchingTrade(MerchantOffers trades) {
        ModConfig config = ConfigManager.getConfig();
        if (trades == null || trades.isEmpty()) {
            LOGGER.info("No villager trades are available yet");
            return false;
        }

        for (MerchantOffer trade : trades) {
            if (isMatchingTrade(trade, config)) {
                LOGGER.info("Found matching trade: {}", getTradeDescription(trade));
                return true;
            }
        }

        return false;
    }

    public boolean isMatchingTrade(MerchantOffer trade, ModConfig config) {
        ItemStack result = trade.getResult();
        if (!result.is(Items.ENCHANTED_BOOK)) {
            return false;
        }

        if (trade.getBaseCostA().getCount() > config.targetTrade.maximumPrice) {
            return false;
        }

        ItemEnchantments enchantments = getStoredEnchantments(result);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            ResourceLocation id = entry.getKey().unwrapKey()
                    .map(key -> key.location())
                    .orElse(null);

            if (id != null && matchesEnchantment(id.toString(), entry.getIntValue(), config)) {
                return true;
            }
        }

        return false;
    }

    public String getTradeDescription(MerchantOffer trade) {
        ItemStack result = trade.getResult();
        String enchantmentInfo = getEnchantmentInfo(result);
        return String.format("%s %s for %d emeralds",
                result.getHoverName().getString(),
                enchantmentInfo,
                trade.getBaseCostA().getCount());
    }

    public boolean isValidEnchantment(String enchantmentId) {
        ResourceLocation id = ResourceLocation.tryParse(enchantmentId);
        Registry<Enchantment> registry = enchantmentRegistry();
        return id != null && registry != null && registry.containsKey(id);
    }

    public String getEnchantmentDisplayName(String enchantmentId) {
        ResourceLocation id = ResourceLocation.tryParse(enchantmentId);
        if (id == null) {
            return enchantmentId;
        }

        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) {
            return enchantmentId;
        }

        Enchantment enchantment = resolveEnchantment(registry, id);
        return enchantment != null
                ? enchantment.description().getString()
                : enchantmentId;
    }

    public int getEnchantmentMaxLevel(String enchantmentId) {
        ResourceLocation id = ResourceLocation.tryParse(enchantmentId);
        if (id == null) {
            return 1;
        }

        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) {
            return 1;
        }

        Enchantment enchantment = resolveEnchantment(registry, id);
        return enchantment != null ? enchantment.getMaxLevel() : 1;
    }

    public String[] getAllEnchantmentIds() {
        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) {
            return new String[] {"minecraft:mending"};
        }

        Set<String> tradeableEnchantmentIds = resolveTradeableEnchantmentIds(registry);

        Stream<ResourceLocation> enchantmentIds = registry.keySet().stream();
        if (!tradeableEnchantmentIds.isEmpty()) {
            enchantmentIds = enchantmentIds.filter(id -> tradeableEnchantmentIds.contains(id.toString()));
        } else {
            enchantmentIds = enchantmentIds.filter(this::isFallbackLibrarianTradeable);
        }

        return enchantmentIds
                .map(ResourceLocation::toString)
                .sorted()
                .toArray(String[]::new);
    }

    private Set<String> resolveTradeableEnchantmentIds(Registry<Enchantment> registry) {
        if (TRADEABLE_ENCHANTMENT_TAG_ID == null) {
            return Set.of();
        }

        TagKey<Enchantment> tradeableTag = TagKey.create(Registries.ENCHANTMENT, TRADEABLE_ENCHANTMENT_TAG_ID);

        for (Method method : registry.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (!parameterType.isInstance(tradeableTag)) {
                continue;
            }

            try {
                Object result = method.invoke(registry, tradeableTag);
                Set<String> resolvedIds = collectEnchantmentIdsFromTagResult(result);
                if (!resolvedIds.isEmpty()) {
                    return resolvedIds;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        return Set.of();
    }

    private boolean isFallbackLibrarianTradeable(ResourceLocation enchantmentId) {
        return !FALLBACK_NON_LIBRARIAN_ENCHANTMENTS.contains(enchantmentId.toString());
    }

    private Set<String> collectEnchantmentIdsFromTagResult(Object value) {
        if (value == null) {
            return Set.of();
        }

        if (value instanceof Optional<?> optional) {
            return optional.map(this::collectEnchantmentIdsFromTagResult).orElseGet(Set::of);
        }

        if (value instanceof Holder<?> holder) {
            return holder.unwrapKey()
                    .map(key -> Set.of(key.location().toString()))
                    .orElseGet(Set::of);
        }

        if (value instanceof Iterable<?> iterable) {
            Set<String> ids = new HashSet<>();
            for (Object element : iterable) {
                ids.addAll(collectEnchantmentIdsFromTagResult(element));
            }
            return ids;
        }

        return Set.of();
    }

    private boolean matchesEnchantment(String enchantmentId, int level, ModConfig config) {
        if (!enchantmentId.equals(config.targetTrade.enchantment)) {
            return false;
        }

        return switch (config.targetTrade.levelMode) {
            case ANY -> true;
            case EXACT -> level == config.targetTrade.exactLevel;
            case RANGE -> level >= config.targetTrade.minimumLevel && level <= config.targetTrade.maximumLevel;
        };
    }

    private String getEnchantmentInfo(ItemStack book) {
        ItemEnchantments enchantments = getStoredEnchantments(book);
        StringBuilder result = new StringBuilder();

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Component name = Enchantment.getFullname(entry.getKey(), entry.getIntValue());
            if (!result.isEmpty()) {
                result.append(", ");
            }
            result.append(name.getString());
        }

        return result.isEmpty() ? "" : "(" + result + ")";
    }

    private Enchantment resolveEnchantment(Registry<Enchantment> registry, ResourceLocation id) {
        for (Method method : registry.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (parameterType != ResourceLocation.class) {
                continue;
            }

            try {
                Object result = method.invoke(registry, id);
                Enchantment enchantment = unwrapEnchantment(result);
                if (enchantment != null) {
                    return enchantment;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        return null;
    }

    private Enchantment unwrapEnchantment(Object value) {
        if (value instanceof Enchantment enchantment) {
            return enchantment;
        }

        if (value instanceof Holder<?> holder) {
            Object holderValue = holder.value();
            if (holderValue instanceof Enchantment enchantment) {
                return enchantment;
            }
        }

        if (value instanceof Optional<?> optional) {
            return optional.map(this::unwrapEnchantment).orElse(null);
        }

        return null;
    }

    private ItemEnchantments getStoredEnchantments(ItemStack stack) {
        if (stack == null) {
            return ItemEnchantments.EMPTY;
        }

        if (supportsDirectStoredEnchantmentsGetOrDefault) {
            try {
                return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            } catch (NoSuchMethodError ignored) {
                supportsDirectStoredEnchantmentsGetOrDefault = false;
            }
        }

        Object fallbackValue = invokeStoredEnchantmentsAccessor(stack, DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments enchantments = asItemEnchantments(fallbackValue);
        return enchantments != null ? enchantments : ItemEnchantments.EMPTY;
    }

    private Object invokeStoredEnchantmentsAccessor(ItemStack stack, Object componentType, Object defaultValue) {
        for (Method method : stack.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() == void.class) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 2
                    && parameterTypes[0].isInstance(componentType)
                    && isCompatibleArgument(parameterTypes[1], defaultValue)) {
                try {
                    return method.invoke(stack, componentType, defaultValue);
                } catch (IllegalAccessException | InvocationTargetException ignored) {
                }
            }
        }

        for (Method method : stack.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() == void.class) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1 && parameterTypes[0].isInstance(componentType)) {
                try {
                    return method.invoke(stack, componentType);
                } catch (IllegalAccessException | InvocationTargetException ignored) {
                }
            }
        }

        return null;
    }

    private boolean isCompatibleArgument(Class<?> parameterType, Object value) {
        return value == null || parameterType == Object.class || parameterType.isInstance(value);
    }

    private ItemEnchantments asItemEnchantments(Object value) {
        if (value instanceof ItemEnchantments enchantments) {
            return enchantments;
        }

        if (value instanceof Optional<?> optional) {
            return optional.map(this::asItemEnchantments).orElse(null);
        }

        return null;
    }

    private Registry<Enchantment> enchantmentRegistry() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return null;
        }
        return client.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    }
}
