package com.ticket.events;

public final class MessageTypes {

    public static final String HOLD_SEAT_COMMAND = "HoldSeatCommand";
    public static final String CONFIRM_SEAT_COMMAND = "ConfirmSeatCommand";
    public static final String RELEASE_SEAT_COMMAND = "ReleaseSeatCommand";

    public static final String SEAT_HELD = "SeatHeld";
    public static final String SEAT_HOLD_REJECTED = "SeatHoldRejected";
    public static final String SEAT_CONFIRMED = "SeatConfirmed";
    public static final String SEAT_CONFIRM_FAILED = "SeatConfirmFailed";
    public static final String SEAT_HOLD_EXPIRED = "SeatHoldExpired";

    public static final String PROCESS_PAYMENT_COMMAND = "ProcessPaymentCommand";
    public static final String CANCEL_PAYMENT_COMMAND = "CancelPaymentCommand";

    public static final String PAYMENT_COMPLETED = "PaymentCompleted";
    public static final String PAYMENT_DECLINED = "PaymentDeclined";
    public static final String PAYMENT_CANCELLED = "PaymentCancelled";

    private MessageTypes() {
    }
}
