package dutchplayer.tradeselector.mixin;

import dutchplayer.tradeselector.TradeRerollModClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

@Mixin(Minecraft.class)
public abstract class ReduceFPSMixin {
    @Shadow public Options options;
    @Shadow public abstract boolean isWindowActive();

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void tradeselector$overrideInactiveFpsLimit(CallbackInfoReturnable<Integer> cir) {
        if (!TradeRerollModClient.shouldOverrideInactiveFpsLimit()) {
            return;
        }

        if (isWindowActive()) {
            return;
        }

        Integer activeLimit = resolveConfiguredFramerateLimit(options);
        if (activeLimit != null && activeLimit > 0) {
            cir.setReturnValue(activeLimit);
        }
    }

    private static Integer resolveConfiguredFramerateLimit(Options options) {
        if (options == null) {
            return null;
        }

        Object framerateOption = invokeOptionsAccessorNoArgs(options, "framerateLimit", "maxFps");
        Integer value = readIntegerOptionValue(framerateOption);
        if (value != null && value > 0) {
            return value;
        }

        int fallback = Options.UNLIMITED_FRAMERATE_CUTOFF + 10;
        return fallback > 0 ? fallback : null;
    }

    private static Object invokeOptionsAccessorNoArgs(Options options, String... accessorNames) {
        for (String accessorName : accessorNames) {
            try {
                Method method = options.getClass().getMethod(accessorName);
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                    continue;
                }
                return method.invoke(options);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private static Integer readIntegerOptionValue(Object optionInstance) {
        if (optionInstance == null) {
            return null;
        }

        if (optionInstance instanceof Number number) {
            return number.intValue();
        }

        Integer value = invokeIntegerGetterByName(optionInstance, "get", "getValue");
        if (value != null) {
            return value;
        }

        value = invokeIntegerGetterHeuristic(optionInstance);
        if (value != null) {
            return value;
        }

        return readIntegerFromFields(optionInstance);
    }

    private static Integer invokeIntegerGetterByName(Object target, String... names) {
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                    continue;
                }

                Object result = method.invoke(target);
                if (result instanceof Number number) {
                    return number.intValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private static Integer invokeIntegerGetterHeuristic(Object target) {
        for (Method method : target.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || method.getReturnType() == void.class) {
                continue;
            }

            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            if (method.getName().equals("hashCode") || method.getName().equals("toString") || method.getName().equals("getClass")) {
                continue;
            }

            try {
                Object result = method.invoke(target);
                if (result instanceof Number number) {
                    return number.intValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private static Integer readIntegerFromFields(Object target) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    Object result = field.get(target);
                    if (result instanceof Number number) {
                        return number.intValue();
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }

            type = type.getSuperclass();
        }

        return null;
    }
}
