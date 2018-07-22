/**
 * WallerClientThread responsible to get values from Replicas system at the end retrieve protocol.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

public class WalletClientStoreThread extends WalletClient implements Runnable
{
    private BufferedReader fromBroad;
    private PrintWriter toBroad;

    /**
     * WalletClientThread constructor
     */
    WalletClientStoreThread(BufferedReader fromBroad, PrintWriter toBroad)
    {
        this.fromBroad = fromBroad;
        this.toBroad = toBroad;
    }

    /**
     * wait to receive value from replica in the system and save this value.
     */
    public void run()
    {
        try {
            while (!Thread.currentThread().isInterrupted())
            {
                String[] repOutput = fromBroad.readLine().split(";");
                String repAns = repOutput[0];
                if (repAns.equals("OK")) // count ok messages
                {
                    countOks++;
                    int repId = Integer.parseInt(repOutput[1]);
                    repOks[repId] = true;
                }
                else if (repAns.equals("OK2")) // count ok2 messages
                {
                    countOks2++;
                    int repId = Integer.parseInt(repOutput[1]);
                    repOks2[repId] = true;
                }
                else if (repAns.equals("Complaint"))
                {
                    String[] complaint = repOutput[1].split(",");
                    int i = Integer.parseInt(complaint[0]);
                    int j = Integer.parseInt(complaint[1]);
                    toBroad.println("Reveal;" + i + "," + j +";" + skValues[i][j] + "," + skValues[j][i] + "," + svValues[i][j] + "," + svValues[j][i]);
                }
            }
        } catch (IOException e) {
            System.err.println("In RepBroadcastThread Run()");
            e.printStackTrace();
        }


    }
}