package dht;

import peersim.core.Node;

public class Message {
    public final int type;
    public final Object content;
    public final Node sender;
    public final long targetId; // Added for routing

    public static final int JOIN_REQUEST = 0;
    public static final int JOIN_ACCEPTED  = 1;
    public static final int UPDATE_PRED  = 2;
    public static final int UPDATE_SUCC  = 3;
    public static final int LEAVE_NOTIFY = 10;
    
    // Pour l'étape 2
    public static final int SEND_DATA = 4;
    public static final int DELIVER_DATA = 5;
    
    // Pour l'étape 3 (Stockage)
    public static final int PUT_DATA = 6;
    public static final int GET_DATA = 7;
    public static final int GET_REPLY = 8;
    public static final int STORE_REPLICA = 9;

    public Message(int type, Object content, Node sender) {
        this(type, content, sender, -1);
    }
    
    public Message(int type, Object content, Node sender, long targetId) {
        this.type = type;
        this.content = content;
        this.sender = sender;
        this.targetId = targetId;
    }
}
