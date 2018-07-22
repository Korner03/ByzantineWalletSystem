/**
 * Replica Broadcast Thread handler, which handle broadcast-server messages.
 */

import java.io.BufferedReader;
import java.io.IOException;


public class RepBCHandlerThread extends Replica implements Runnable
{
    private BufferedReader fromBroad;

    /**
     * Replica Broadcast thread constructor.
     * @param fromBroad Broadcast server BufferReader.
     */
    RepBCHandlerThread(BufferedReader fromBroad)
    {
        this.fromBroad = fromBroad;
    }

    /**
     * Initialize Thread and handle requests:
     *      1. reveal
     *      2. humiliate
     *      3. shareP
     *      4. shareR
     *      5. OK
     *      6. OK2
     */
    public void run()
    {
        try {

            while (true)
            {
                // try to read from broadcast - server
                if (fromBroad.ready())
                {
                    String[] bdMessage = fromBroad.readLine().split(";"); // parse broadcast server message
                    String typeMsg = bdMessage[0];

                    if (typeMsg.equals("Complaint")) {
                        continue;
                    } else if (typeMsg.equals("Reveal")) // handle Reveal message
                    {
                        System.out.println("Received a Complaint response from Dealer via Broadcast: " + bdMessage[1]);

                        String[] ij = bdMessage[1].split(",");
                        int i = Integer.parseInt(ij[0]);
                        int j = Integer.parseInt(ij[1]);
                        // if I hear my complaint on j' Replica. update my values.
                        if (i == my_id)
                        {
                            String[] SkSv = bdMessage[2].split(",");
                            SkIJ[j] = SkSv[0];
                            SkJI[j] = SkSv[1];
                            SvIJ[j] = SkSv[2];
                            SvJI[j] = SkSv[3];
                            repsThreadsStatus[j] = true;
                        }

                    } else if (typeMsg.equals("Humiliate")) { // Handle Humiliate message
                        System.out.println("Received a Humiliate Message via Broadcast");
                        handleHumiliate(bdMessage);

                    } else if (typeMsg.equals("ShareP")) { // handle ShareP message

                        int otherId = Integer.parseInt(bdMessage[1]);
                        if (otherId != my_id) { // if this is not my polynomial, save other polynomial
                            String[] otherPoly = bdMessage[2].split(",");
                            updatePoly(otherPoly, otherId);
                        }

                        sem.acquire();
                        countPMsgs++; // raise polynomials counter
                        sem.release();

                    } else if (typeMsg.equals("ShareR")) { // handle ShareR message

                        int otherId = Integer.parseInt(bdMessage[1]);

                        String Ri = bdMessage[2];
                        R[otherId] = Ri; // keep R(i)
                        System.out.println("Received R(" + otherId + ")=" + Ri);

                        sem2.acquire();
                        countRMsgs++; // raise R counter
                        sem2.release();

                    } else if (typeMsg.equals("OK")) { // handle OK message
                        System.out.println("Received OK via Broadcast");
                        okCounter++;

                    } else if (typeMsg.equals("OK2")) { // handle OK2 message
                        System.out.println("Received OK2 via Broadcast");
                        ok2Counter++;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * update values of otherReplica that was Humiliate
     * @param bdMessage a string containing values of byzantine replica discovered
     */
    private void handleHumiliate(String[] bdMessage)
    {
        int otherId = Integer.parseInt(bdMessage[1]);
        String[] otherSkij = bdMessage[2].split(",");
        String[] otherSkji = bdMessage[3].split(",");
        String[] otherSvij = bdMessage[4].split(",");
        String[] otherSvji = bdMessage[5].split(",");

        SkIJ[otherId] = otherSkij[my_id];
        SkJI[otherId] = otherSkji[my_id];
        SkIJ[otherId] = otherSvij[my_id];
        SkIJ[otherId] = otherSvji[my_id];
    }

    /**
     * Save other Replica polynomial.
     * @param otherPoly - Replica polynomial coefficients
     * @param otherId - Replica id
     */
    private static void updatePoly(String[] otherPoly, int otherId)
    {
        otherPolys[otherId][0] = Integer.parseInt(otherPoly[0]);
        otherPolys[otherId][1] = Integer.parseInt(otherPoly[1]);
    }
}
