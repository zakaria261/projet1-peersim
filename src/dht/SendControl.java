package dht;

import peersim.core.*;
import peersim.config.*;
import peersim.edsim.*;

public class SendControl implements Control {
    
    private static final String PAR_PROT = "protocol";
    private final int pid;
    
    public SendControl(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROT);
    }
    
    public boolean execute() {
        if (Network.size() < 2) return false;
        
        // On choisit deux nœuds au hasard, par exemple index 2 et index 7
        Node sender = Network.get(2);
        Node target = Network.get(7);
        
        DHTNode senderDHT = (DHTNode) sender.getProtocol(pid);
        DHTNode targetDHT = (DHTNode) target.getProtocol(pid);
        
        System.out.println("\n*** EVENT: Node " + senderDHT.id + " is sending a message to ID " + targetDHT.id + " ***");
        
        Message msg = new Message(Message.SEND_DATA, "Hello from " + senderDHT.id, sender, targetDHT.id);
        
        // On l'envoie à soi-même pour déclencher le routage
        EDSimulator.add(0, msg, sender, pid);
        
        return false;
    }
}
