package dev.claude.assistant.ankai;
/** Only one enable per boot; explicit disabling afterwards must remain effective. */
public final class DebugAutostartPolicy {
 public static boolean allowed(boolean consent,boolean permission,boolean wifi,boolean debugging,int boot,int lastBoot){
  return consent&&permission&&wifi&&!debugging&&boot>=0&&boot!=lastBoot;
 }
}
