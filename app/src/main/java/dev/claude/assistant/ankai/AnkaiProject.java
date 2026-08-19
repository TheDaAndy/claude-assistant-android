package dev.claude.assistant.ankai;

/** Ein fuer den Nutzer sichtbares Ankai-Projekt. */
public final class AnkaiProject {

    public final String id;
    public final String name;

    public AnkaiProject(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return name == null ? id : name;
    }
}
