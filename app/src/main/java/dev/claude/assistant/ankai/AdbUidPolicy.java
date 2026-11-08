package dev.claude.assistant.ankai;
/** Preserve actual adbd identity; accepting a transport never grants remote command consent. */
public final class AdbUidPolicy {
 public static int parse(String value){if("0".equals(value))return 0;if("2000".equals(value))return 2000;throw new IllegalArgumentException("Unsupported ADB UID");}
 public static boolean matches(int approved,int current){return (current==0||current==2000)&&approved==current;}
}
