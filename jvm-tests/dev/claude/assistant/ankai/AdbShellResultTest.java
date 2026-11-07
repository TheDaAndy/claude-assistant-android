package dev.claude.assistant.ankai;
import java.io.*;
import java.util.Arrays;
public final class AdbShellResultTest {
    private byte[] frame(int id,byte[] data)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(id);for(int i=0;i<4;i++)b.write(data.length>>>(8*i));b.write(data);return b.toByteArray();}
    private byte[] join(byte[]... frames)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();for(byte[] f:frames)b.write(f);return b.toByteArray();}
    public void testSeparatesStreamsAndExit()throws Exception{
        byte[] bytes=join(frame(1,"output".getBytes()),frame(2,"failure".getBytes()),frame(3,new byte[]{7}));
        InputStream fragmented=new FilterInputStream(new ByteArrayInputStream(bytes)){@Override public int read(byte[] b,int o,int l)throws IOException{return super.read(b,o,Math.min(l,1));}};
        AdbShellResult r=AdbShellResult.read(fragmented);Assert.eq("output",r.stdout);Assert.eq("failure",r.stderr);Assert.eq(7,r.exitCode);
    }
    public void testCapsAndDrainsOutput()throws Exception{
        byte[] large=new byte[9000];Arrays.fill(large,(byte)'x');AdbShellResult r=AdbShellResult.read(new ByteArrayInputStream(join(frame(1,large),frame(2,large),frame(3,new byte[]{0}))));
        Assert.eq(2048,r.stdout.length());Assert.eq(2048,r.stderr.length());Assert.isTrue("output capped",r.truncated);Assert.eq(0,r.exitCode);
    }
    public void testRejectsUncertainAndMalformedFrames()throws Exception{
        for(byte[] bad:new byte[][]{new byte[0],frame(1,"no exit".getBytes()),new byte[]{1,4,0,0,0,1},frame(3,new byte[]{0,0}),new byte[]{1,-1,-1,-1,127}}){
            try{AdbShellResult.read(new ByteArrayInputStream(bad));Assert.fail("malformed frame accepted");}catch(IOException expected){}
        }
    }
}
