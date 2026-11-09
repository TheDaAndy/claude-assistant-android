package dev.claude.assistant.ankai;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class PersonalDecisionsTest {
    public void testSavedAnswerImmediatelyReplacesCardWithoutWaitingForRefresh() {
        PersonalDashboard old=PersonalDashboard.from(AnkaiJson.parseObject("{\"decisions\":[{\"id\":\"d1\",\"revision\":1,\"kind\":\"question\",\"status\":\"proposed\",\"risk\":1,\"confidence\":90}]}"));
        PersonalDashboard next=old.withResponse(AnkaiJson.parseObject("{\"decision\":{\"id\":\"d1\",\"revision\":2,\"kind\":\"question\",\"status\":\"answered\",\"risk\":1,\"confidence\":90}}"));
        Assert.eq(1,old.openCount());Assert.eq(0,next.openCount());Assert.isTrue("answered leaves inbox",!next.decisions.get(0).isPending());
    }
    public void testDashboardKeepsGoalAgentScoresAndPendingStates() {
        PersonalDashboard dashboard = PersonalDashboard.from(AnkaiJson.parseObject("{\"agents\":[{\"id\":\"a1\",\"name\":\"Zielagent\"}],\"goals\":[{\"id\":\"g1\",\"title\":\"Arzttermine\"}],\"decisions\":[{\"id\":\"decision-one\",\"revision\":2,\"agentId\":\"a1\",\"goalId\":\"g1\",\"kind\":\"strategy\",\"status\":\"proposed\",\"title\":\"Vormittags?\",\"body\":\"Bitte entscheiden\",\"risk\":19.5,\"confidence\":80.5,\"options\":[\"Ja\",\"Nein\"]},{\"id\":\"decision-two\",\"revision\":1,\"kind\":\"action\",\"status\":\"done\",\"risk\":4,\"confidence\":95,\"action\":{\"type\":\"memory_note\",\"payload\":{}}}]}"));
        Assert.eq(1,dashboard.openCount());
        PersonalDecision d=dashboard.decisions.get(0);
        Assert.eq("Zielagent",d.agentName); Assert.eq("Arzttermine",d.goalTitle);
        Assert.eq(19.5,d.risk);Assert.eq(80.5,d.confidence);Assert.isTrue("proposed responds",d.canRespond());
        Assert.eq(2,d.options.size());Assert.isTrue("done cannot execute",!dashboard.decisions.get(1).canRespond());
    }
    public void testExternalCommunicationBannerDoesNotTrustLowScoresOrMissingFlag() {
        PersonalDashboard dashboard=PersonalDashboard.from(AnkaiJson.parseObject("{\"decisions\":[{\"id\":\"d1\",\"revision\":1,\"kind\":\"action\",\"status\":\"proposed\",\"risk\":0,\"confidence\":100,\"external\":false,\"action\":{\"type\":\"web_booking\",\"payload\":{\"provider\":\"Praxis\"}}}]}"));
        Assert.isTrue("booking always external",dashboard.decisions.get(0).external);
        Assert.eq("Praxis",AnkaiJson.string(dashboard.decisions.get(0).payload,"provider"));
    }
    public void testIncompleteOrUnknownDecisionCannotBeApproved() {
        PersonalDashboard dashboard=PersonalDashboard.from(AnkaiJson.parseObject("{\"decisions\":[{\"id\":\"d1\",\"revision\":0,\"status\":\"proposed\"},{\"id\":\"d2\",\"revision\":1,\"status\":\"running\",\"risk\":0,\"confidence\":100}]}"));
        Assert.isTrue("missing version disabled",!dashboard.decisions.get(0).canRespond());
        Assert.isTrue("running disabled",!dashboard.decisions.get(1).canRespond());
    }
    public void testDashboardReadRenewsCookieAndDecisionMutationUsesExactVersionOnce() throws Exception {
        FakeAnkai fake=new FakeAnkai();try {
            fake.rejectedCookieStatus=401;
            fake.respond("/api/auth/session",200,"application/json","{\"user\":{\"username\":\"andy\"}}");
            fake.respond("/api/personal",200,"application/json","{\"decisions\":[]}");
            fake.respond("/api/personal/decisions/d1/respond",200,"application/json","{\"decision\":{\"status\":\"running\"}}");
            AnkaiClient client=new AnkaiClient(new AnkaiEndpoint(fake.baseUrl()),"andy","protected");
            client.useSessionCookie("old=expired");
            Assert.eq(0,client.loadPersonalDashboard().decisions.size());
            fake.rejectedCookieStatus=0;
            Map<String,Object> result=client.respondToPersonalDecision("d1",7,"approve","Nur Donnerstag","Ja");
            Assert.eq("running",AnkaiJson.string(AnkaiJson.object(result,"decision"),"status"));
            Assert.isTrue("version sent",fake.lastBody.contains("\"revision\":7"));
            Assert.isTrue("message sent",fake.lastBody.contains("Nur Donnerstag"));
            Assert.eq(1L,fake.requestLog.stream().filter(s->s.equals("POST /api/personal/decisions/d1/respond")).count());
        }finally{fake.stop();}
    }
    public void testConflictDoesNotRepeatMutationAndSurfacesTheServerMessage() throws Exception {
        FakeAnkai fake=new FakeAnkai();try{
            fake.respond("/api/auth/session",200,"application/json","{\"user\":{\"username\":\"andy\"}}");
            fake.respond("/api/personal/decisions/d1/respond",409,"application/json","{\"error\":\"Vorschlag wurde geändert\"}");
            AnkaiClient client=new AnkaiClient(new AnkaiEndpoint(fake.baseUrl()),"andy","protected");
            try{client.respondToPersonalDecision("d1",1,"approve","","");Assert.fail("conflict expected");}catch(AnkaiApiException e){Assert.isTrue("server error shown",e.getMessage().contains("geändert"));}
            Assert.eq(1L,fake.requestLog.stream().filter(s->s.startsWith("POST ")).count());
        }finally{fake.stop();}
    }
    public void testVoiceFeedbackCarriesRevisionWithoutChatRoutingFields() throws Exception {
        FakeAnkai fake=new FakeAnkai();try{
            fake.respond("/api/auth/session",200,"application/json","{\"user\":{\"username\":\"andy\"}}");
            fake.respond("/api/personal/decisions/d1/voice",200,"application/json","{\"decision\":{\"status\":\"needs_revision\"}}");
            AnkaiClient client=new AnkaiClient(new AnkaiEndpoint(fake.baseUrl()),"andy","protected");
            client.sendPersonalDecisionVoice("d1",3,"antwort.m4a","audio/mp4",new byte[]{1,2,3});
            Assert.isTrue("revision multipart",fake.lastBody.contains("name=\"revision\"\r\n\r\n3"));
            Assert.isTrue("file multipart",fake.lastBody.contains("filename=\"antwort.m4a\""));
            Assert.isTrue("no routing",!fake.lastBody.contains("routeProject")&&!fake.lastBody.contains("sessionId"));
            Assert.eq(1L,fake.requestLog.stream().filter(s->s.startsWith("POST ")).count());
        }finally{fake.stop();}
    }
    public void testRejectsUnsafeDecisionIdBeforeNetworking() throws Exception {
        AnkaiClient client=new AnkaiClient(new AnkaiEndpoint("http://127.0.0.1:1"),"andy","protected");
        try{client.respondToPersonalDecision("../secrets",1,"approve","","");Assert.fail("unsafe id");}catch(IllegalArgumentException expected){}
    }
    private PersonalAttachment attachment(){return new PersonalAttachment(AnkaiJson.parseObject("{\"id\":\"12345678-1234-1234-1234-123456789abc\",\"filename\":\"front.jpg\",\"mimeType\":\"image/jpeg\",\"size\":3,\"sha256\":\"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad\"}"));}
    public void testAttachmentRejectsChangedBytesAndWrongSize()throws Exception{
        PersonalAttachment a=attachment();a.verify("abc".getBytes(StandardCharsets.UTF_8));
        for(String changed:new String[]{"abd","abcd"})try{a.verify(changed.getBytes(StandardCharsets.UTF_8));Assert.fail("changed attachment");}catch(java.io.IOException expected){}
    }
    public void testAttachmentAuthenticatedReadUsesExactRevision()throws Exception{
        FakeAnkai fake=new FakeAnkai();try{
            PersonalAttachment a=attachment();
            fake.respond("/api/personal/decisions/d1/attachments/"+a.id,200,"image/jpeg","abc");
            AnkaiClient client=new AnkaiClient(new AnkaiEndpoint(fake.baseUrl()),"andy","protected");
            Assert.eq(3,client.loadPersonalAttachment("d1",7,a).length);
            Assert.isTrue("exact revision","revision=7".equals(fake.lastQuery));
        }finally{fake.stop();}
    }
    public void testAttachmentConflictAndTypeMismatchStopPreview()throws Exception{
        FakeAnkai fake=new FakeAnkai();try{
            PersonalAttachment a=attachment();AnkaiClient client=new AnkaiClient(new AnkaiEndpoint(fake.baseUrl()),"andy","protected");
            for(int status:new int[]{409,200}){
                fake.respond("/api/personal/decisions/d1/attachments/"+a.id,status,"text/html","abc");
                try{client.loadPersonalAttachment("d1",2,a);Assert.fail("unsafe preview");}catch(java.io.IOException expected){}
            }
        }finally{fake.stop();}
    }
}
