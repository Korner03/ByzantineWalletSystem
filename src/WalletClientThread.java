/**
 * WallerClientThread responsible to get values from Replicas system at the end retrieve protocol.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class WalletClientThread extends WalletClient implements Runnable
{
    private Socket socket;
    private int repId;

    /**
     * WalletClientThread constructor
     * @param socket - socket to connect on
     * @param repId - replica id to connect
     */
    WalletClientThread(Socket socket, int repId)
    {
        this.repId = repId;
        this.socket = socket;
    }

    /**
     * wait to receive value from replica in the system and save this value.
     */
    public void run()
    {
        try {
            BufferedReader fromRep = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            while (true)
            {
                // wait to receive value from replica
                if (fromRep.ready())
                {
                    String value = fromRep.readLine();

                    System.out.println("Received Share value of " + value + " from Replica " + repId);

                    revealedValues[repId] = value;
                    revValSem.acquire();
                    valCounter++;
                    revValSem.release();
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("In WalletClientThread Run()");
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}