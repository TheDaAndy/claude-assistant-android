package dev.claude.assistant.ankai;
import java.util.*;

public final class NotificationsTest {
    private AnkaiNotification item(String id, boolean unread) {
        return AnkaiNotification.parse(AnkaiJson.parseObject("{\"notifications\":[{\"id\":\""+id+"\",\"title\":\"Fertig\",\"body\":\"Dein Ergebnis\",\"unread\":"+unread+",\"readAt\":null}]}")).get(0);
    }
    public void testReadRecoversExpiredCookieWithoutMutation() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.rejectedCookieStatus = 401;
            fake.respond("/api/notifications",200,"application/json","{\"notifications\":[{\"id\":\"notice-1\",\"title\":\"Fertig\",\"body\":\"Ergebnis\",\"unread\":true}]}");
            AnkaiClient client = new AnkaiClient(new AnkaiEndpoint(fake.baseUrl()),"andy","protected");
            client.useSessionCookie("old=expired");
            List<AnkaiNotification> list = client.listNotifications();
            Assert.eq(1,list.size()); Assert.eq("Fertig",list.get(0).title);
            Assert.eq(2L,fake.requestLog.stream().filter(s -> s.equals("GET /api/notifications")).count());
            Assert.eq(0L,fake.requestLog.stream().filter(s -> !s.startsWith("GET ")).count());
        } finally { fake.stop(); }
    }
    public void testDeliverySurvivesRestartAndAccountIsolation() {
        Map<String,String> values = new HashMap<>();
        SecretStore store = new SecretStore() {
            public String get(String k){return values.get(k);}
            public void put(String k,String v){values.put(k,v);}
            public void remove(String k){values.remove(k);}
        };
        NotificationLedger ledger = new NotificationLedger(store,"server\nandy");
        Assert.isTrue("new unread delivered",ledger.shouldDeliver(item("one",true)));
        Assert.isTrue("read suppressed",!ledger.shouldDeliver(item("read",false)));
        ledger.delivered("one");
        Assert.isTrue("no replay after restart",!new NotificationLedger(store,"server\nandy").shouldDeliver(item("one",true)));
        Assert.isTrue("other account independent",new NotificationLedger(store,"server\nother").shouldDeliver(item("one",true)));
        Assert.isTrue("failed delivery remains retryable",new NotificationLedger(store,"server\nandy").shouldDeliver(item("two",true)));
    }
    public void testUntrustedTargetsStayOnLinkedInstance() {
        String base="https://ankai.example";
        Assert.eq(base+"/?session=abc",AnkaiNotification.safeTarget(base,"/?session=abc"));
        for(String url:Arrays.asList("https://evil.example/x","//evil.example","intent://foo","javascript:alert(1)","https://user@ankai.example/","https://ankai.example:444/"))
            Assert.eq(base+"/",AnkaiNotification.safeTarget(base,url));
        Assert.eq(base+"/#personal",AnkaiNotification.safeTarget(base,"/#personal"));
    }
    public void testMalformedInboxCannotProduceFakeDelivery() {
        Assert.eq(0,AnkaiNotification.parse(AnkaiJson.parseObject("{\"notifications\":[null,{}, {\"id\":\"../bad\"}]}")).size());
        try { AnkaiNotification.parse(AnkaiJson.parseObject("{}")); Assert.fail("missing list"); } catch(IllegalArgumentException expected) { }
        List<AnkaiNotification> rows=AnkaiNotification.parse(AnkaiJson.parseObject("{\"notifications\":[{\"id\":\"a\",\"unread\":true,\"readAt\":\"2026-01-01\"}]}"));
        Assert.isTrue("readAt wins",!rows.get(0).unread);
    }
}
