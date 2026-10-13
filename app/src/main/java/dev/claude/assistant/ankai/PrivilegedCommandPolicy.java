package dev.claude.assistant.ankai;
import java.nio.charset.StandardCharsets;
public final class PrivilegedCommandPolicy {
    public static void validate(String command,int timeout) {
        if(command==null || command.trim().isEmpty() || command.indexOf('\0')>=0 || command.getBytes(StandardCharsets.UTF_8).length>8192)
            throw new IllegalArgumentException("Befehl fehlt, enthält NUL oder ist zu groß (max. 8 KiB).");
        if(timeout<1 || timeout>20)throw new IllegalArgumentException("Timeout muss 1–20 Sekunden betragen.");
    }
}
