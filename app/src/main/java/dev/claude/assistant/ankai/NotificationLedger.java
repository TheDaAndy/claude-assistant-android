package dev.claude.assistant.ankai;

import java.util.*;

/** Bounded, durable delivery receipts scoped to the linked instance/account. */
public final class NotificationLedger {
    private final SecretStore store;
    private final String key;
    private final LinkedHashSet<String> seen = new LinkedHashSet<>();
    public NotificationLedger(SecretStore store, String account) {
        this.store = store;
        key = "notifications.seen." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(account.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String saved = store.get(key);
        if (saved != null) for (String id : saved.split("\n")) if (!id.isEmpty()) seen.add(id);
    }
    public boolean shouldDeliver(AnkaiNotification item) { return item.unread && !seen.contains(item.id); }
    /** Call only after Android accepted delivery; failed/offline reads never advance receipts. */
    public void delivered(String id) {
        seen.remove(id); seen.add(id);
        while (seen.size() > 4096) seen.remove(seen.iterator().next());
        store.put(key, String.join("\n", seen));
    }
}
