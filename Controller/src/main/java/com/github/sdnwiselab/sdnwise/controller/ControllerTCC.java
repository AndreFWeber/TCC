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
import com.github.sdnwiselab.sdnwise.packet.NetworkPacket;
import com.github.sdnwiselab.sdnwise.topology.NetworkGraph;
import com.github.sdnwiselab.sdnwise.util.NodeAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
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
    public ControllerTCC(Adapter lower, NetworkGraph networkGraph) {
        super(lower, networkGraph);
        this.dijkstra = new Dijkstra(Dijkstra.Element.EDGE, null, "length");
    }

    @Override
    public final void graphUpdate() {

    }

    @Override
    public final void manageRoutingRequest(NetworkPacket data) {

        String destination = data.getNetId() + "." + data.getDst();
        String source = data.getNetId() + "." + data.getSrc();
        for(Map.Entry<String, Integer> entry : nodesBattery.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            // do what you have to do here
            // In your case, another loop.
        }

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
                    LinkedList<Integer> nodesB = new LinkedList<>();
                    LinkedList<String> nodesID = new LinkedList<>();

     public final void MultiplePath_manageRoutingRequest(NetworkPacket data, NetworkGraph _networkGraph) {
        NetworkGraph tmp_networkGraph = new NetworkGraph(_networkGraph.getTimeout(), _networkGraph.getRssiResolution());;
        tmp_networkGraph.copy(_networkGraph);
         
        String destination = data.getNetId() + "." + data.getDst();
        String source = data.getNetId() + "." + data.getSrc();

        for(Map.Entry<String, Integer> entry : nodesBattery.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            // do what you have to do here
            // In your case, another loop.
        }
        
        if (!source.equals(destination)) {
            Node sourceNode =  tmp_networkGraph.getNode(source);
            Node destinationNode = tmp_networkGraph.getNode(destination);
            LinkedList<NodeAddress> path = null;

            if (sourceNode != null && destinationNode != null) {
                    //System.out.println("NO CAMINHO::: FROM: " + source + " To: " + destination );
                    results.clear();
                    dijkstra.init(tmp_networkGraph.getGraph());
                    dijkstra.setSource(tmp_networkGraph.getNode(source));
                    dijkstra.compute();
                    lastSource = source;
                    lastModification = tmp_networkGraph.getLastModification();

                    path = new LinkedList<>();
                    for (Node node : dijkstra.getPathNodes(tmp_networkGraph.getNode(destination))) {
                        path.push((NodeAddress) node.getAttribute("nodeAddress"));
                        //System.out.println("TESTE>>>>>>>>>>>> " + node.getAttribute("nodeAddress")+ " " + data.getDst() + " " + node.getAttribute("nodeAddress").equals(data.getDst()) );
                        if(!node.getAttribute("nodeAddress").equals(data.getDst()) && !node.getAttribute("nodeAddress").equals(data.getSrc())){
                           //System.out.println("NO CAMINHO::: FROM: " + source + " To: " + destination + " -----*****-----" + node.getAttribute("nodeAddress"));
                           nodesID.push(node.getId());
                          // if(node.getId().equals("0.1"))
                            //System.out.println("remove " + node.getId());
                           tmp_networkGraph.removeNode(node);
                        }
                    }
                    System.out.println("[CTRL]: " + path);
                    results.put(data.getDst(), path);         
                if (path.size() > 1 && path.size() > 2) {
                     //System.out.println("-------------------Procurando caminho alternativo...  para: FROM:" + source + " To: " + destination);
                     MultiplePath_manageRoutingRequest(data, tmp_networkGraph);
                } else {
                    System.out.println("FIM____________________________________ FROM:" + source + " To: " + destination + " ");
                    nodesID.clear();
                    // TODO send a rule in order to say "wait I dont have a path"
                    //sendMessage(data.getNetId(), data.getDst(),(byte) 4, new byte[10]);
                }
            }
        }
    }

    
    
    @Override
    public void setupNetwork() {

    }
}
