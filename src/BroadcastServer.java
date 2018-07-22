/**
 * Broadcast Server class broadcast each received message to all connections.
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class BroadcastServer
{

    protected static int n_replicas = 4;
    private static final int MY_PORT = 3333;
    private static final int SERVER_PORT = 4444;
    private static String discServerId = "localhost";
    private static String MY_IP = "localhost";
    private static ServerSocket mySocket = null;
    private static ArrayList<PrintWriter> replica_writers = new ArrayList<PrintWriter>(n_replicas);
    private static ArrayList<Socket> replicaSockets = new ArrayList<Socket>(n_replicas);
    protected static Socket clientSocket;
    protected static PrintWriter toClient;


    /**
     * Initialize Broadcast Server
     * @param args
     */
    public static void main( String args[] )
    {
        try {
            // start listen socket for connections.
            mySocket = new ServerSocket(MY_PORT);

            // connect to discover server.
            System.out.println("----Broadcast Server is up!----");
            Socket toServerSocket = new Socket(discServerId, SERVER_PORT);
            PrintWriter toDiscServer = new PrintWriter(toServerSocket.getOutputStream(), true);
            MY_IP = InetAddress.getLocalHost().toString().split("/")[1];
            // send to discover serve my ip and port
            toDiscServer.println(MY_IP + "," + MY_PORT);

            // wait for n_replicas to connect
            System.out.println("----Waiting for Replicas to connect----");
            connectToReps();

            // wait for client connection
            System.out.println("----Waiting for Client to connect----");
            connectToClient();

            // initialize broadcast threads for each replica.
            System.out.println("----Waiting for Requests----");
            initThreads();

            // closing all sockets
            toServerSocket.close();
            toClient.close();
            mySocket.close();
            for (Socket repSocket : replicaSockets) {
                repSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * The function waits for n_replicas to connect and save each connection
     * @throws IOException
     */
    private static void connectToReps() throws IOException
    {
        for (int i = 0; i < n_replicas; i++)
        {
            Socket repSocket = mySocket.accept();
            System.out.println("Replica connected with Broadcast server");

            PrintWriter toRep = new PrintWriter(repSocket.getOutputStream(), true);
            replicaSockets.add(repSocket);
            replica_writers.add(toRep);
        }
    }

    /**
     * The function waits for client connection and initialize a thread that handles client requests.
     * @throws IOException
     */
    private static void connectToClient() throws IOException
    {
        clientSocket = mySocket.accept();
        System.out.println("Client connected with Broadcast server");

        toClient = new PrintWriter(clientSocket.getOutputStream(), true);

        Thread currRepThread = new Thread(new BroadcastServerThread(clientSocket, replica_writers));
        currRepThread.start();
    }

    /**
     * The function initialize thread for each replica that handles replica requests.
     * @throws InterruptedException
     */
    private static void initThreads() throws InterruptedException
    {
        Thread[] repThreads = new Thread[n_replicas];
        for (int i = 0; i < n_replicas; i++)
        {
            repThreads[i] = new Thread(new BroadcastServerThread(replicaSockets.get(i), replica_writers));
            repThreads[i].start();
        }

        for (int i = 0; i < n_replicas; i++)
        {
            repThreads[i].join();
        }
    }
}
