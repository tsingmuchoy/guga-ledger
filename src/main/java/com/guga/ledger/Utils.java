package com.guga.ledger;

import java.text.DecimalFormat;

public class Utils {
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");

    public static String money(long cents) {
        return "¥" + DF.format(cents / 100.0);
    }

    public static String moneySigned(long cents, int type) {
        return (type == 1 ? "+ " : "- ") + money(Math.abs(cents));
    }
}
