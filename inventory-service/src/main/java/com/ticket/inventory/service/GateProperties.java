package com.ticket.inventory.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "inventory.hold.gate")
public record GateProperties(@DefaultValue("true") boolean enabled) {
}
