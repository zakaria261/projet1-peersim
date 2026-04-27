package dht;

import peersim.core.*;
import peersim.config.*;

public class RingObserver implements Control {
    
    private static final String PAR_PROT = "protocol";
    private final int pid;
    
    public RingObserver(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROT);
    }
    
    public boolean execute() {
        System.out.println("\n--- Etat de l'anneau ---");
        Node startNode = Network.get(0);
        DHTNode startDHT = (DHTNode) startNode.getProtocol(pid);
        DHTNode curr = startDHT;
        int count = 0;
        
        do {
            if (curr.succ == null) {
               System.out.println("Anneau casse au noeud " + curr.id + " (successeur introuvable)");
               break;
            }
            long succId = ((DHTNode)curr.succ.getProtocol(pid)).id;
            long predId = curr.pred == null ? -1 : ((DHTNode)curr.pred.getProtocol(pid)).id;
            System.out.println("Noeud " + curr.id + " -> Pred: " + predId + " | Succ: " + succId);
            
            curr = (DHTNode) curr.succ.getProtocol(pid);
            count++;
            if (count > Network.size() * 2) {
                System.out.println("Boucle infinie detectee ! (Count > Network Size)");
                break;
            }
        } while (curr.id != startDHT.id && count < Network.size() * 2);
        
        System.out.println("Total des noeuds valides observes : " + count + " (Taille du reseau prevue = " + Network.size() + ")");
        System.out.println("-------------------------\n");
        return false;
    }
}
