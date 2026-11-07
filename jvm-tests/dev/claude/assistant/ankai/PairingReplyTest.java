package dev.claude.assistant.ankai;
public final class PairingReplyTest {
 public void testKeepsLeadingZeros(){PairingReply r=PairingReply.parse(" 001234 ");Assert.eq("001234",r.code);Assert.eq(0,r.port);}
 public void testAcceptsManualPortWithoutSwitchingApps(){for(String s:new String[]{"45678 001234","45678:001234","45678   001234"}){PairingReply r=PairingReply.parse(s);Assert.eq(45678,r.port);Assert.eq("001234",r.code);}}
 public void testRejectsInvalidReplies(){for(String s:new String[]{null,"","12345","1234567","abcdef","0 123456","65536 123456","12 12345","123456 123456","host:12345 123456","12 123456 trailing"}){try{PairingReply.parse(s);Assert.fail("invalid input accepted");}catch(IllegalArgumentException expected){}}}
}
