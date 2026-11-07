package dev.claude.assistant.ankai;
public final class AdbRecoveryPolicyTest {
 public void testRequiresEveryLocalGrant(){Assert.isTrue("complete grant",AdbRecoveryPolicy.allowed(30,true,true,true,true,false));for(int missing=0;missing<6;missing++)Assert.isTrue("missing condition must deny",!AdbRecoveryPolicy.allowed(missing==0?29:30,missing!=1,missing!=2,missing!=3,missing!=4,missing==5));}
 public void testBoundedBackoff(){Assert.eq(5000L,AdbRecoveryPolicy.retryMillis(1));Assert.eq(10000L,AdbRecoveryPolicy.retryMillis(2));Assert.eq(300000L,AdbRecoveryPolicy.retryMillis(20));Assert.eq(5000L,AdbRecoveryPolicy.retryMillis(-1));}
}
