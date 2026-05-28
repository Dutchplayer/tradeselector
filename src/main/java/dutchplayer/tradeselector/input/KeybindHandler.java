package dutchplayer.tradeselector.input;

import com.mojang.blaze3d.platform.InputConstants;
import dutchplayer.tradeselector.TradeRerollModClient;
import dutchplayer.tradeselector.automation.VillagerBinder;
import dutchplayer.tradeselector.util.ModState;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;

public class KeybindHandler {
    private static final String CATEGORY = "category.tradeselector";
    private static final String CATEGORY_ID = "tradeselector:keybinds";
    private static final String CATEGORY_NAMESPACE = "tradeselector";
    private static final String CATEGORY_PATH = "keybinds";

    private static KeyMapping openGuiKey;
    private static KeyMapping bindVillagerKey;
    private static KeyMapping bindJobBlockKey;
    private static KeyMapping toggleAutomationKey;
    private static Class<?> cachedCategoryClass;
    private static Object cachedCategory;

    public static void registerKeybinds() {
        openGuiKey = register("key.tradeselector.open_gui", InputConstants.KEY_K);
        bindVillagerKey = register("key.tradeselector.bind_villager", InputConstants.KEY_V);
        bindJobBlockKey = register("key.tradeselector.bind_job_block", InputConstants.KEY_B);
        toggleAutomationKey = register("key.tradeselector.toggle_automation", InputConstants.KEY_N);
    }

    public static void handleKeybinds(VillagerBinder villagerBinder, ModState modState) {
        while (openGuiKey.consumeClick()) {
            TradeRerollModClient.openGui();
        }

        while (bindVillagerKey.consumeClick()) {
            villagerBinder.bindVillager();
        }

        while (bindJobBlockKey.consumeClick()) {
            villagerBinder.bindJobBlock();
        }

        while (toggleAutomationKey.consumeClick()) {
            TradeRerollModClient.toggleAutomation();
        }
    }

    private static KeyMapping register(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(createKeyMapping(translationKey, keyCode));
    }

    private static KeyMapping createKeyMapping(String translationKey, int keyCode) {
        Class<?> categoryClass = findCategoryClassFromConstructors();

        if (categoryClass != null) {
            Object category = getOrCreateCategory(categoryClass);
            if (category != null) {
                KeyMapping mapping = createCategoryBasedKeyMapping(translationKey, keyCode, categoryClass, category);
                if (mapping != null) {
                    return mapping;
                }
            }
        }

        KeyMapping mapping = tryCreate(
                new Class<?>[] {String.class, int.class, String.class},
                translationKey,
                keyCode,
                CATEGORY
        );
        if (mapping != null) {
            return mapping;
        }

        mapping = tryCreate(
                new Class<?>[] {String.class, InputConstants.Type.class, int.class, String.class},
                translationKey,
                InputConstants.Type.KEYSYM,
                keyCode,
                CATEGORY
        );
        if (mapping != null) {
            return mapping;
        }

        throw new IllegalStateException("Unable to create key mapping for current Minecraft version. Available constructors: " + availableConstructors());
    }

    private static Object getOrCreateCategory(Class<?> categoryClass) {
        if (cachedCategoryClass != null && cachedCategoryClass == categoryClass && cachedCategory != null) {
            return cachedCategory;
        }

        Object category = createCustomCategory(categoryClass);
        if (category != null) {
            cachedCategoryClass = categoryClass;
            cachedCategory = category;
        }

        return category;
    }

    private static KeyMapping tryCreate(Class<?>[] parameterTypes, Object... arguments) {
        try {
            Constructor<KeyMapping> constructor = KeyMapping.class.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException reflectionException) {
            throw new IllegalStateException("Unable to invoke KeyMapping constructor. Available constructors: " + availableConstructors(), reflectionException);
        }
    }

    private static Class<?> findCategoryClassFromConstructors() {
        for (Constructor<?> constructor : KeyMapping.class.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();

            if (parameterTypes.length == 3
                    && parameterTypes[0] == String.class
                    && parameterTypes[1] == int.class
                    && parameterTypes[2] != String.class) {
                return parameterTypes[2];
            }

            if (parameterTypes.length >= 4
                    && parameterTypes[0] == String.class
                    && parameterTypes[1] == InputConstants.Type.class
                    && parameterTypes[2] == int.class
                    && parameterTypes[3] != String.class) {
                return parameterTypes[3];
            }
        }
        return null;
    }

    private static KeyMapping createCategoryBasedKeyMapping(String translationKey, int keyCode, Class<?> categoryClass, Object category) {
        KeyMapping mapping = tryCreate(
                new Class<?>[] {String.class, InputConstants.Type.class, int.class, categoryClass},
                translationKey,
                InputConstants.Type.KEYSYM,
                keyCode,
                category
        );
        if (mapping != null) {
            return mapping;
        }

        mapping = tryCreate(
                new Class<?>[] {String.class, InputConstants.Type.class, int.class, categoryClass, int.class},
                translationKey,
                InputConstants.Type.KEYSYM,
                keyCode,
                category,
                0
        );
        if (mapping != null) {
            return mapping;
        }

        return tryCreate(
                new Class<?>[] {String.class, int.class, categoryClass},
                translationKey,
                keyCode,
                category
        );
    }

    private static Object createCustomCategory(Class<?> categoryClass) {
        Object category = tryCreateCategoryWithIdentifierFactory(categoryClass);
        if (category != null) {
            return category;
        }

        category = tryCreateCategoryWithIdentifierConstructor(categoryClass);
        if (category != null) {
            return category;
        }

        category = tryCreateCategoryWithStringConstructor(categoryClass);
        if (category != null) {
            return category;
        }

        return tryCreateCategoryWithStringFactory(categoryClass);
    }

    private static Object tryCreateCategoryWithIdentifierConstructor(Class<?> categoryClass) {
        for (Constructor<?> constructor : categoryClass.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = constructor.getParameterTypes()[0];
            if (parameterType == String.class) {
                continue;
            }

            Object argument = createIdentifierInstance(parameterType);
            if (argument == null) {
                continue;
            }

            try {
                constructor.setAccessible(true);
                Object result = constructor.newInstance(argument);
                if (result != null) {
                    return result;
                }
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        return null;
    }

    private static Object tryCreateCategoryWithIdentifierFactory(Class<?> categoryClass) {
        for (Method method : categoryClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
                continue;
            }
            if (!categoryClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (parameterType == String.class) {
                continue;
            }

            Object argument = createIdentifierInstance(parameterType);
            if (argument == null) {
                continue;
            }

            try {
                method.setAccessible(true);
                Object result = method.invoke(null, argument);
                if (result != null) {
                    return result;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        return null;
    }

    private static Object tryCreateCategoryWithStringConstructor(Class<?> categoryClass) {
        for (Constructor<?> constructor : categoryClass.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != 1) {
                continue;
            }
            if (constructor.getParameterTypes()[0] != String.class) {
                continue;
            }

            try {
                constructor.setAccessible(true);
                Object result = constructor.newInstance(CATEGORY);
                if (result != null) {
                    return result;
                }
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        return null;
    }

    private static Object tryCreateCategoryWithStringFactory(Class<?> categoryClass) {
        for (Method method : categoryClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
                continue;
            }
            if (!categoryClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (method.getParameterTypes()[0] != String.class) {
                continue;
            }

            try {
                method.setAccessible(true);
                Object result = method.invoke(null, CATEGORY);
                if (result != null) {
                    return result;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        return null;
    }

    private static Object createIdentifierInstance(Class<?> identifierClass) {
        for (Method method : identifierClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!identifierClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            try {
                if (parameterTypes.length == 2
                        && parameterTypes[0] == String.class
                        && parameterTypes[1] == String.class) {
                    method.setAccessible(true);
                    Object result = method.invoke(null, CATEGORY_NAMESPACE, CATEGORY_PATH);
                    if (result != null) {
                        return result;
                    }
                }

                if (parameterTypes.length == 1 && parameterTypes[0] == String.class) {
                    method.setAccessible(true);
                    Object result = method.invoke(null, CATEGORY_ID);
                    if (result != null) {
                        return result;
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        for (Constructor<?> constructor : identifierClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            try {
                if (parameterTypes.length == 2
                        && parameterTypes[0] == String.class
                        && parameterTypes[1] == String.class) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(CATEGORY_NAMESPACE, CATEGORY_PATH);
                }

                if (parameterTypes.length == 1 && parameterTypes[0] == String.class) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(CATEGORY_ID);
                }
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        return null;
    }

    private static String availableConstructors() {
        return Arrays.stream(KeyMapping.class.getDeclaredConstructors())
                .map(constructor -> Arrays.stream(constructor.getParameterTypes())
                        .map(Class::getName)
                        .collect(Collectors.joining(", ", "(", ")")))
                .collect(Collectors.joining(", "));
    }
}
