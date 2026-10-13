package dev.claude.assistant.ankai;
public final class DeviceCommandPolicyTest {
    public void testAllowsOnlyExplicitCapabilities() {
        Assert.isTrue("info", DeviceCommandPolicy.allowed("device_info", null, 101, 100));
        Assert.isTrue("snapshot", DeviceCommandPolicy.allowed("settings_snapshot", null, 101, 100));
        Assert.isTrue("wifi", DeviceCommandPolicy.allowed("open_settings", "wifi", 101, 100));
        Assert.isTrue("no shell", !DeviceCommandPolicy.allowed("shell", "id", 101, 100));
        Assert.isTrue("no arbitrary intents", !DeviceCommandPolicy.allowed("open_settings", "intent://evil", 101, 100));
    }
    public void testExpiredCommandsAreRejected() {
        Assert.isTrue("expired", !DeviceCommandPolicy.allowed("device_info", null, 100, 100));
        Assert.isTrue("missing expiry", !DeviceCommandPolicy.allowed("device_info", null, 0, 100));
    }
    public void testChangedGrantRejectsOldCommandsAndNotifications() {
        Assert.isTrue("same revision",DeviceCommandPolicy.matchesGrant(3,3));
        Assert.isTrue("legacy revision",DeviceCommandPolicy.matchesGrant(0,0));
        Assert.isTrue("old notification",!DeviceCommandPolicy.matchesGrant(2,3));
        Assert.isTrue("future revision",!DeviceCommandPolicy.matchesGrant(4,3));
        Assert.isTrue("invalid",!DeviceCommandPolicy.matchesGrant(-1,-1));
    }
    public void testBackoffIsBounded() {
        Assert.eq(5, DeviceCommandPolicy.retrySeconds(1));
        Assert.eq(10, DeviceCommandPolicy.retrySeconds(2));
        Assert.eq(300, DeviceCommandPolicy.retrySeconds(100));
    }
}
