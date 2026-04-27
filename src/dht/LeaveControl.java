package dht;

import peersim.core.*;
import peersim.config.*;

public class LeaveControl implements Control {
    
    private static final String PAR_PROT = "protocol";
    private final int pid;
    
    public LeaveControl(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROT);
    }
    
    public boolean execute() {
        if (Network.size() <= 1) return false;
        
        // On choisit le nœud 5 par exemple pour le faire quitter
        Node victim = Network.get(5);
        DHTNode victimDHT = (DHTNode) victim.getProtocol(pid);
        
        System.out.println("\n*** EVENT: Node " + victimDHT.id + " is leaving the network! ***\n");
        victimDHT.leaveNetwork(victim, pid);
        
        return false;
    }
}
