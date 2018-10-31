/* 
 * Copyright (C) 2015 SDN-WISE
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.sdnwiselab.sdnwise.controller;

import com.github.sdnwiselab.sdnwise.adapter.Adapter;
import com.github.sdnwiselab.sdnwise.packet.DataPacket;
import com.github.sdnwiselab.sdnwise.packet.NetworkPacket;
import com.github.sdnwiselab.sdnwise.topology.NetworkGraph;
import com.github.sdnwiselab.sdnwise.util.NodeAddress;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import org.graphstream.algorithm.Dijkstra;
import org.graphstream.graph.Node;

/**
 * This class implements the Controller class using the Dijkstra routing
 * algorithm in order to find the shortest path between nodes. When a request
 * from the network is sent, this class sends a SDN_WISE_OPEN_PATH message with
 * the shortest path. No action is taken if the topology of the network changes.
 *
 * @author Sebastiano Milardo
 * @version 0.1
 */
public class ControllerTCC extends Controller {

    private final boolean DEBUG_PRINT = true;
    private final Dijkstra dijkstra;
    private String lastSource = "";
    private long lastModification = -1;
    protected Map<NodeAddress, Vector<LinkedList<NodeAddress>> > paths = new HashMap<NodeAddress, Vector<LinkedList<NodeAddress>> >();
    protected Map<NodeAddress, LinkedList<NodeAddress> > active_paths = new HashMap<NodeAddress, LinkedList<NodeAddress> >();

    LinkedList<String> nodesID = new LinkedList<>();
    private final Vector<LinkedList<NodeAddress>> pathVector = new Vector<>(2); // N here isn't really needed, but it sets the initial capacity of the vector
    private int BATTERY_MINIMUM_THRESHOLD = 100;

    /*
     * Constructor method fo ControllerDijkstra.
     * 
     * @param id ControllerId object.
     * @param lower Lower Adpater object.
     * @param networkGraph NetworkGraph object.
     */
    public ControllerTCC(Adapter lower, NetworkGraph networkGraph, String type) {
        super(lower, networkGraph, type);
        this.dijkstra = new Dijkstra(Dijkstra.Element.EDGE, null, "length");
    }

    @Override
    public final void graphUpdate() {

    }

    @Override
    public final void manageRoutingRequest(NetworkPacket data) {

        String destination = data.getNetId() + "." + data.getDst();
        String source = data.getNetId() + "." + data.getSrc();


        if (!source.equals(destination)) {
            Node sourceNode = networkGraph.getNode(source);
            Node destinationNode = networkGraph.getNode(destination);
            LinkedList<NodeAddress> path = null;

            if (sourceNode != null && destinationNode != null) {
                if (!lastSource.equals(source) || lastModification != networkGraph.getLastModification()) {
                    results.clear();
                    dijkstra.init(networkGraph.getGraph());
                    dijkstra.setSource(networkGraph.getNode(source));
                    dijkstra.compute();
                    lastSource = source;
                    lastModification = networkGraph.getLastModification();
                } else {
                    path = results.get(data.getDst());
                }
                if (path == null) {
                    path = new LinkedList<>();
                    for (Node node : dijkstra.getPathNodes(networkGraph.getNode(destination))) {
                        path.push((NodeAddress) node.getAttribute("nodeAddress"));
                    }
                    System.out.println("[CTRL]: " + path);
                    results.put(data.getDst(), path);
                }
                if (path.size() > 1) {
                    sendPath((byte) data.getNetId(), path.getFirst(), path);

                    data.unsetRequestFlag();
                    data.setSrc(getSinkAddress());
                    sendNetworkPacket(data);
                } else {
                    // TODO send a rule in order to say "wait I dont have a path"
                    //sendMessage(data.getNetId(), data.getDst(),(byte) 4, new byte[10]);
                }
            }
        }
    }

    /* 
    *  Onde BEST nesse caso sera o menor caminho.
    *  Caso haja mais de um caminho com mesmo numero de modulos (ou saltos), o melhor caminha sera aquele em
    *  que os modulos possuem melhor nivel energetico. (i.e: A maior soma do nivel de bateria de todos os 
    *  modulos do path define o melhor caminho)
    */
    private LinkedList<NodeAddress> findBestPath(int initialSize, NetworkPacket data){
        if(!this.pathVector.isEmpty()){
            LinkedList<NodeAddress> pathToSend = null;

            //int shorterPathIndex = 0;
            Vector shorterPathIndex = new Vector();
            Vector numberOfHopsAvailable = new Vector(); //armazena todos os numeros de hops possivel entre A e B

            int shorterPathHops = 1024;
            boolean keepShortestPath = false;
            boolean pathMatch = false;
            int pathMatchIndex = 0;
            boolean pathSuitable = true; //Devido a bateria de um ou mais modulos...
            int i = 0;
            for (Iterator it = this.pathVector.iterator(); it.hasNext(); i++) {
                LinkedList<NodeAddress> p = (LinkedList<NodeAddress> )it.next();
                if(DEBUG_PRINT){
                    System.out.println("path "+p.toString());
                    System.out.println("p.size() "+ p.size() + " shorterPathHops " + shorterPathHops);
                }
                if(p.size()<initialSize)
                    continue;
                for (NodeAddress na : p){
                    if(DEBUG_PRINT)
                        System.out.println(" node - "+ na.toString() + " com bateria: " +nodesBattery.get(na));
                    
                    if(nodesBattery.get(na) < BATTERY_MINIMUM_THRESHOLD){
                        pathSuitable=false;
                        break;
                    } else {
                        pathSuitable=true;
                    }
                }
                if(!pathSuitable)
                    continue;
                
                if(p.size() < shorterPathHops){
                    shorterPathHops = p.size();
                    shorterPathIndex.clear();
                    shorterPathIndex.add(i);    
                } else if(p.size() == shorterPathHops){
                    shorterPathIndex.add(i);
                } 
                if(!numberOfHopsAvailable.contains(p.size()))
                    numberOfHopsAvailable.add(p.size());
            }
            if(DEBUG_PRINT){
                System.out.println("0 - "+numberOfHopsAvailable.toString());
                System.out.println("1- " + shorterPathIndex.toString());
            }
            //Verifica se o ha um path A->B ativo.
            boolean pathIsActive = active_paths.containsKey(pathVector.get(0).getLast());     
            if(pathIsActive){
                i = 0;
                for (Iterator it = shorterPathIndex.iterator(); it.hasNext(); i++) {
                    int index = (int)it.next();
                    NodeAddress addr = pathVector.get(index).getLast();

                    if(DEBUG_PRINT)
                        System.out.println("2- " + addr.toString());
                    //if(!pathIsActive){
                    //    break;
                    //} else {
                        //Se existe. Verifica se o caminho ativo existe e se esta entre os mais curtos.
                        LinkedList<NodeAddress> activePath = active_paths.get(addr);
                        //if(activePath==null);
                        //System.out.println("3- "+ activePath.toString() + " " + pathVector.get(index).toString());
                        for (NodeAddress na : activePath) {
                            if(pathVector.get(index).contains(na)){
                                pathMatch = true;
                                pathMatchIndex=index;
                            } else{
                                pathMatch = false;
                                break;
                            } 
                        }
                        if(DEBUG_PRINT)    
                            System.out.println("4- "+pathMatch);
                        if(pathMatch){
                            keepShortestPath=true;
                            break;
                            /*
                            for (NodeAddress na : activePath){
                                System.out.println("5 LOOP- " + nodesBattery.get(na));
                                if(nodesBattery.get(na) < BATTERY_MINIMUM_THRESHOLD){
                                    keepShortestPath=false;
                                    break;
                                } else {
                                    keepShortestPath=true;
                                }
                            }*/
                        } 
                    //}

                }
            } 
            if(DEBUG_PRINT)
                System.out.println("6 - " + keepShortestPath);
            if(!keepShortestPath) {
                if(pathIsActive){
                    clearFlowtable((byte)data.getNetId(), data.getDst());
                    active_paths.remove(pathVector.get(0).getLast());
                    if(pathMatch)
                        shorterPathIndex.remove(pathMatchIndex);
                }
                int batterySum=0;
                if(shorterPathIndex.size()>1) {
                    int pathBatteryLevel = 0;
                    for (Iterator it = shorterPathIndex.iterator(); it.hasNext(); i++) {
                        int index = (int)it.next();
                        int level=0;
                        for (NodeAddress na :  pathVector.get(index)){
                            level += nodesBattery.get(na);
                        }
                        if(DEBUG_PRINT)
                            System.out.println("6a- " + level + " do " + index);

                        if(level > pathBatteryLevel){
                            pathBatteryLevel = level;
                            pathToSend = pathVector.get(index);
                        }
                    }
                    if(DEBUG_PRINT)
                        System.out.println("7 - " + pathToSend);
                } else {
                    if(shorterPathIndex.isEmpty()){                        
                        if(!numberOfHopsAvailable.isEmpty())
                            numberOfHopsAvailable.remove(0);
                        if(numberOfHopsAvailable.isEmpty()){
                                
                            System.out.println(" NO MORE PATHS AVAILABLE" );
                        } else {
                            findBestPath((Integer)numberOfHopsAvailable.get(0),  data);
                        }
                    } else {
                        pathToSend = pathVector.get((Integer)shorterPathIndex.get(0));
                        if(DEBUG_PRINT)
                            System.out.println("8 - " + pathToSend);                    
                    }
                }
            } else {
                //pathToSend = active_paths.get(pathVector.get(0).getLast());
                if(DEBUG_PRINT)
                    System.out.println("9 - " + pathToSend);               
            }
            
            if(pathToSend != null){
                active_paths.put(pathVector.get(0).getLast(), pathToSend);
                System.out.println("PATH TO SEND" + pathToSend);
                this.pathVector.clear();            
                return pathToSend;
            }
            if(!pathVector.isEmpty())
                System.out.println("<<<<<<<< "+ pathVector.get(0).getLast());
            this.pathVector.clear();
        }
        return null;
    }
    
    @Override
    public void clearFlowtable(byte netId, NodeAddress addr/*,  NetworkPacket data*/){
        if(active_paths.isEmpty())
            System.out.println("Active Path is empty");
        else {
            System.out.println("sendClearFlowtable - " + addr.toString() + " clear " + active_paths.get(addr).toString());
            //sendClearFlowtable((byte) data.getNetId(), data.getSrc(), active_paths.get(data.getSrc()));
            sendClearFlowtable(netId, addr, active_paths.get(addr));
        }
    }
    
    @Override
    public final void TCC_manageRoutingRequest_disjoint(NetworkPacket data, NetworkGraph _networkGraph, boolean SendDataBack) {
        NetworkGraph tmp_networkGraph = new NetworkGraph(_networkGraph.getTimeout(), _networkGraph.getRssiResolution());;
        tmp_networkGraph.copy(_networkGraph);
        
        String destination = data.getNetId() + "." + data.getDst();
        String source = data.getNetId() + "." + data.getSrc();

        if (!source.equals(destination)) {
            Node sourceNode =  tmp_networkGraph.getNode(source);
            Node destinationNode = tmp_networkGraph.getNode(destination);
            LinkedList<NodeAddress> path = null;

            if (sourceNode != null && destinationNode != null) {
                    results.clear();
                    dijkstra.init(tmp_networkGraph.getGraph());
                    dijkstra.setSource(tmp_networkGraph.getNode(source));
                    dijkstra.compute();
                    lastSource = source;
                    lastModification = tmp_networkGraph.getLastModification();

                    path = new LinkedList<>();
                    for (Node node : dijkstra.getPathNodes(tmp_networkGraph.getNode(destination))) {
                        path.push((NodeAddress) node.getAttribute("nodeAddress"));
                        if(!node.getAttribute("nodeAddress").equals(data.getDst()) && !node.getAttribute("nodeAddress").equals(data.getSrc())){
                           nodesID.push(node.getId());
                           tmp_networkGraph.removeNode(node);
                        }
                    }
                    if(path.size()>0){
                        System.out.println("[CTRL]: " + path);
                        results.put(data.getDst(), path);      
                        pathVector.add(path);
                    }
                if (path.size() > 1 && path.size() > 2) {
                    TCC_manageRoutingRequest_disjoint(data, tmp_networkGraph, SendDataBack);
                } else {
                    System.out.println("FIM____________________________________ FROM:" + source + " To: " + destination + " ");
                    this.paths.put(data.getDst(), pathVector);
                    //pathVector.clear();
                    nodesID.clear();
                    path = findBestPath(0, data);
                    if(path != null){
                        System.out.println("*******************SENDING PATH******************");
                        pathChecker();
                        
                        sendPath((byte) data.getNetId(), path.getFirst(), path);
                        if(SendDataBack){
                            data.unsetRequestFlag();
                            data.setSrc(getSinkAddress());
                            sendNetworkPacket(data);
                        }
                    } else {
                        System.out.println("PATH NULL o.0 Ele manteve o caminho... :D ");
                    }

                    // TODO send a rule in order to say "wait I dont have a path"
                    //sendMessage(data.getNetId(), data.getDst(),(byte) 4, new byte[10]);
                }
            }
        }
    }
    boolean pathCheckerOn = false;
    
    private void pathChecker() {
        if(pathCheckerOn)
           return;
        pathCheckerOn=true;
        Timer timer = new Timer("MyTimer");//create a new Timer
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~CHECK PATH " + active_paths.toString());
                for (Iterator it = active_paths.keySet().iterator(); it.hasNext();) {
                    NodeAddress index = (NodeAddress)it.next();
                    
                    DataPacket p = new DataPacket(1,getSinkAddress(), index);
                    p.setNxhop(getSinkAddress());
                    
                    p.setPayload("Veryfing if path is OK :D".getBytes(Charset.forName("UTF-8")));
                    System.out.println(">>>>>>>> :0 " + index.toString());
                    TCC_manageRoutingRequest_disjoint(p, networkGraph, false);
                    System.out.println("/////~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~CHECK PATH");

                }
            }
        };
        timer.scheduleAtFixedRate(timerTask, 20000, 20000);//this line starts the timer at the same time its executed
    }
    
    @Override
    public void setupNetwork() {
    }

    @Override
    public void TCC_manageRoutingRequest_Negative_Reward(NetworkPacket data, NetworkGraph _networkGraph, boolean SendDataBack) {
        NetworkGraph tmp_networkGraph = new NetworkGraph(_networkGraph.getTimeout(), _networkGraph.getRssiResolution());
        tmp_networkGraph.copy(_networkGraph);
        
        String destination = data.getNetId() + "." + data.getDst();
        String source = data.getNetId() + "." + data.getSrc();

        if (!source.equals(destination)) {
            Node sourceNode =  tmp_networkGraph.getNode(source);
            Node destinationNode = tmp_networkGraph.getNode(destination);
            LinkedList<NodeAddress> path = null;

            if (sourceNode != null && destinationNode != null) {
                    results.clear();
                    dijkstra.init(tmp_networkGraph.getGraph());
                    dijkstra.setSource(tmp_networkGraph.getNode(source));
                    dijkstra.compute();
                    lastSource = source;
                    lastModification = tmp_networkGraph.getLastModification();

                    path = new LinkedList<>();
                    for (Node node : dijkstra.getPathNodes(tmp_networkGraph.getNode(destination))) {
                        path.push((NodeAddress) node.getAttribute("nodeAddress"));
                        if(!node.getAttribute("nodeAddress").equals(data.getDst()) && !node.getAttribute("nodeAddress").equals(data.getSrc())){
                           nodesID.push(node.getId());
                           tmp_networkGraph.removeNode(node);
                        }
                    }
                    if(path.size()>0){
                        System.out.println("[CTRL]: " + path);
                        results.put(data.getDst(), path);      
                        pathVector.add(path);
                    }
                if (path.size() > 1 && path.size() > 2) {
                    TCC_manageRoutingRequest_disjoint(data, tmp_networkGraph, SendDataBack);
                } else {
                    System.out.println("FIM____________________________________ FROM:" + source + " To: " + destination + " ");
                    this.paths.put(data.getDst(), pathVector);
                    //pathVector.clear();
                    nodesID.clear();
                    path = findBestPath(0, data);
                    if(path != null){
                        System.out.println("*******************SENDING PATH******************");
                        pathChecker();
                        
                        sendPath((byte) data.getNetId(), path.getFirst(), path);
                        if(SendDataBack){
                            data.unsetRequestFlag();
                            data.setSrc(getSinkAddress());
                            sendNetworkPacket(data);
                        }
                    } else {
                        System.out.println("PATH NULL o.0 Ele manteve o caminho... :D ");
                    }

                    // TODO send a rule in order to say "wait I dont have a path"
                    //sendMessage(data.getNetId(), data.getDst(),(byte) 4, new byte[10]);
                }
            }
        }    
    }


}
