package dev.claude.assistant.ankai;

public class DefaultAssistantSetupPolicyTest {
    public void testAndroid10MitVerfuegbarerRolleNutztSystemdialog() {
        Assert.isTrue("Assistant-Rolle soll direkt angefragt werden",
                DefaultAssistantSetupPolicy.shouldRequestAssistantRole(29, true));
    }

    public void testAltesAndroidFaelltAufEinstellungenZurueck() {
        Assert.isTrue("vor Android 10 gibt es keinen RoleManager-Pfad",
                !DefaultAssistantSetupPolicy.shouldRequestAssistantRole(28, true));
    }

    public void testNichtVerfuegbareRolleFaelltAufEinstellungenZurueck() {
        Assert.isTrue("nicht verfuegbare Rolle braucht den Einstellungs-Fallback",
                !DefaultAssistantSetupPolicy.shouldRequestAssistantRole(34, false));
    }
}
