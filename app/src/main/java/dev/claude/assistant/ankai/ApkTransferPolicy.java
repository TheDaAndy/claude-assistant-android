package dev.claude.assistant.ankai;
/** Integrity boundary for server-staged single APKs; no arbitrary URLs or shell. */
public final class ApkTransferPolicy {
 public static final long MAX_BYTES=100L*1024*1024;
 public static void validate(String id,long size,String sha){
  if(id==null||!id.matches("[0-9a-f-]{36}")||size<4||size>MAX_BYTES||sha==null||!sha.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Ungültige APK-Metadaten.");
 }
 public static void verify(long expected,long received,String sha,String actual){
  if(expected!=received||!sha.equals(actual))throw new IllegalArgumentException("APK-Größe oder SHA256 stimmt nicht.");
 }
}
