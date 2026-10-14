package dev.claude.assistant.ankai;
public final class RecentInteractionTest {
 public void testUnknownUntilAnInteractionIsObserved(){RecentInteraction r=new RecentInteraction();Assert.eq(null,r.age(100,true));r.record(100);Assert.eq(0L,r.age(100,true));Assert.eq(31000L,r.age(31100,true));}
 public void testDisabledAndClockRestartAreUnknown(){RecentInteraction r=new RecentInteraction();r.record(100);Assert.eq(null,r.age(101,false));Assert.eq(null,r.age(99,true));}
 public void testAgesAreBoundedAndInvalidEventsIgnored(){RecentInteraction r=new RecentInteraction();r.record(-1);Assert.eq(null,r.age(100,true));r.record(1);Assert.eq(86400000L,r.age(999999999,true));}
}
