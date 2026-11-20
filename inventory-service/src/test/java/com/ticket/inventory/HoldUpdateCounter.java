package com.ticket.inventory;

import com.ticket.inventory.repository.SeatRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.BeanPostProcessor;

final class HoldUpdateCounter implements BeanPostProcessor {

    private final AtomicInteger updates = new AtomicInteger();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof SeatRepository repository) {
            return counting(repository);
        }
        return bean;
    }

    int count() {
        return updates.get();
    }

    void reset() {
        updates.set(0);
    }

    private SeatRepository counting(SeatRepository target) {
        return (SeatRepository) Proxy.newProxyInstance(
            SeatRepository.class.getClassLoader(),
            new Class<?>[] {SeatRepository.class},
            (proxy, method, arguments) -> {
                if ("holdIfAvailable".equals(method.getName())) {
                    updates.incrementAndGet();
                }
                try {
                    return method.invoke(target, arguments);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
    }
}
