package dev.claude.assistant.ankai;

import java.util.ArrayList;
import java.util.List;

public final class SpeechTextTest {
    public void testUrlsMarkdownAndPunctuation() {
        Assert.eq("Siehe example.org, example.net und example.com!",
            SpeechText.forPlayback("Siehe https://example.org/pfad?x=1#teil, [Quelle](http://example.net/a) und <https://example.com/q>!" ));
        Assert.eq("(example.org).", SpeechText.forPlayback("(https://example.org/a)."));
        Assert.eq("example.org", SpeechText.forPlayback("HTTPS://user:pass@example.org:8443/a?q=secret#part"));
        Assert.eq("example.orgexample.net", SpeechText.forPlayback("[A](https://example.org/a(b)c)[B](https://example.net/q)"));
        Assert.eq("[2001:db8::1]", SpeechText.forPlayback("https://[2001:db8::1]:8443/path"));
        Assert.eq("münchen.de", SpeechText.forPlayback("https://münchen.de/weg"));
    }

    public void testEveryStreamingSplitHoldsUrlAndMarkdown() {
        for (String message : new String[]{"Siehe https://example.org/pfad?x=1#teil", "Siehe [die Quelle](https://example.org/pfad?x=1#teil)"}) {
            for (int split = message.indexOf(' ') + 1; split < message.length(); split++) {
                LiveRunState run = new LiveRunRegistry().start("s", "r");
                Recording playback = new Recording();
                LiveRunSpeechController controller = new LiveRunSpeechController(run, playback);
                controller.onChanged(new LiveRunSnapshot("s", "r", message.substring(0, split), false, true));
                controller.onChanged(new LiveRunSnapshot("s", "r", message, false, true));
                controller.onChanged(new LiveRunSnapshot("s", "r", message, true, true));
                Assert.eq("Siehe example.org", String.join(" ", playback.text));
                controller.close();
            }
        }
    }

    public void testActualRunKeepsTextAndStopsPendingUrl() {
        String message = "Quelle: [Link](https://example.org/path?q=1#part)";
        LiveRunState run = new LiveRunRegistry().start("s", "r");
        Recording playback = new Recording();
        new LiveRunSpeechController(run, playback);
        run.accept(new LiveRunEvent("assistant", message));
        run.accept(new LiveRunEvent("done", null));
        Assert.eq(message, run.text());
        Assert.eq("Quelle: example.org", String.join(" ", playback.text));
        LiveRunState stopped = new LiveRunRegistry().start("s", "r2");
        Recording muted = new Recording();
        new LiveRunSpeechController(stopped, muted);
        stopped.accept(new LiveRunEvent("assistant", "https://example.org/private"));
        stopped.closeOverlay();
        stopped.accept(new LiveRunEvent("done", null));
        Assert.eq(0, muted.text.size());
        Assert.eq(1, muted.stops);
    }

    private static final class Recording implements SpeechPlayback {
        final List<String> text = new ArrayList<>();
        int stops;
        public void speak(String value) { text.add(value); }
        public void stop() { stops++; }
    }
}
