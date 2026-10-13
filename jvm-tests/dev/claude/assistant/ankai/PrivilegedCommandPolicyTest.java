package dev.claude.assistant.ankai;
public final class PrivilegedCommandPolicyTest {
    public void testAcceptsBoundedCommand(){PrivilegedCommandPolicy.validate("id; getprop ro.product.model",15);}
    public void testRejectsEmptyAndNul(){for(String s:new String[]{null," ","a\0b"})reject(s,15);}
    public void testCountsUtf8Bytes(){reject("ä".repeat(4097),15);}
    public void testRejectsUnboundedRuntime(){reject("id",0);reject("id",21);}
    private void reject(String s,int timeout){try{PrivilegedCommandPolicy.validate(s,timeout);Assert.fail("unbounded command accepted");}catch(IllegalArgumentException expected){}}
}
