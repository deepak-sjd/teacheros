package com.teacheros.modules.feestructure;

/** NONE is only valid for tenant rules; a discount is always FIXED or PERCENT. */
public enum AmountType {
    NONE,
    FIXED,
    PERCENT
}
