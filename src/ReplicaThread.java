/**
 * ReplicaThread handle store protocol for every replica i with every replica j
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;


public class ReplicaThread extends Replica implements Runnable
{
    private int jId;
    private BufferedReader fromRep;
    private PrintWriter toRep;

    /**
     * Normal Replica constructor
     * @param id - Other Replica id to communicate
     * @param toRep - other Replica write buffer
     * @param fromRep - other Replica read buffer.
     */
    ReplicaThread(int id, PrintWriter toRep, BufferedReader fromRep)
    {
        this.jId = id;
        this.toRep = toRep;
        this.fromRep = fromRep;
    }


    /**
     * Start store protocol:
     * compare my values with other Replicas values and complain on every miss match.
     */
    public void run()
    {
        boolean answer = false;
        try
        {
            if (byzMode)
            {
                toRep.println("666,666;666,666"); // byzantine Replica test
            } else {
                // send my values to replica j
                toRep.println(SkIJ[jId] + ',' + SkJI[jId] + ';' + SvIJ[jId] + ',' + SvJI[jId]);
            }

            while (true)
            {
                if (fromRep.ready())
                {
                    // get Replica j values
                    String[] repInput = fromRep.readLine().split(";");
                    String[] Sk = repInput[0].split(",");
                    String[] Sv = repInput[1].split(",");
                    answer = compareValues(Sk, Sv); // compare my values with Replica j' values.
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("In ReplicaThread Run()");
            e.printStackTrace();
        }

        if (!answer) // if compare test failed, complain on replica j.
        {
            complain();
        }
        else
        {
            System.out.println("Check Values Versus Replica " + jId + " Passed");
            repsThreadsStatus[jId] = true; // set status as done.
        }

    }

    /**
     * compare Sk and Sv with my Sk and Sv.
     * @param Sk - String array of values.
     * @param Sv - String array of values.
     * @return - True if my values and input values are equal, otherwise false.
     */
    private boolean compareValues(String[] Sk, String[] Sv)
    {
        return Sk[0].equals(SkJI[jId]) && Sk[1].equals(SkIJ[jId]) &&
                Sv[0].equals(SvJI[jId]) && Sv[1].equals(SvIJ[jId]) && !byzMode;
    }

    /**
     * Broadcast complaint on J' Replica.
     */
    private void complain()
    {
        System.out.println("Broadcasting a Complaint on Replica: " + jId);

        try {
            sem.acquire();
            toBroadcast.println("Complaint;" + my_id + "," + jId);
            sem.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}