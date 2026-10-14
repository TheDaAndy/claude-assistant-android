package dev.claude.assistant.ankai;
public final class ApkTransferPolicyTest {
 public void testValidMetadataAndHash(){ApkTransferPolicy.validate("12345678-1234-1234-1234-123456789abc",4,"a".repeat(64));ApkTransferPolicy.verify(4,4,"abc","abc");}
 public void testRejectsPathHashAndSize(){for(String id:new String[]{"../secret","https://x",null})try{ApkTransferPolicy.validate(id,4,"a".repeat(64));Assert.fail("invalid id");}catch(IllegalArgumentException ok){}try{ApkTransferPolicy.validate("12345678-1234-1234-1234-123456789abc",ApkTransferPolicy.MAX_BYTES+1,"a".repeat(64));Assert.fail("size");}catch(IllegalArgumentException ok){}try{ApkTransferPolicy.validate("12345678-1234-1234-1234-123456789abc",4,"bad");Assert.fail("hash");}catch(IllegalArgumentException ok){}}
 public void testTruncationAndTamperingFail(){try{ApkTransferPolicy.verify(4,3,"abc","abc");Assert.fail("size");}catch(IllegalArgumentException ok){}try{ApkTransferPolicy.verify(4,4,"abc","def");Assert.fail("hash");}catch(IllegalArgumentException ok){}}
}
