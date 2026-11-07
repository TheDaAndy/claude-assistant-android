package dev.claude.assistant.ankai;

/** Recovery never creates consent or revives a paused/revoked grant. */
public final class AdbRecoveryPolicy {
    private AdbRecoveryPolicy(){}
    public static boolean allowed(int api,boolean enabled,boolean linked,boolean shellGranted,boolean identity,boolean pairing){
        return api>=30&&enabled&&linked&&shellGranted&&identity&&!pairing;
    }
    public static long retryMillis(int failures){return Math.min(300000L,5000L<<Math.min(6,Math.max(0,failures-1)));}
}
