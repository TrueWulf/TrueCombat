package ru.truwlf.truecombat;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class PlatformScheduler {
    private final TrueCombatPlugin plugin;
    private final boolean folia;

    PlatformScheduler(TrueCombatPlugin plugin) {
        this.plugin = plugin;
        folia = hasClass("io.papermc.paper.threadedregions.RegionizedServer");
    }

    void run(Player player, Runnable task) {
        if (!folia) {
            plugin.getServer().getScheduler().runTask(plugin, task);
            return;
        }
        invokeScheduler(playerScheduler(player), "run", task);
    }

    void runLater(Player player, Runnable task, long delayTicks) {
        if (!folia) {
            plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
            return;
        }
        invokeScheduler(playerScheduler(player), "runDelayed", task, delayTicks);
    }

    void runGlobal(Runnable task) {
        if (!folia) {
            plugin.getServer().getScheduler().runTask(plugin, task);
            return;
        }
        invokeScheduler(serverScheduler("getGlobalRegionScheduler"), "run", task);
    }

    void runAsync(Runnable task) {
        if (!folia) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
            return;
        }
        invokeScheduler(serverScheduler("getAsyncScheduler"), "runNow", task);
    }

    TaskHandle runTimer(Runnable task, long delayTicks, long periodTicks) {
        if (!folia) {
            return new BukkitHandle(plugin.getServer().getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
        }
        return new ReflectiveHandle(invokeScheduler(serverScheduler("getGlobalRegionScheduler"), "runAtFixedRate", task, delayTicks, periodTicks));
    }

    interface TaskHandle {
        void cancel();
    }

    private record BukkitHandle(BukkitTask task) implements TaskHandle {
        @Override
        public void cancel() {
            task.cancel();
        }
    }

    private record ReflectiveHandle(Object task) implements TaskHandle {
        @Override
        public void cancel() {
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not cancel scheduled task", exception);
            }
        }
    }

    private Object invokeScheduler(Object scheduler, String name, Runnable task, Object... extra) {
        Method method = findMethod(scheduler.getClass(), name, extra.length + 2);
        Class<?> consumerType = method.getParameterTypes()[1];
        Object consumer = Proxy.newProxyInstance(consumerType.getClassLoader(), new Class<?>[]{consumerType}, handler(task));
        Object[] arguments = new Object[extra.length + 2];
        arguments[0] = plugin;
        arguments[1] = consumer;
        System.arraycopy(extra, 0, arguments, 2, extra.length);
        try {
            return method.invoke(scheduler, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not schedule task", exception);
        }
    }

    private Object playerScheduler(Player player) {
        try {
            return player.getClass().getMethod("getScheduler").invoke(player);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Folia entity scheduler is unavailable", exception);
        }
    }

    private Object serverScheduler(String accessor) {
        try {
            Method method = plugin.getServer().getClass().getMethod(accessor);
            return method.invoke(plugin.getServer());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Folia scheduler is unavailable: " + accessor, exception);
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return method;
        }
        throw new IllegalStateException("Scheduler method is unavailable: " + name);
    }

    private static InvocationHandler handler(Runnable task) {
        return (proxy, method, args) -> {
            if (method.getName().equals("accept")) task.run();
            return null;
        };
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
