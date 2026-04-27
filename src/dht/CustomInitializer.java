package dht;

import peersim.core.*;
import peersim.config.*;
import peersim.edsim.*;

public class CustomInitializer implements Control {
    
    private static final String PAR_PROT = "protocol";
    private final int pid;
    
    public CustomInitializer(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_PROT);
    }
    
    public boolean execute() {
        if (Network.size() == 0) return false;
        
        // --- Lancement du noeud contact (Noeud 0) ---
        Node n0 = Network.get(0);
        DHTNode dht0 = (DHTNode) n0.getProtocol(pid);
        dht0.id = Math.abs(CommonState.r.nextLong());
        dht0.pred = n0;
        dht0.succ = n0;
        System.out.println("Noeud 0 (Contact) initilise. ID=" + dht0.id);
        
        // --- Les autres noeuds rejoignent tour à tour ---
        for (int i = 1; i < Network.size(); i++) {
            Node newNode = Network.get(i);
            DHTNode newDHT = (DHTNode) newNode.getProtocol(pid);
            newDHT.id = Math.abs(CommonState.r.nextLong());
            
            System.out.println(" -- Demande de Join lancee pour l'ID " + newDHT.id + " via le noeud de contact");
            Message joinMsg = new Message(Message.JOIN_REQUEST, newNode, n0, newDHT.id);
            // On decale dans le temps pour simuler un vrai systeme
            EDSimulator.add(i * 10, joinMsg, n0, pid);
        }
        
        // Les tests de base pour verifier que ca marche
        // 1) Test de plantage d'un noeud
        EDSimulator.add(300, new Message(Message.LEAVE_NOTIFY, null, Network.get(5)), Network.get(5), pid);
        
        // 2) Un ptit test de routage
        Node sender = Network.get(2);
        Node targetRouting = Network.get(7);
        EDSimulator.add(400, new Message(Message.SEND_DATA, "Salut depuis le routeur interne", sender, ((DHTNode)targetRouting.getProtocol(pid)).id), sender, pid);
        
        // 3) On teste le stockage et sa recuperation
        Node clientNode = Network.get(1);
        long dataKey = Math.abs(CommonState.r.nextLong());
        EDSimulator.add(500, new Message(Message.PUT_DATA, "Mon super projet M1", clientNode, dataKey), clientNode, pid);
        
        // C'est censé récupérer "Mon super projet M1"
        EDSimulator.add(600, new Message(Message.GET_DATA, null, clientNode, dataKey), clientNode, pid);
        
        return false;
    }
}
