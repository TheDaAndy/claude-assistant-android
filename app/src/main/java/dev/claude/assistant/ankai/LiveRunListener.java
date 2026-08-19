package dev.claude.assistant.ankai;

/** Empfaengt fortlaufende Ereignisse eines sichtbaren Ankai-Chatlaufs. */
public interface LiveRunListener {
    void onEvent(LiveRunEvent event);
}
