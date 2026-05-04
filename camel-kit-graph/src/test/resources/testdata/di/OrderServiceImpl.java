package com.example.di;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Named("orderService")
@Singleton
public class OrderServiceImpl implements OrderService {

    @ConfigProperty(name = "order.max-retries")
    int maxRetries;

    @Override
    public void process(Object exchange) {
    }
}
