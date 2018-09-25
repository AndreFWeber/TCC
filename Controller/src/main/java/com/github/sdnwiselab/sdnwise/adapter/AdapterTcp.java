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
package com.github.sdnwiselab.sdnwise.adapter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * The adapter class for UDP port communication. Configuration data are passed
 * using a Map<String,String> which contains all the options needed in the
 * constructor of the class.
 *
 * @author Sebastiano Milardo
 * @version 0.1
 */
public class AdapterTcp extends Adapter {

    private final int PORT;
    private final String IP;
    private final boolean IS_SERVER;
    private TcpElement tcpElement;
    private Thread th;

    /**
     * Creates an AdapterUDP object. The conf map is used to pass the
     * configuration settings for the serial port as strings. Specifically
     * needed parameters are:
     * <ol>
     * <li>IN_PORT</li>
     * </ol>
     *
     * @param conf contains the serial port configuration data.
     */
    public AdapterTcp(Map<String, String> conf) {
        this.IS_SERVER = Boolean.parseBoolean(conf.get("IS_SERVER"));
        this.IP = conf.get("IP");
        this.PORT = Integer.parseInt(conf.get("PORT"));
    }

    /**
     * Opens this adapter.
     *
     * @return a boolean indicating the correct ending of the operation
     */
    @Override
    public final boolean open() {
        if (IS_SERVER) {
            tcpElement = new TcpServer(PORT);
        } else {
            tcpElement = new TcpClient(IP, PORT);
        }

        tcpElement.addObserver(this);
        th = new Thread(tcpElement);
        th.start();
        return true;
    }

    /**
     * Closes this adapter.
     *
     * @return a boolean indicating the correct ending of the operation
     */
    @Override
    public final boolean close() {
        tcpElement.isStopped = true;
        return true;
    }

    /**
     * Sends a byte array using this adapter.
     *
     * @param data the array to be sent
     */
    @Override
    public final void send(byte[] data) {
        tcpElement.send(data);
    }

    /**
     * Sends a byte array using this adapter. This method also specifies the
     * destination IP address and TCP port.
     *
     * @param data the array to be sent
     * @param OUT_IP a string containing the IP address of the destination
     * @param OUT_PORT an integer containing the UDP port of the destination
     */
    public final void send(byte[] data, String OUT_IP, int OUT_PORT) {
        tcpElement.send(data);
    }

    /**
     * Sends a byte array using this adapter. This method also specifies the
     * destination IP address and UDP port.
     *
     * @param data the array to be sent
     * @param OUT_IP a string containing the IP address of the destination
     * @param OUT_PORT an integer containing the UDP port of the destination
     */
    public final void sendDatagram(byte[] data, String OUT_IP, int OUT_PORT) {
        try (DatagramSocket sck = new DatagramSocket(OUT_PORT)) {
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName(OUT_IP), OUT_PORT);
            sck.send(packet);
        } catch (SocketException ex) {
            Logger.getLogger(AdapterTcp.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnknownHostException ex) {
            Logger.getLogger(AdapterTcp.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(AdapterTcp.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private abstract class TcpElement extends Observable implements Runnable, Observer {

        boolean isStopped = false;
        final int port;

        TcpElement(int port) {
            this.port = port;
        }

        public abstract void send(byte[] data);

        synchronized boolean isStopped() {
            return this.isStopped;
        }

        @Override
        public final void update(Observable o, Object arg) {
            setChanged();
            notifyObservers(arg);
        }
    }

    private class TcpServer extends TcpElement {

        private ServerSocket serverSocket = null;
        private final LinkedList<Socket> clientSockets = new LinkedList<>();

        TcpServer(int port) {
            super(port);
        }

        @Override
        public void run() {
            openServerSocket();
            Socket clientSocket = null;
            while (!isStopped()) {
                try {
                    clientSocket = this.serverSocket.accept();
                    clientSockets.add(clientSocket);
                } catch (IOException e) {
                    if (isStopped()) {
                        return;
                    }
                    throw new RuntimeException(
                            "Error accepting client connection", e);
                }
                WorkerRunnable wr = new WorkerRunnable(clientSocket);
                wr.addObserver(this);
                new Thread(wr).start();
            }
        }

        public synchronized void stop() {
            this.isStopped = true;
            try {
                this.serverSocket.close();
            } catch (IOException e) {
                throw new RuntimeException("Error closing server", e);
            }
        }

        private void openServerSocket() {
            try {
                this.serverSocket = new ServerSocket(this.port);
            } catch (IOException e) {
                throw new RuntimeException("Cannot open port", e);
            }
        }

        @Override
        public void send(byte[] data) {
            for (Socket sck : clientSockets) {
                try {
                    OutputStream out = sck.getOutputStream();
                    DataOutputStream dos = new DataOutputStream(out);
                    dos.write(data);
                } catch (IOException ex) {
                    Logger.getLogger(AdapterTcp.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        private class WorkerRunnable extends Observable implements Runnable {

            private Socket clientSocket = null;
            private int start = 0;

            private boolean endflag = false;
            //private byte[] data = new byte[1024]; //MAX_PACKET_LENGTH = 116; Porem o mote pode jogar msgs maiores durante a inicializacao...1024 me parece seguro.
            //ArrayList data = new ArrayList();
            List<Byte> dataASCII = new ArrayList<Byte>();
            List<Byte> data = new ArrayList<Byte>();

            WorkerRunnable(Socket clientSocket) {
                this.clientSocket = clientSocket;
            }
            
            private void ASCII_2_Bytes() {
                Iterator iter1 = dataASCII.iterator();
                List<Integer> data_2_byte = new ArrayList<Integer>();

                while(iter1.hasNext()){
                    int next = (Byte)iter1.next() & 0xFF;

                   // System.out.println("NEXT: " + next);
                    
                    if(next == 32){ //ASCII space separator
                        int len = data_2_byte.size();
                        int val = 0;
                        Iterator iter2 = data_2_byte.iterator();   
                        boolean byteZero = false; //Nao perde o(s) primeiro(s) byte se eles forem zero
                        while(iter2.hasNext()){
                            int value = ((Integer)iter2.next()-48);
                            if(value != 0 || byteZero) {
                                byteZero = true;
                                val += value * Math.pow(10, --len); //48 base 10 = 0 ASCII
                            }
                            if(!byteZero){
                                //System.out.println("BYTE " + ((byte)val & 0xFF) + " ");
                                data.add((byte)val);
                                if(--len == 0)
                                    break;
                            }
                        }             
                        if(data_2_byte.get(0)-48==0 && data_2_byte.size()==1){
                            data_2_byte.clear();
                        } else {
                            data.add((byte)val);
                            data_2_byte.clear();
                            // System.out.println("BYTE " + ((byte)val & 0xFF) +"\n\n");
                        }
                    } else {
                        data_2_byte.add(next);
                    }
                }
            }

            @Override
            public void run() {
                try {
                    InputStream in = clientSocket.getInputStream();
                    DataInputStream dis = new DataInputStream(in);
                    while (true) {
                        byte bt = dis.readByte();
                       
                        if((bt & 0xFF) == 192){
                            if(endflag){
                                                                /*
                                Ignora as 5 MSGs de inicializacao do mote:
                                Rime started with address 1.0
                                MAC 01:00:00:00:00:00:00:00 Contiki-2.6-2450-geaa8760 started. Node id is set to 1.
                                nullsec CSMA ContikiMAC, channel check rate 8 Hz, radio channel 26
                                uIP started with IP address 172.16.1.0
                                Starting 'Main Process' 'RF Unicast Send Process' 'RF Broadcast Send Process' 'Timer Process' 'Beacon Timer Process' 'Report Timer Process' 'Packet Handler Process' 'Data Timer Process' 'TCP echo process'
                                */
                                if(start++ < 5){
                                    dataASCII.clear();
                                    continue;
                                }
                                System.out.println("BUFFER DONE");
                                    ASCII_2_Bytes();
                                        Iterator iter1 = data.iterator();
                                        while(iter1.hasNext()){
                                                System.out.printf("0x%02X ",iter1.next());
                                        }
                                        System.out.printf("\n\n\n");
                                        dataASCII.clear();
                            } else
                                endflag = true;
                            continue;            
                        }
                        if((bt & 0xFF) == 13 || (bt & 0xFF) == 46)
                            continue;
                      
                        endflag = false;
                        dataASCII.add(bt);
                        //System.out.println("ADD BT");
                        /*
                        int len = teste & 0xFF;
                        //System.out.printf("VAI LER 0x%02X \n", teste );
                        
                        if(len == 0 && !start)
                            start=true;                        
                        if(!start)
                        //    continue;
                        //System.out.println("O bicho vai pegar");

                        
                        if (len > 0) {                           
                            byte[] data = new byte[len];
                            data[0] = (byte) len;
                            dis.readFully(data, 1, len - 1);                        
                            notifyObservers(data);
                            for (int i = 0; i < len; i++) {
                                System.out.printf("0x%02X", data[i]);
                            }
                            System.out.printf("\n\n\n");

                        }                
*/
                    }
                } catch (IOException ex) {
                    Logger.getLogger(
                            AdapterTcp.class.getName()).log(Level.SEVERE, null, ex);

                }
            }
        }
    }

    private class TcpClient extends TcpElement {

        Socket socket;

        TcpClient(String ip, int port) {
            super(port);
            try {
                socket = new Socket(ip, port);
            } catch (IOException ex) {
                Logger.getLogger(
                        AdapterTcp.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        @Override
        public void send(byte[] data) {
            try {
                OutputStream out = socket.getOutputStream();
                DataOutputStream dos = new DataOutputStream(out);
                dos.write(data);
            } catch (IOException ex) {
                Logger.getLogger(AdapterTcp.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        @Override
        public void run() {
            try {
                InputStream in = socket.getInputStream();
                DataInputStream dis = new DataInputStream(in);
                while (true) {
                    int len = dis.readByte() & 0xFF;
                    if (len > 0) {
                        byte[] data = new byte[len];
                        data[0] = (byte) len;
                        dis.readFully(data, 1, len - 1);
                        setChanged();
                        notifyObservers(data);
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(AdapterTcp.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

}
