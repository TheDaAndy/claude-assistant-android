package dev.claude.assistant.remote;

import android.content.Context;
import android.os.Build;
import android.util.Base64;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** One local-device transport per process. Identity is encrypted at rest, never logged. */
final class WirelessAdbConnection extends AbsAdbConnectionManager {
    static String failureMessage(Throwable failure){
        for(Throwable t=failure;t!=null;t=t.getCause()){
            if(t instanceof javax.net.ssl.SSLException)return "TLS-Verbindung fehlgeschlagen. Kopplung vorhanden; Androids ADB-TLS-Verbindung prüfen.";
            if(t instanceof java.net.ConnectException)return "ADB-Port nicht erreichbar. Aktuellen Verbindungsport der WLAN-Debugging-Hauptseite prüfen.";
            if(t instanceof java.net.SocketTimeoutException||t instanceof java.util.concurrent.TimeoutException)return "ADB-Verbindung hat das Zeitlimit überschritten.";
        }
        return "ADB-Verbindung oder UID-Prüfung fehlgeschlagen ("+failure.getClass().getSimpleName()+"). Kein Befehl ausgeführt.";
    }
    private static volatile WirelessAdbConnection instance;
    private volatile boolean verified;
    private volatile int verifiedUid=-1;
    int uid(){return verified()?verifiedUid:-1;}
    boolean verified(){return verified&&isConnected();}
    void verifyShell()throws Exception {
        verified=false;
        java.util.concurrent.ScheduledExecutorService deadline=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        deadline.schedule(()->{try{disconnect();}catch(java.io.IOException ignored){}},15,TimeUnit.SECONDS);
        try(io.github.muntashirakon.adb.AdbStream stream=openStream("shell: id -u")){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            java.io.InputStream in=new java.io.BufferedInputStream(stream.openInputStream());int b;
            while((b=in.read())!=-1 && b!='\n'){if(out.size()>32)throw new java.io.IOException();out.write(b);}
            String value=out.toString("UTF-8").trim();
            if(!"2000".equals(value)&&!"0".equals(value)){disconnect();throw new java.io.IOException("Unerwartete ADB-UID; Verbindung abgelehnt");}
            verifiedUid=dev.claude.assistant.ankai.AdbUidPolicy.parse(value);
            verified=true;
        }finally{deadline.shutdownNow();}
    }
    @Override public void disconnect()throws java.io.IOException {verified=false;verifiedUid=-1;super.disconnect();}
    private final PrivateKey key;
    private final Certificate certificate;
    static synchronized WirelessAdbConnection get(Context context) throws Exception {
        if(instance==null)instance=new WirelessAdbConnection(context.getApplicationContext());
        return instance;
    }
    static WirelessAdbConnection current(){return instance;}
    private WirelessAdbConnection(Context context)throws Exception {
        setApi(Build.VERSION.SDK_INT);setHostAddress("127.0.0.1");setTimeout(15,TimeUnit.SECONDS);
        EncryptedPrefsSecretStore store=new EncryptedPrefsSecretStore(context);
        String identity=store.get("wireless_adb_identity_v1");
        if(identity==null){
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
            KeyPair pair=generator.generateKeyPair();key=pair.getPrivate();
            X500Name name=new X500Name("CN=Ankai Assistant");long now=System.currentTimeMillis();
            certificate=new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(name,
                new BigInteger(128,new SecureRandom()),new Date(now-86400000L),new Date(now+315360000000L),name,pair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(key)));
            store.put("wireless_adb_identity_v1",Base64.encodeToString(key.getEncoded(),Base64.NO_WRAP)+":"+Base64.encodeToString(certificate.getEncoded(),Base64.NO_WRAP));
        }else{
            String[] parts=identity.split(":",2);
            key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(parts[0],Base64.NO_WRAP)));
            certificate=CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Base64.decode(parts[1],Base64.NO_WRAP)));
        }
    }
    @Override protected PrivateKey getPrivateKey(){return key;}
    @Override protected Certificate getCertificate(){return certificate;}
    @Override protected String getDeviceName(){return "Ankai Assistant";}
}
