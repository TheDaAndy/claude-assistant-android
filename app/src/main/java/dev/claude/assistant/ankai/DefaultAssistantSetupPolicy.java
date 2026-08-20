package dev.claude.assistant.ankai;

/** Android-freie Entscheidung fuer den einfachsten verfuegbaren Assistenten-Setup-Pfad. */
public final class DefaultAssistantSetupPolicy {
    private DefaultAssistantSetupPolicy() {}

    public static boolean shouldRequestAssistantRole(int sdkInt, boolean roleAvailable) {
        return sdkInt >= 29 && roleAvailable;
    }
}
