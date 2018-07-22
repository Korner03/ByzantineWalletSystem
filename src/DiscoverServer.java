/**
 * Discover Server class responsible for the initialize process by accepting 4 Replicas, Broadcast Server and client to
 * the system. When the Initialize process has been completed the Discover Server is shut down.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;


public class DiscoverServer
{
    private static final int PORT = 4444;
    private static int n_replicas = 4;
    private static ArrayList<Socket> replica_sockets = new ArrayList<Socket>(n_replicas);
    private static ArrayList<PrintWriter> replica_writers = new ArrayList<PrintWriter>(n_replicas);
    private static ServerSocket serverSocket = null;

    /**
     * The function responsible for the initialize process.
     * @param args
     */
    public static void main( String args[] )
    {
        try {
            // open socket for incoming connections.
            serverSocket = new ServerSocket(PORT);
            System.out.println("----Discover Server is up!----");

            // accepting all connections
            acceptReplicas();
            serverSocket.close();
            System.out.println("----Closed My Server socket----");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("----Discover Server Shutdown----");
    }

    /**
     * The function accept all replicas, broadcast-server and client connections and save each connection information.
     * In addition, the function sends to each replica the information that it needs in order to be part of the system.
     * The order of the connections supposed to be:
     *      1. broadcast-server
     *      2. 4-replicas
     *      3. client
     * @throws IOException
     */
    private static void acceptReplicas() throws IOException
    {

        String[] replicas_info = new String[n_replicas];

        // wait for broadcast-server to connect
        Socket broadcastSocket = serverSocket.accept();
        System.out.println("Connected with Broadcast server!");
        BufferedReader fromBroadcast = new BufferedReader( new InputStreamReader( broadcastSocket.getInputStream() ) );
        String broadcastInfo = fromBroadcast.readLine(); // get broadcast server information.
        broadcastSocket.close();

        Socket[] repSockets = new Socket[n_replicas];

        // wait for n_replicas to connect.
        for (int n_conns = 0; n_conns < n_replicas; n_conns++)
        {
            Socket curr_socket = serverSocket.accept();
            repSockets[n_conns] = curr_socket;
            System.out.println("Replica connected! her id is: " + n_conns);

            PrintWriter toRep = new PrintWriter(curr_socket.getOutputStream(), true);
            BufferedReader fromRep = new BufferedReader( new InputStreamReader( curr_socket.getInputStream() ) );

            replica_sockets.add(curr_socket);
            replica_writers.add(toRep);

            // get replica information.
            replicas_info[n_conns] = fromRep.readLine();
        }

        StringBuilder builder = null;
        // all replicas are connect, send each replica broadcast-server/ other replicas ip and ports.
        for (int i = 0; i < n_replicas; i++)
        {
            System.out.println("----Sending Replicas IP/PORT info----");
            PrintWriter currToRep = replica_writers.get(i);
            builder = new StringBuilder();
            builder.append(i).append(";");
            for (int j = 0; j < n_replicas; j++)
            {
                if (i != j) {
                    builder.append(j).append(",");
                    builder.append(replicas_info[j]);
                    builder.append(";");
                }

            }
            builder.append(broadcastInfo);
            String repsInfoString = builder.toString();
            // Send replica i the IP/PORT of all other Replicas and BD server at the end
            currToRep.println(repsInfoString);
        }

        assert builder != null;
        builder.deleteCharAt(0);
        builder.insert(0, String.valueOf(n_replicas - 1) + "," + replicas_info[n_replicas - 1]);

        // keep information for client and wait for client to connect.
        String infoForClient = builder.toString();
        acceptClient(infoForClient);

        for (Socket repSocket : repSockets) {
            repSocket.close();
            System.out.println("----Closed Replica socket----");
        }
    }

    /**
     * The function accpets client connection and send it replicas and bd-server ips and ports.
     * @param infoForClient - replicas and bd-server ips and ports.
     * @throws IOException
     */
    private static void acceptClient(String infoForClient)  throws IOException
    {
        System.out.println("----Waiting for Client to connect----");
        Socket clientSocket = serverSocket.accept();
        System.out.println("Client connected with Discover server!");

        PrintWriter toClient = new PrintWriter(clientSocket.getOutputStream(), true);
        System.out.println("----Sending IP/PORT info to Client----");
        toClient.println(infoForClient);
        clientSocket.close();
        System.out.println("----Closed client socket----");
    }
}



