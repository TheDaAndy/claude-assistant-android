package dev.claude.assistant.ankai;
public final class AdbUidPolicyTest {
 public void testPreservesActualIdentity(){Assert.eq(0,AdbUidPolicy.parse("0"));Assert.eq(2000,AdbUidPolicy.parse("2000"));}
 public void testRejectsUntrustedOutput(){for(String v:new String[]{null,"","10000","0 root","2000\n0","-1"})try{AdbUidPolicy.parse(v);Assert.fail("Invalid UID accepted");}catch(IllegalArgumentException expected){}}
 public void testRequiresMatchingConsent(){Assert.isTrue("root approved",AdbUidPolicy.matches(0,0));Assert.isTrue("shell approved",AdbUidPolicy.matches(2000,2000));Assert.isTrue("no elevation",!AdbUidPolicy.matches(2000,0));Assert.isTrue("no reuse after UID change",!AdbUidPolicy.matches(0,2000));Assert.isTrue("disconnected",!AdbUidPolicy.matches(-1,-1));}
}
