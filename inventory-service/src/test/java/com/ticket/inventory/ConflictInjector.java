package com.ticket.inventory;

import com.ticket.inventory.repository.SeatRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.config.BeanPostProcessor;

final class ConflictInjector implements BeanPostProcessor {

    private final AtomicReference<Runnable> competingAction = new AtomicReference<>();
    private final AtomicInteger loads = new AtomicInteger();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof SeatRepository repository) {
            return injecting(repository);
        }
        return bean;
    }

    void runOnceAfterTheNextLoad(Runnable action) {
        competingAction.set(action);
    }

    int loads() {
        return loads.get();
    }

    void reset() {
        competingAction.set(null);
        loads.set(0);
    }

    private SeatRepository injecting(SeatRepository target) {
        return (SeatRepository) Proxy.newProxyInstance(
            SeatRepository.class.getClassLoader(),
            new Class<?>[] {SeatRepository.class},
            (proxy, method, arguments) -> {
                Object result = invoke(target, method, arguments);
                if ("findByEventIdAndSeatId".equals(method.getName())) {
                    loads.incrementAndGet();
                    runCompetingAction();
                }
                return result;
            });
    }

    private void runCompetingAction() {
        Runnable action = competingAction.getAndSet(null);
        if (action != null) {
            CompletableFuture.runAsync(action).join();
        }
    }

    private static Object invoke(SeatRepository target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
