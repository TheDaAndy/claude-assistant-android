package dev.claude.assistant.ankai;
public final class DebugAutostartPolicyTest {
 public void testOnlyNewBootWithAllRequirements(){Assert.isTrue("new boot",DebugAutostartPolicy.allowed(true,true,true,false,7,6));for(int i=0;i<6;i++)Assert.isTrue("missing requirement",!DebugAutostartPolicy.allowed(i!=0,i!=1,i!=2,i==3,i==4?-1:7,i==5?7:6));}
 public void testDoesNotOverrideManualDisableSameBoot(){Assert.isTrue("one attempt only",!DebugAutostartPolicy.allowed(true,true,true,false,7,7));}
}
