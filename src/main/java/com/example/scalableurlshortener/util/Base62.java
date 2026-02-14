package com.example.scalableurlshortener.util;

public final class Base62 {
    private static final char[] ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int BASE = ALPHABET.length;

    private Base62() {}

    public static String encode(long value) {
        if (value < 0) throw new IllegalArgumentException("value must be >= 0");
        if (value == 0) return String.valueOf(ALPHABET[0]);

        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            int rem = (int) (value % BASE);
            sb.append(ALPHABET[rem]);
            value /= BASE;
        }
        return sb.reverse().toString();
    }
}
