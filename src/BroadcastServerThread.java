/**
 * BroadcastServerThread listens to every request to broadcast and broadcast it.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

public class BroadcastServerThread extends BroadcastServer implements Runnable
{
    private Socket repSocket;
    private ArrayList<PrintWriter> replica_writers;

    /**
     * BroadcastServerThread constructor
     * @param repSocket - Broadcast Server Socket.
     * @param replica_writers - all Write buffers of Replicas that are connect to the system.
     */
    BroadcastServerThread(Socket repSocket, ArrayList<PrintWriter> replica_writers)
    {
        this.repSocket = repSocket;
        this.replica_writers = replica_writers;
    }

    /**
     * Initialize Broadcast Server Thread to listen for requests and handle them.
     */
    public void run()
    {
        try
        {
            BufferedReader fromRep = new BufferedReader( new InputStreamReader( repSocket.getInputStream() ) );

            while(!repSocket.isClosed())
            {
                // read broadcast request.
                String input = fromRep.readLine();

                if (input != null) {
                    System.out.println("Received Broadcast Request: " + input);
                    for (PrintWriter currRepWriter : replica_writers) // broadcast request.
                    {
                        currRepWriter.println(input);
                    }
                    toClient.println(input);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
