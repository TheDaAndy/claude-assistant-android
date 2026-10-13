package org.ankai.uifixture;
import android.app.Activity;import android.os.Bundle;import android.widget.*;import android.text.InputType;import android.view.WindowManager;
public final class MainActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);ScrollView scroll=new ScrollView(this);LinearLayout l=new LinearLayout(this);l.setPadding(20,20,20,20);l.setOrientation(1);scroll.addView(l);setContentView(scroll);
 TextView title=new TextView(this);title.setText("Ankaï UI test fixture");title.setTextSize(24);l.addView(title);
 EditText text=new EditText(this);text.setContentDescription("Plain test field");text.setHint("Plain test field");l.addView(text);
 EditText password=new EditText(this);password.setContentDescription("Password test field");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setText("fixture-secret");l.addView(password);
 TextView count=new TextView(this);count.setText("Counter 0");l.addView(count);Button b=new Button(this);b.setText("Increment counter");int[] n={0};b.setOnClickListener(v->count.setText("Counter "+(++n[0])));l.addView(b);
 Button secure=new Button(this);secure.setText("Protect screenshot");secure.setOnClickListener(v->getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE));l.addView(secure);
 for(int i=0;i<40;i++){TextView line=new TextView(this);line.setText("Scroll row "+i);line.setPadding(0,12,0,12);l.addView(line);}
 }
}
