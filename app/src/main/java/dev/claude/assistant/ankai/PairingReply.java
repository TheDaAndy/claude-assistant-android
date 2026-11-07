package dev.claude.assistant.ankai;

/** Notification input: normally code only; port + code is a manual fallback without leaving Settings. */
public final class PairingReply {
    public final int port;
    public final String code;
    private PairingReply(int port,String code){this.port=port;this.code=code;}
    public static PairingReply parse(CharSequence value){
        String input=value==null?"":value.toString().trim();
        if(input.matches("[0-9]{6}"))return new PairingReply(0,input);
        if(input.matches("[0-9]{1,5}[ :]+[0-9]{6}")){
            String[] parts=input.split("[ :]+",2);int port=Integer.parseInt(parts[0]);
            if(port>0&&port<=65535)return new PairingReply(port,parts[1]);
        }
        throw new IllegalArgumentException("Sechsstelligen Code eingeben. Alternativ: Port Leerzeichen Code.");
    }
}
