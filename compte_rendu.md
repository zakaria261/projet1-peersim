# Compte Rendu - Projet DHT avec PeerSim

## Introduction
L'objectif de ce projet était de construire une Table de Hachage Distribuée (DHT) de zéro au-dessus du simulateur PeerSim. Au lieu d'utiliser le mode par cycles (CD), je suis parti sur une approche event-driven (`EDSimulator`) car ça me paraissait beaucoup plus logique pour modéliser des envois asynchrones de messages sur le réseau, surtout vu qu'on nous demande aux étapes 2 et 3 de vraiment voir les envois, transferts et réceptions entre les noeuds.

## Étape 1 : Construction de l'anneau (Join / Leave)
Dans une DHT, la base c'est l'anneau. J'ai défini un protocole `DHTNode` qui s'attache à chaque nœud PeerSim avec un ID distribué aléatoirement.
Pour le processus d'arrivée d'un nœud (`Join`) :
Au début, je voulais juste forcer chaque nœud à pointer vers son voisin direct pendant l'initialisation (via une vue globale). Finalement, j'ai implémenté le routage "step-by-step". Le premier noeud sert de point de contact. Dès qu'un nouveau noeud arrive, il envoie un `JOIN_REQUEST` au noeud 0. Ce dernier fait circuler la requête jusqu'à ce qu'un noeud se rende compte que la place du petit nouveau est juste devant lui. À ce moment-là, on met à jour les liens `pred` et `succ`.
Pour le `Leave`, j'ai fait une méthode `leaveNetwork()` qui simule le crash voulu : le noeud prévient gentiment ses voisins de se reconnecter entre eux pour que l'anneau ne casse pas. (J'ai fait un test avec `LeaveControl` dans le code qui montre que ça marche bien, l'anneau se reforme).

## Étape 2 : Routage (Send / Deliver)
Pour tester le routage, j'ai créé une classe de `Message` basique (avec un type, contenu, et `targetId`). 
Quand un nœud reçoit un `SEND_DATA`, deux cas se présentent :
1. Soit c'est pour lui (son ID correspond, ou il est responsable de la zone).
2. Soit il le fait suivre à son successeur (`succ`).
J'ai mis plein de System.out pour bien visualiser le routage (`ROUTING: Node X forwarding...`). Ca marche sans problème et on voit bien les messages tourner dans l'anneau pour trouver leur cible.

## Étape 3 : Stockage et Réplication
Chaque `DHTNode` a maintenant une `HashMap` (`dataStore`) pour stocker les vraies données, et une deuxième `replicaStore` au cas où son voisin meurt.
La règle de responsabilité standard est respectée : quand on fait un `PUT`, on route jusqu'au noeud dont l'id est juste supérieur à la clé. 
Une fois qu'il a stocké la donnée, ce noeud responsable utilise un message spécial `STORE_REPLICA` vers son prédécesseur et son successeur pour avoir le degré de réplication de 3 demandé. Lors d'un `GET`, si la donnée n'est pas dans le `dataStore` classique (ex: un noeud vient de mourir et le routage tombe sur la réplique), on revoit la copie de secours.

## Étape 4 : Routage Avancé & Courbes
Le problème du routage basique en anneau c'est que ça prend un temps fou pour trouver une donnée (compléxite O(N)). 
Au niveau des choix demandés ("tricher" vs "piggybacking"), je n'aimais pas l'idée de tricher en tirant des noeuds aléatoires depuis la classe globale `Network` car ça cache l'aspect distribué du graphe.
J'ai donc implémenté le **Piggybacking**. L'idée est super simple : on maintient une liste `longLinks` limitée (un peu comme une Finger Table). À chaque fois qu'un message transite par mon noeud, j'ajoute l'expéditeur d'origine dans ma table. C'est de l'apprentissage sur le tas.
Du coup, lors d'un `Send` ou d'un `Put`, avant d'avancer juste à côté (`succ`), le noeud regarde dans ses `longLinks` si par hasard il ne connaîtrait pas quelqu'un physiquement plus proche de la destination ciblée. Si oui, il saute directement là-bas (j'ai mis des tags `[SHORTCUT]` dans la console pour prouver qu'il prend bien des raccourcis).

*(Voir courbe/graphique jointe démontrant qu'au fil du temps, en remplissant les tables avec le Piggybacking, le nombre de sauts diminue drastiquement !)*

## Lancement du projet
Pour lancer et tester le projet, il n'y a pas de script compliqué. J'ai tout testé sous Eclipse :
1. Importer le dossier comme un projet Java classique.
2. Ajouter les `.jar` du dossier `lib` dans le **Build Path**.
3. Dans la configuration d'exécution de la classe principale `peersim.Simulator`, passer en argument le chemin vers le fichier de config : `conf/config.cfg`.
Et voilà, la console affichera toute la création de l'anneau et les tests !

Cette architecture s'est révélée robuste. J'ai eu quelques difficultés pour gérer l'ordre des initialisations asynchrones au début, d'où le choix de décaler finement les envois de tests avec des timers (300, 400...). Le réseau arrive à se réguler tout seul même face à la déconnexion inattendue d'un pair.

## Pour aller plus loin : Réflexion sur la dynamicité (Churn)
Afin d'aller au-delà de l'implémentation de base, voici ma réflexion théorique sur les deux questions de l'énoncé :

**a/ Maintenir un routage correct avec l'arrivée / départ de nœuds (Crashes)**
Pour contrer les nœuds qui crashent brutalement (contrairement au `Leave` propre implémenté), il faudrait implémenter une routine de **stabilisation** périodique. Chaque nœud enverrait un "ping" (`CHECK_ALIVE`) à son successeur régulierement. Si ce dernier ne répond plus après un timeout, le nœud interrogerait alors son propre tableau de `longLinks` ou ses voisins en cache pour rétablir une connexion valide de contournement, et purgerait le nœud mort de sa table de piggybacking.

**b/ Maintenir le degré de réplication**
Actuellement, les données primaires sont répliquées 2 fois lors d'un `PUT`. Mais si un voisin crash de manière permanente, la réplication globale tombe. La solution idéale serait de coder un daemon d'**anti-entropie** : à intervalle régulier, le nœud responsable d'une donnée vérifierait si elle est toujours hébergée chez ses 2 voisins directs (qui ont pu changer suite à un départ). S'il remarque un manque, il renvoie un `STORE_REPLICA` en fond pour rétablir le degré de réplication à 3 en permanence.
