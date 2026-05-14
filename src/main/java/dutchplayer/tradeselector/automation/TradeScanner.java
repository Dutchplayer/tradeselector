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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeSelector");

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

        ItemEnchantments enchantments = result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
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

        return registry.getHolder(id)
                .map(holder -> holder.value().description().getString())
                .orElse(enchantmentId);
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

        return registry.getOptional(id)
                .map(Enchantment::getMaxLevel)
                .orElse(1);
    }

    public String[] getAllEnchantmentIds() {
        Registry<Enchantment> registry = enchantmentRegistry();
        if (registry == null) {
            return new String[] {"minecraft:mending"};
        }

        return registry.keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toArray(String[]::new);
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
        ItemEnchantments enchantments = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
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

    private Registry<Enchantment> enchantmentRegistry() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return null;
        }
        return client.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    }
}
