package dev.claude.assistant.ankai;

public final class AssistantInputPolicy {
    public enum Action {
        RECORD,
        SUBMIT
    }

    private AssistantInputPolicy() {}

    public static Action action(boolean recording, String input) {
        return recording || (input != null && !input.trim().isEmpty())
                ? Action.SUBMIT
                : Action.RECORD;
    }
}
