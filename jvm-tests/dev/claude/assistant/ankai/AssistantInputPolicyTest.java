package dev.claude.assistant.ankai;

public final class AssistantInputPolicyTest {
    public static void main(String[] args) {
        emptyIdleInputOffersMicrophone();
        recordingOffersSubmit();
        typedTextOffersSubmit();
    }

    private static void emptyIdleInputOffersMicrophone() {
        assertEquals(AssistantInputPolicy.Action.RECORD,
                AssistantInputPolicy.action(false, "  "));
    }

    private static void recordingOffersSubmit() {
        assertEquals(AssistantInputPolicy.Action.SUBMIT,
                AssistantInputPolicy.action(true, ""));
    }

    private static void typedTextOffersSubmit() {
        assertEquals(AssistantInputPolicy.Action.SUBMIT,
                AssistantInputPolicy.action(false, " Bitte hilf mir "));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
