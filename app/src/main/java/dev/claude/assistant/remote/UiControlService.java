package dev.claude.assistant.remote;

import android.accessibilityservice.*;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.os.*;
import android.util.Base64;
import android.view.Display;
import android.view.accessibility.*;
import org.json.*;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.*;

/** Android-bound service: rootless UI access only after local opt-in. */
public final class UiControlService extends AccessibilityService {
    private static volatile UiControlService current;
    private final Handler main=new Handler(Looper.getMainLooper());
    public static boolean ready(){return current!=null;}
    @Override protected void onServiceConnected(){current=this;resumeChannel();DevicePresenceMonitor.changed();}
    private void resumeChannel(){
        DeviceLinkStore s=new DeviceLinkStore(this);
        if(s.id()!=null&&s.uiGranted()&&s.uiResume()&&getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()){
            try{s.enabled(true);startForegroundService(new Intent(this,DeviceChannelService.class));}catch(Exception denied){s.status("Automatischer Start blockiert · App öffnen");}
        }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){if(event!=null)DevicePresenceMonitor.onEvent(event.getEventType());}
    @Override public void onInterrupt(){}
    @Override public boolean onUnbind(Intent i){if(current==this)current=null;DevicePresenceMonitor.changed();new DeviceLinkStore(this).status("Bedienungshilfe getrennt");return super.onUnbind(i);}
    @Override public void onDestroy(){if(current==this)current=null;DevicePresenceMonitor.changed();super.onDestroy();}
    public static JSONObject execute(String action,JSONObject params)throws Exception{
        UiControlService s=current;if(s==null)return error("Bedienungshilfe nicht verbunden.");
        CompletableFuture<JSONObject> result=new CompletableFuture<>();
        s.main.post(()->{try{s.perform(action,params,result);}catch(Exception e){result.complete(error("UI-Aktion fehlgeschlagen: "+e.getMessage()));}});
        try{return result.get(8,TimeUnit.SECONDS);}catch(TimeoutException e){result.cancel(false);return error("UI-Aktion abgelaufen; Ergebnis unbekannt. Nicht automatisch wiederholen.");}
    }
    private static JSONObject error(String message){try{return new JSONObject().put("status","error").put("data",message);}catch(Exception e){throw new IllegalStateException(e);}}
    private static JSONObject ok(Object data)throws Exception{return new JSONObject().put("status","ok").put("data",data);}
    private boolean allowed(){DeviceLinkStore s=new DeviceLinkStore(this);return s.id()!=null&&s.enabled()&&s.uiGranted()&&current==this;}
    private void perform(String action,JSONObject p,CompletableFuture<JSONObject> future)throws Exception{
        if(future.isDone())return;
        if(!allowed()){future.complete(error("Gerätekanal pausiert oder UI-Freigabe entzogen."));return;}
        if(getSystemService(KeyguardManager.class).isKeyguardLocked()){future.complete(error("Handy gesperrt · bitte lokal entsperren."));return;}
        if(!getSystemService(PowerManager.class).isInteractive()){future.complete(error("Display aus · bitte einschalten und Handy zum Testen freigeben."));return;}
        if("ui_screenshot".equals(action)){screenshot(future);return;}
        AccessibilityNodeInfo root=getRootInActiveWindow();
        try{
            String pkg=root==null?"":String.valueOf(root.getPackageName());
            if("ui_snapshot".equals(action)){
                JSONArray nodes=new JSONArray();if(root!=null)collect(root,nodes,0,new int[]{0});
                android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
                JSONObject data=new JSONObject().put("packageName",pkg).put("width",dm.widthPixels).put("height",dm.heightPixels).put("nodes",nodes).put("limited",true);while(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>14000&&nodes.length()>0)nodes.remove(nodes.length()-1);future.complete(ok(data));return;
            }
            if("ui_launch".equals(action)){
                String target=p.getString("packageName");
                if(target.equals(getPackageName()))throw new IllegalArgumentException("Eigene Freigabeoberfläche nur lokal öffnen.");
                Intent launch=getPackageManager().getLaunchIntentForPackage(target);if(launch==null)throw new IllegalArgumentException("Keine startbare App gefunden.");
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));future.complete(ok("Start angefordert; Ansicht anschließend prüfen."));return;
            }
            if("ui_global".equals(action)){
                String t=p.getString("target");int code="home".equals(t)?GLOBAL_ACTION_HOME:"back".equals(t)?GLOBAL_ACTION_BACK:"recents".equals(t)?GLOBAL_ACTION_RECENTS:"notifications".equals(t)?GLOBAL_ACTION_NOTIFICATIONS:0;
                future.complete(code!=0&&performGlobalAction(code)?ok("Systemaktion ausgeführt; Ansicht prüfen."):error("Systemaktion abgelehnt."));return;
            }
            if(!pkg.equals(p.optString("packageName")))throw new IllegalArgumentException("Aktive App geändert; Bildschirm neu lesen.");
            if(pkg.equals(getPackageName())||pkg.contains("permissioncontroller")||pkg.contains("packageinstaller"))throw new IllegalArgumentException("Freigabe-/Installationsdialog nur lokal bestätigen.");
            if("ui_text".equals(action)){
                AccessibilityNodeInfo focused=findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                try{if(focused==null||!pkg.contentEquals(focused.getPackageName()==null?"":focused.getPackageName())||focused.isPassword()||!focused.isEditable())throw new IllegalArgumentException("Kein freigegebenes Textfeld fokussiert.");
                    Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,p.getString("text"));
                    future.complete(focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)?ok("Text gesetzt."):error("Textfeld lehnt Eingabe ab."));
                }finally{if(focused!=null)focused.recycle();}return;
            }
            if(!"ui_tap".equals(action)&&!"ui_swipe".equals(action))throw new IllegalArgumentException("Unbekannte UI-Aktion.");
            android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
            int x=p.getInt("x"),y=p.getInt("y"),ex=p.optInt("endX",x),ey=p.optInt("endY",y);
            if(x<0||y<0||ex<0||ey<0||x>=dm.widthPixels||ex>=dm.widthPixels||y>=dm.heightPixels||ey>=dm.heightPixels)throw new IllegalArgumentException("Koordinaten außerhalb des Displays.");
            Path path=new Path();path.moveTo(x,y);boolean swipe="ui_swipe".equals(action);if(swipe)path.lineTo(ex,ey);
            int duration=swipe?p.getInt("durationMs"):80;if(duration<50||duration>2000)throw new IllegalArgumentException("Ungültige Wischdauer.");
            GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
            if(!dispatchGesture(g,new GestureResultCallback(){@Override public void onCompleted(GestureDescription g){try{future.complete(allowed()?ok("Geste ausgeführt; Ansicht prüfen."):error("Während Ausführung pausiert; Ergebnis prüfen."));}catch(Exception e){future.complete(error("Ergebnisfehler."));}}@Override public void onCancelled(GestureDescription g){future.complete(error("Geste abgebrochen."));}},main))future.complete(error("Geste abgelehnt."));
        }finally{if(root!=null)root.recycle();}
    }
    private void collect(AccessibilityNodeInfo n,JSONArray out,int depth,int[] visits)throws Exception{
        if(depth>15||++visits[0]>180||out.length()>=32)return;
        if(n.isVisibleToUser()){
            Rect r=new Rect();n.getBoundsInScreen(r);JSONObject j=new JSONObject().put("bounds",new JSONArray(new int[]{r.left,r.top,r.right,r.bottom})).put("clickable",n.isClickable()).put("editable",n.isEditable()).put("password",n.isPassword());
            if(!n.isPassword()){j.put("text",shortText(n.getText())).put("description",shortText(n.getContentDescription()));}
            j.put("viewId",shortText(n.getViewIdResourceName()));out.put(j);
        }
        for(int i=0;i<n.getChildCount()&&out.length()<32;i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)try{collect(c,out,depth+1,visits);}finally{c.recycle();}}
    }
    private String shortText(CharSequence t){return t==null?"":t.toString().substring(0,Math.min(100,t.length()));}
    private void screenshot(CompletableFuture<JSONObject> future){
        if(Build.VERSION.SDK_INT<30){future.complete(error("Screenshot braucht Android11+."));return;}
        AccessibilityNodeInfo root=getRootInActiveWindow();final int windowId=root==null?-1:root.getWindowId();final Rect bounds=new Rect();if(root!=null){root.getBoundsInScreen(bounds);root.recycle();}
        final boolean windowCapture=Build.VERSION.SDK_INT>=34&&windowId>=0;
        TakeScreenshotCallback callback=new TakeScreenshotCallback(){
            @Override public void onFailure(int code){future.complete(error("Screenshot abgelehnt/geschützt ("+code+")."));}
            @Override public void onSuccess(ScreenshotResult result){
                try(HardwareBuffer buffer=result.getHardwareBuffer()){
                    if(future.isDone()||!allowed()){future.complete(error("UI-Freigabe entzogen."));return;}
                    Bitmap hardware=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());if(hardware==null)throw new IllegalStateException("Kein Bild");
                    Bitmap copy=hardware.copy(Bitmap.Config.ARGB_8888,false);hardware.recycle();
                    int w=copy.getWidth(),h=copy.getHeight();float scale=Math.min(1f,720f/Math.max(w,h));
                    Bitmap small=Bitmap.createScaledBitmap(copy,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,60,bytes);
                    if(bytes.size()>131072){bytes.reset();small.compress(Bitmap.CompressFormat.JPEG,25,bytes);}
                    if(bytes.size()>131072)throw new IllegalStateException("Screenshot zu groß");
                    JSONObject data=new JSONObject().put("mimeType","image/jpeg").put("base64",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)).put("width",small.getWidth()).put("height",small.getHeight()).put("displayWidth",getResources().getDisplayMetrics().widthPixels).put("displayHeight",getResources().getDisplayMetrics().heightPixels).put("capture",windowCapture?"active_window":"display").put("windowBounds",new JSONArray(new int[]{bounds.left,bounds.top,bounds.right,bounds.bottom}));
                    if(small!=copy)small.recycle();copy.recycle();future.complete(ok(data));
                }catch(Exception e){future.complete(error("Screenshot nicht verfügbar."));}
            }
        };
        if(windowCapture)takeScreenshotOfWindow(windowId,getMainExecutor(),callback);else takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),callback);
    }
}
