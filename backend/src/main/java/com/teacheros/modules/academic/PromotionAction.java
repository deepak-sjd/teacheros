package com.teacheros.modules.academic;

public enum PromotionAction {
    /** Move to the next class (or an explicitly mapped section). */
    PROMOTE,
    /** Same section, new academic year. */
    DETAIN,
    /** Deactivate - graduating or leaving the school. */
    LEAVE
}
