package dev.claude.assistant.ankai;

/** Timestamp only; never stores screen content or claims physical human presence. */
public final class RecentInteraction {
    private long last = -1;
    public synchronized void record(long elapsedMs) { if(elapsedMs>=0)last=elapsedMs; }
    public synchronized Long age(long elapsedMs, boolean monitoring) {
        if(!monitoring || last<0 || elapsedMs<last)return null;
        return Math.min(86400000L,elapsedMs-last);
    }
}
