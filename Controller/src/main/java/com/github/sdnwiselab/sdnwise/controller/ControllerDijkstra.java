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
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
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
public class ControllerDijkstra extends Controller {

    private final Dijkstra dijkstra;
    private String lastSource = "";
    private long lastModification = -1;

    /*
     * Constructor method fo ControllerDijkstra.
     * 
     * @param id ControllerId object.
     * @param lower Lower Adpater object.
     * @param networkGraph NetworkGraph object.
     */
    public ControllerDijkstra(Adapter lower, NetworkGraph networkGraph) throws FileNotFoundException, UnsupportedEncodingException {
        super(lower, networkGraph, "ControllerDijkstra");
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
                    if(active_paths.get(data.getDst()) != null){
                        if(active_paths.get(data.getDst()).toString().equals(path.toString())){
                            System.out.println("Mantem o caminho em uso...");
                            return;
                        }
                        clearFlowtable((byte)data.getNetId(), data.getDst());
                        active_paths.remove(data.getDst());
                    }
                    System.out.println("******************SENDING PATH***********" + path.toString());
                    pathChecker();
                    active_paths.put(data.getDst(), path);
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

        boolean pathCheckerOn = false;
        private Timer timer;

    private void pathChecker() {
        if(pathCheckerOn)
           return;
        pathCheckerOn=true;

        System.out.println("\n\nINIT TIMER\n\n");
        
        timer = new Timer("MyTimer");//create a new Timer
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                System.out.println("\n\n\nCHECK PATH " + active_paths.toString());
                Map<NodeAddress, LinkedList<NodeAddress> > paths = new HashMap<NodeAddress, LinkedList<NodeAddress> >();
                for (Iterator it = active_paths.keySet().iterator(); it.hasNext();) {
                    NodeAddress index = (NodeAddress)it.next();
                    paths.put(index, active_paths.get(index));
                }
                //Necessario, pois o metodo *manageRouting* modifica a lista active_paths
                System.out.println("path size checker " + paths.size());
                for (Iterator it = paths.keySet().iterator(); it.hasNext();) {
                    NodeAddress index = (NodeAddress)it.next();
                    
                    DataPacket p = new DataPacket(1,getSinkAddress(), index);
                    p.setNxhop(getSinkAddress());
                    
                    p.setPayload("Veryfing if path is OK :D".getBytes(Charset.forName("UTF-8")));
                    manageRoutingRequest(p);
                }
            }
        };
        timer.scheduleAtFixedRate(timerTask, 20000, 20000);//this line starts the timer at the same time its executed
    }
    
    
    @Override
    public void setupNetwork() {

    }
    
    @Override
    public void TCC_manageRoutingRequest_disjoint(NetworkPacket data, NetworkGraph _networkGraph, boolean SendDataBack) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clearFlowtable(byte netId, NodeAddress addr/*,  NetworkPacket data*/){
        if(active_paths.isEmpty())
            System.out.println("Active Path is empty");
        else {
            System.out.println("sendClearFlowtable - " + addr.toString() + " clear " + active_paths.get(addr).toString());
  //          timer.cancel();
//            pathCheckerOn=false;
            //sendClearFlowtable((byte) data.getNetId(), data.getSrc(), active_paths.get(data.getSrc()));
            sendClearFlowtable(netId, addr, active_paths.get(addr));
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                System.out.println("\nThread.sleep\n");
            }
        }
    }

    @Override
    public void TCC_manageRoutingRequest_Negative_Reward(NetworkPacket data, NetworkGraph _networkGraph, boolean SendDataBack) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
