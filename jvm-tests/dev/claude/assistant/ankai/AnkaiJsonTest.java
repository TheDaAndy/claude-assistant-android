package dev.claude.assistant.ankai;

import java.util.List;
import java.util.Map;

public class AnkaiJsonTest {

    public void testParsesFlatObject() {
        Map<String, Object> o = AnkaiJson.parseObject("{\"sessionId\":\"s1\",\"runId\":\"r1\"}");
        Assert.eq("s1", o.get("sessionId"));
        Assert.eq("r1", o.get("runId"));
    }

    public void testParsesNumbersBooleansAndNull() {
        Map<String, Object> o = AnkaiJson.parseObject("{\"percent\":42,\"ratio\":0.5,\"active\":false,\"x\":null}");
        Assert.eq(42.0, o.get("percent"));
        Assert.eq(0.5, o.get("ratio"));
        Assert.eq(Boolean.FALSE, o.get("active"));
        Assert.isTrue("null bleibt null", o.get("x") == null);
    }

    public void testParsesEscapesAndUnicode() {
        Map<String, Object> o = AnkaiJson.parseObject("{\"t\":\"Zeile\\n\\\"x\\\" \\u00fcber\"}");
        Assert.eq("Zeile\n\"x\" \u00fcber", o.get("t"));
    }

    @SuppressWarnings("unchecked")
    public void testParsesNestedArrayOfObjects() {
        Map<String, Object> o = AnkaiJson.parseObject(
            "{\"candidates\":[{\"id\":\"p1\",\"name\":\"Alpha\"},{\"id\":\"p2\",\"name\":\"Beta\"}]}");
        List<Object> list = (List<Object>) o.get("candidates");
        Assert.eq(2, list.size());
        Assert.eq("Beta", ((Map<String, Object>) list.get(1)).get("name"));
    }

    public void testRejectsNonObject() {
        try {
            AnkaiJson.parseObject("[1,2]");
            Assert.fail("Array darf nicht als Objekt durchgehen");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testRejectsTrailingGarbage() {
        try {
            AnkaiJson.parseObject("{\"a\":1} kaputt");
            Assert.fail("Muell nach dem Objekt muss auffallen");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testStringHelperReturnsNullForMissingOrNonString() {
        Map<String, Object> o = AnkaiJson.parseObject("{\"a\":\"x\",\"b\":3}");
        Assert.eq("x", AnkaiJson.string(o, "a"));
        Assert.isTrue("Zahl ist kein String", AnkaiJson.string(o, "b") == null);
        Assert.isTrue("fehlender Key ist null", AnkaiJson.string(o, "c") == null);
    }

    public void testEscapeProducesValidJsonString() {
        Assert.eq("\"a\\\"b\\\\c\\n\"", AnkaiJson.escape("a\"b\\c\n"));
    }
}
