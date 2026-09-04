package com.strutton.dynamicmagic.client;

public final class ClientManaState {
    public static double current;
    public static double maximum = 100;
    public static boolean unlimited;
    private ClientManaState() {}
    public static void update(double currentMana, double maximumMana, boolean isUnlimited) {
        current = currentMana; maximum = Math.max(1, maximumMana); unlimited = isUnlimited;
    }
}
