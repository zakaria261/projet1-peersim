package dht;

import peersim.core.*;
import peersim.edsim.*;

public class DHTNode implements EDProtocol {

    public long id;
    public Node pred;
    public Node succ;
    
    public java.util.HashMap<Long, Object> dataStore;
    public java.util.HashMap<Long, Object> replicaStore;
    public java.util.ArrayList<Node> longLinks; // pour le piggybacking

    public DHTNode(String prefix) {
        dataStore = new java.util.HashMap<>();
        replicaStore = new java.util.HashMap<>();
        longLinks = new java.util.ArrayList<>();
    }

    public Object clone() {
        DHTNode prot = null;
        try {
            prot = (DHTNode) super.clone();
            prot.dataStore = new java.util.HashMap<>();
            prot.replicaStore = new java.util.HashMap<>();
            prot.longLinks = new java.util.ArrayList<>();
        } catch (CloneNotSupportedException e) {}
        return prot;
    }

    @Override
    public void processEvent(Node node, int pid, Object event) {
        if (event instanceof Message) {
            Message msg = (Message) event;
            
            // Etape 4 : Piggybacking sans tricher.
            // On découvre un nœud éloigné grâce à l'expéditeur du message !
            if (msg.sender != null && msg.sender != node && !longLinks.contains(msg.sender)) {
                if (longLinks.size() < 5) { // On limite la taille pour imiter une Finger Table réduite
                    longLinks.add(msg.sender);
                }
            }
            
            switch (msg.type) {
                case Message.JOIN_REQUEST:
                    handleJoinRequest(node, pid, msg);
                    break;
                case Message.JOIN_ACCEPTED:
                    Node[] neighbors = (Node[]) msg.content;
                    this.pred = neighbors[0];
                    this.succ = neighbors[1];
                    System.out.println("Le noeud " + this.id + " a rejoint l'anneau avec succes ! Pred=" + ((DHTNode)this.pred.getProtocol(pid)).id + ", Succ=" + ((DHTNode)this.succ.getProtocol(pid)).id);
                    break;
                case Message.LEAVE_NOTIFY:
                    System.out.println("\n*** EVENEMENT : Le noeud " + this.id + " quitte le reseau ! ***");
                    leaveNetwork(node, pid);
                    break;
                case Message.UPDATE_PRED:
                    Node oldPred = this.pred;
                    this.pred = (Node) msg.content;
                    // System.out.println("Node " + this.id + " updated Pred from " + ((oldPred==null||oldPred.getProtocol(pid)==null)?"null":((DHTNode)oldPred.getProtocol(pid)).id) + " to " + ((DHTNode)this.pred.getProtocol(pid)).id);
                    break;
                case Message.UPDATE_SUCC:
                    this.succ = (Node) msg.content;
                    break;
                case Message.SEND_DATA:
                case Message.PUT_DATA:
                case Message.GET_DATA:
                    handleRouting(node, pid, msg);
                    break;
                case Message.STORE_REPLICA:
                    this.replicaStore.put(msg.targetId, msg.content);
                    break;
                case Message.GET_REPLY:
                    System.out.println("<<< REPONSE RECUE : Le noeud " + this.id + " a recu la data : [" + msg.content + "] pour la cle ID " + msg.targetId);
                    break;
            }
        }
    }

    private void handleRouting(Node myNode, int pid, Message msg) {
        long targetId = msg.targetId;
        
        // Suis-je le responsable de targetId ?
        // Je suis responsable si targetId est dans (pred.id, my.id]
        boolean iAmResponsible = false;
        
        if (this.pred != null) {
            DHTNode predDHT = (DHTNode) this.pred.getProtocol(pid);
            if (predDHT.id < this.id) {
                if (targetId > predDHT.id && targetId <= this.id) iAmResponsible = true;
            } else { // Wraparound
                if (targetId > predDHT.id || targetId <= this.id) iAmResponsible = true;
            }
        } else {
            iAmResponsible = true; // Je suis seul
        }
        
        if (targetId == this.id || iAmResponsible) {
            handleStorageOrDelivery(pid, msg, myNode);
        } else {
            // Forwarding (Etape 4 : Utilisation des liens longs)
            Node bestNextHop = this.succ;
            long bestDist = distance(this.id, targetId);
            long succDist = distance(((DHTNode)this.succ.getProtocol(pid)).id, targetId);
            
            if (succDist < bestDist) bestDist = succDist;
            
            for (Node link : longLinks) {
                DHTNode linkDHT = (DHTNode) link.getProtocol(pid);
                long dist = distance(linkDHT.id, targetId);
                // Si ce lien long nous rapproche plus de la cible que notre successeur (sans dépasser)
                if (dist < bestDist) {
                    bestDist = dist;
                    bestNextHop = link;
                }
            }
            
            if (bestNextHop != this.succ) {
                System.out.println("  [RACCOURCI] Noeud " + this.id + " -> " + ((DHTNode)bestNextHop.getProtocol(pid)).id);
            } else {
                // Log strictly required by Step 2 instructions
                System.out.println("  [FORWARD] Le noeud " + this.id + " passe au voisin normal " + ((DHTNode)bestNextHop.getProtocol(pid)).id);
            }
            
            EDSimulator.add(1, msg, bestNextHop, pid);
        }
    }
    
    // Calcule la distance logique numériquement (en ignorant le wraparound pour simplifier ici, 
    // ou plutôt en faisant simplement la valeur absolue pour le saut le plus proche).
    private long distance(long id1, long id2) {
        // Dans une DHT pure, on tourne toujours dans un sens, donc (id2 - id1) mod MAX.
        // Ici on simplifie avec la différence absolue pour le routage avancé rapide :
        return Math.abs(id1 - id2);
    }

    private void handleStorageOrDelivery(int pid, Message msg, Node myNode) {
        if (msg.type == Message.SEND_DATA) {
             System.out.println(">>> RECEPTION : Le noeud " + this.id + " a bien recu le message explicite : [" + msg.content + "] envoye par le Noeud " + msg.sender.getID());
        } else if (msg.type == Message.PUT_DATA) {
             System.out.println(">>> RECEPTION (PUT) : Le Noeud " + this.id + " stocke la valeur : [" + msg.content + "] a la cle " + msg.targetId);
             this.dataStore.put(msg.targetId, msg.content);
             
             // Réplication de degré 3 (moi + pred + succ)
             if (this.pred != null && this.pred != myNode) {
                 EDSimulator.add(1, new Message(Message.STORE_REPLICA, msg.content, null, msg.targetId), this.pred, pid);
             }
             if (this.succ != null && this.succ != myNode) {
                 EDSimulator.add(1, new Message(Message.STORE_REPLICA, msg.content, null, msg.targetId), this.succ, pid);
             }
        } else if (msg.type == Message.GET_DATA) {
             System.out.println(">>> LECTURE (GET)   : Le Noeud " + this.id + " s'occupe de la lecture pour la cle " + msg.targetId);
             Object data = this.dataStore.get(msg.targetId);
             if (data == null) {
                 data = this.replicaStore.get(msg.targetId); // Essayer dans le réplica
             }
             
             EDSimulator.add(1, new Message(Message.GET_REPLY, (data != null ? data : "INTROUVABLE"), myNode, msg.targetId), msg.sender, pid); // Retour à l'expéditeur
        }
    }

    private void handleJoinRequest(Node myNode, int pid, Message msg) {
        Node newNode = (Node) msg.content;
        DHTNode newDHT = (DHTNode) newNode.getProtocol(pid);
        
        // Handle case where myNode doesn't have initialized succ (should not happen if starting with 1 active node)
        if (this.succ == null) {
            System.err.println("Error: Node " + this.id + " has no successor!");
            return;
        }

        DHTNode mySuccDHT = (DHTNode) this.succ.getProtocol(pid);

        // Determine if newDHT.id falls between my id and my succ's id (considering wraparound)
        boolean inBetween = false;
        
        if (this.id < mySuccDHT.id) {
            // Normal case: e.g., this.id=10, mySucc.id=20. New node must be in (10, 20)
            if (newDHT.id > this.id && newDHT.id < mySuccDHT.id) {
                inBetween = true;
            }
        } else if (this.id > mySuccDHT.id) {
            // Wraparound case: e.g., this.id=90, mySucc.id=10. New node must be >90 or <10
            if (newDHT.id > this.id || newDHT.id < mySuccDHT.id) {
                inBetween = true;
            }
        } else {
            // this.id == mySuccDHT.id (Only node in the ring)
            inBetween = true;
        }

        if (inBetween) {
            // Node found its place!
            Node oldSucc = this.succ;
            
            // Re-wire
            this.succ = newNode; // I point to new
            
            // Notify new node: "Your pred is ME, your succ is OLD_SUCC"
            EDSimulator.add(1, new Message(Message.JOIN_ACCEPTED, new Node[]{myNode, oldSucc}, myNode), newNode, pid);
            
            // Notify my old succ: "Your pred is NEW"
            EDSimulator.add(1, new Message(Message.UPDATE_PRED, newNode, myNode), oldSucc, pid);
            
        } else {
            // Forward request to my successor
            EDSimulator.add(1, msg, this.succ, pid);
        }
    }

    public void leaveNetwork(Node myNode, int pid) {
        if (this.pred != null && this.succ != null && this.pred != myNode && this.succ != myNode) {
            // Tell my pred that its new succ is my succ
            EDSimulator.add(1, new Message(Message.UPDATE_SUCC, this.succ, myNode), this.pred, pid);
            // Tell my succ that its new pred is my pred
            EDSimulator.add(1, new Message(Message.UPDATE_PRED, this.pred, myNode), this.succ, pid);
        }
        this.pred = null;
        this.succ = null;
    }
}
