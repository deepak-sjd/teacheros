package com.teacheros.modules.ai;

/**
 * Lets an AiTutorClient report the provider's token counts for the call it
 * is making right now, without changing AiTutorClient's String-returning
 * signatures. AiUsageService#track clears this before a call and reads it
 * after.
 *
 * Thread-local, so it relies on the provider call running synchronously on
 * the request thread (true today). If an AI call is ever made async, this
 * must be replaced with an explicit return value.
 */
public final class AiTokenReporter {

    private static final ThreadLocal<int[]> CURRENT = new ThreadLocal<>();

    private AiTokenReporter() {
    }

    /**
     * Accumulates, so a client that makes more than one HTTP call per logical call
     * still reports a total.
     */
    public static void report(int inputTokens, int outputTokens) {
        int[] existing = CURRENT.get();
        if (existing == null) {
            CURRENT.set(new int[] { inputTokens, outputTokens });
        } else {
            existing[0] += inputTokens;
            existing[1] += outputTokens;
        }
    }

    static void clear() {
        CURRENT.remove();
    }

    /** Returns {input, output} or null if nothing was reported, and clears. */
    static int[] consume() {
        int[] value = CURRENT.get();
        CURRENT.remove();
        return value;
    }
}
