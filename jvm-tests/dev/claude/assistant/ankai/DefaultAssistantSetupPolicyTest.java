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

    public void testAbgelehnteRollenAnfrageOeffnetDefaultAppEinstellungen() {
        Assert.isTrue("ohne gehaltene Rolle muss die sichtbare Einstellungsseite folgen",
                DefaultAssistantSetupPolicy.shouldOpenSettingsAfterRoleRequest(false));
    }

    public void testErfolgreicheRollenAnfrageOeffnetKeineZweiteSeite() {
        Assert.isTrue("mit gehaltener Rolle ist kein Fallback notwendig",
                !DefaultAssistantSetupPolicy.shouldOpenSettingsAfterRoleRequest(true));
    }
}
