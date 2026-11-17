package com.ticket.inventory.service;

import java.util.UUID;

public interface HoldGate {

    GateDecision admit(UUID orderId, String eventId, String seatId);

    void release(UUID orderId, String eventId, String seatId);
}
