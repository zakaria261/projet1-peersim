package dht;

import peersim.core.*;
import peersim.config.*;
import peersim.edsim.*;

public class StorageControl implements Control {
    
    private static final String PAR_PROT = "protocol";
    private final int pid;
    private boolean putDone = false;
    private long dataKey;
    private Node clientNode;
    
    public StorageControl(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROT);
        dataKey = Math.abs(CommonState.r.nextLong());
    }
    
    private int state = 0;

    public boolean execute() {
        if (Network.size() < 2) return false;
        
        if (state == 0) {
            // First tick at step 150: wait or just do PUT now since it's step 150
            clientNode = Network.get(1); // Nœud aléatoire qui initie l'action
            DHTNode clientDHT = (DHTNode) clientNode.getProtocol(pid);
            
            System.out.println("\n*** EVENT: Node " + clientDHT.id + " -> PUT Request for Key " + dataKey + " ***");
            Message putMsg = new Message(Message.PUT_DATA, "My Valuable Data", clientNode, dataKey);
            EDSimulator.add(0, putMsg, clientNode, pid);
            state = 1;
        } else if (state == 1 && clientNode != null) {
            // Second tick at step 300: do GET
            DHTNode clientDHT = (DHTNode) clientNode.getProtocol(pid);
            System.out.println("\n*** EVENT: Node " + clientDHT.id + " -> GET Request for Key " + dataKey + " ***");
            Message getMsg = new Message(Message.GET_DATA, null, clientNode, dataKey);
            EDSimulator.add(0, getMsg, clientNode, pid);
            state = 2; // Prevent running GET multiple times
        }
        
        return false;
    }
}
