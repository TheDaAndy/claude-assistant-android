"""Build a separate instrumentation APK with the existing debug identity."""
from pathlib import Path
import os, subprocess, zipfile
src=Path(__file__).resolve().parent; root=src.parents[1]; sdk=Path(os.environ.get('ANDROID_HOME',Path.home()/'Android/Sdk')); bt=sdk/'build-tools/34.0.0'; out=Path(os.environ['ANKAI_QA_OUTPUT']); out.mkdir(parents=True,exist_ok=True)
classes=out/'classes'; classes.mkdir(exist_ok=True); dex=out/'dex';dex.mkdir(exist_ok=True); jar=sdk/'platforms/android-34/android.jar'
def run(*args,**kwargs):subprocess.run([str(x) for x in args],check=True,**kwargs)
run('javac','--release','8','-classpath',str(jar)+':'+str(root/'app/build/intermediates/javac/debug/classes'),'-d',classes,src/'PresenceInstrumentation.java')
run(bt/'d8','--lib',jar,'--output',dex,*classes.rglob('*.class'))
run(bt/'aapt','package','-f','-M',src/'AndroidManifest.xml','-I',jar,'-F',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:z.write(dex/'classes.dex','classes.dex')
run(bt/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk')
run(bt/'apksigner','sign','--ks',Path.home()/'.android/debug.keystore','--ks-pass','stdin','--out',out/'fixture.apk',out/'aligned.apk',input=(os.environ['ANKAI_QA_SIGNING_PASS']+'\n').encode())
print('Separate presence instrumentation built')
