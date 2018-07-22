/**
 * Replica class represents a replica in the system which allow client complete store and retrieve protocols.
 */

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.net.InetAddress;

public class Replica extends Thread
{

    private static String discServerId = "localhost";
    private static final int SERVER_PORT = 4444;
    protected static final int n_replicas = 4;
    private static ServerSocket replicaServerSocket = null;
    protected static int my_port;
    protected static int my_id;
    private static String ownIP = "localhost";
    private static Socket clientSocket;
    private static Socket broadcastSocket;

    // client read and write buffers.
    private static BufferedReader fromClient;
    private static PrintWriter toClient;

    // broadcast-server read and write buffers.
    private static BufferedReader fromBroadcast;
    protected static PrintWriter toBroadcast;

    // write and reader buffer maps by replica id
    private static HashMap<Integer, PrintWriter> idToRep = new HashMap<Integer, PrintWriter>(n_replicas - 1);
    private static HashMap<Integer, BufferedReader> idFromRep = new HashMap<Integer, BufferedReader>(n_replicas - 1);

    // store protocol values
    protected static String[] SkIJ;
    protected static String[] SkJI;
    protected static String[] SvIJ;
    protected static String[] SvJI;

    // retrieve protocol values
    protected static String[] SdIJ;
    protected static String[] SdJI;

    // array that indicates if replica 'i' heard n-f messages in the protocol.
    protected static boolean[] repsThreadsStatus;
    protected static Semaphore sem = new Semaphore(1);
    protected static Semaphore sem2 = new Semaphore(1);
    protected static int okCounter = 0;
    protected static int ok2Counter = 0;
    protected static String[] R;
    protected static int[] myPoly;
    private static String polyStr;
    protected static double[][] otherPolys = new double[n_replicas][2];
    protected static int countPMsgs = 0;
    protected static int countRMsgs = 0;
    protected static Random rand = new Random();
    protected static boolean byzMode = false;

    /**
     * Initialize Replica
     *
     * @param args
     */
    public static void main(String args[])
    {
        String hostname = discServerId;
        Scanner configReader = new Scanner(System.in);
        System.out.println("Enter Port number: ");
        my_port = configReader.nextInt();
        configReader.close();

        if (my_port == 5666 || my_port == 5777)
            byzMode = true; // For testing byzantine replica

        try {
            ownIP = InetAddress.getLocalHost().toString().split("/")[1];

            // replica starts to listen for connections.
            replicaServerSocket = new ServerSocket(my_port);
            System.out.println("----Replica Server is up!----");
            Thread.sleep(1000);

            // connect to discover server and send it my port and ip
            System.out.println("----Attempting to connect to Discover Server----");
            connectToDiscoverServer(hostname);

            // wait for n_replicas-1 replicas connections
            System.out.println("----Waiting for Replicas to connect----");
            acceptReplicas();

            // wait for client connection
            System.out.println("----Waiting for Client to connect----");
            acceptClient();

            // handle client request
            System.out.println("----Waiting for Client Requests----");
            handleClientReq();

        } catch (IOException e) {
            System.err.println("Failed to connect!");

        } catch (InterruptedException e) {
            System.err.println("Failed to connect!");
            e.printStackTrace();
        }
    }

    /**
     * The function tries to connect to Discover Server and then connect to bd-server and other replicas.
     *
     * @param hostname - discover server ip.
     * @throws IOException
     */
    private static void connectToDiscoverServer(String hostname) throws IOException {
        // connect to discover server
        Socket discSocket = new Socket(hostname, SERVER_PORT);
        System.out.println("Connected to Discover Server!");
        BufferedReader fromDisc = new BufferedReader(new InputStreamReader(discSocket.getInputStream()));
        PrintWriter toDisc = new PrintWriter(discSocket.getOutputStream(), true);
        toDisc.println(ownIP + "," + my_port);
        System.out.println("----Attempting to connect to Replicas----");
        while (true) {
            // wait for information from discover server.
            if (fromDisc.ready()) {
                String info = fromDisc.readLine();
                String[] pInfo = info.split(";");
                my_id = Integer.parseInt(pInfo[0]);
                System.out.println("My Replica ID is: " + my_id);
                // connect to all replicas
                for (int i = 0; i < n_replicas - 1; i++) {
                    String[] ppInfo = pInfo[i + 1].split(",");

                    int currRepId = Integer.parseInt(ppInfo[0]);
                    String currRepIP = ppInfo[1];
                    int currPort = Integer.parseInt(ppInfo[2]);

                    Socket RepSocket = new Socket(currRepIP, currPort);
                    PrintWriter toRep = new PrintWriter(RepSocket.getOutputStream(), true);

                    idToRep.put(currRepId, toRep);

                    System.out.println("I've connected to Replica: " + currRepId);
                    toRep.println(my_id);
                }

                String[] bdInfo = pInfo[n_replicas].split(",");
                int bdServPort = Integer.parseInt(bdInfo[1]);
                // connect to bd-server
                broadcastSocket = new Socket(bdInfo[0], bdServPort);
                toBroadcast = new PrintWriter(broadcastSocket.getOutputStream(), true);
                fromBroadcast = new BufferedReader(new InputStreamReader(broadcastSocket.getInputStream()));

                System.out.println("I've connected to BroadcastServer");

                break;
            }
        }
        discSocket.close();
    }

    /**
     * The function waits for n_replicas - 1 replicas connections.
     *
     * @throws IOException
     */
    private static void acceptReplicas() throws IOException {

        for (int i = 0; i < n_replicas - 1; i++) {
            // wait for replica connection
            Socket repSocket = replicaServerSocket.accept();
            BufferedReader fromRep = new BufferedReader(new InputStreamReader(repSocket.getInputStream()));
            while (true) {
                if (fromRep.ready()) {
                    // save replica read buffer accordance to it's id.
                    int repId = Integer.parseInt(fromRep.readLine());
                    idFromRep.put(repId, fromRep);
                    System.out.println("Replica connected! it's id is: " + repId);
                    break;
                }
            }
        }
    }

    /**
     * The function waits for client connection.
     *
     * @throws IOException
     */
    private static void acceptClient() throws IOException
    {
        clientSocket = replicaServerSocket.accept();
        System.out.println("Client connected!");
        fromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        toClient = new PrintWriter(clientSocket.getOutputStream(), true);
    }

    /**
     * The function handle client requests such as restore and retrieve.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    private static void handleClientReq() throws IOException, InterruptedException {
        Thread broadThread = new Thread(new RepBCHandlerThread(fromBroadcast));
        broadThread.start();

        while (true) {
            if (fromClient.ready()) {
                String[] clientReq = fromClient.readLine().split("#"); // parse client request.
                // perform store protocol.
                if (clientReq[0].equals("Store")) {
                    System.out.println("----Starts Store protocol----");
                    String[] SkValues = clientReq[1].split(";"); // extract Sk
                    SkIJ = SkValues[0].split(",");
                    SkJI = SkValues[1].split(",");
                    String[] SvValues = clientReq[2].split(";"); // extract Sv
                    SvIJ = SvValues[0].split(",");
                    SvJI = SvValues[1].split(",");
                    store();
                    System.out.println("----Finished Store protocol----");
                }
                // perform retrieve protocol.
                else if (clientReq[0].equals("Retrieve")) {
                    System.out.println("----Starts Retrieve protocol----");
                    String[] SdValues = clientReq[1].split(";");
                    SdIJ = SdValues[0].split(","); // extract Sd
                    SdJI = SdValues[1].split(",");
                    R = new String[n_replicas];
                    polyStr = genPolynomial();
                    retrieve();
                    System.out.println("----Finished Retrieve protocol----");
                }
            }
        }
    }

    /**
     * The function preforms retrieve protocol. If the protocol succeeded the replica send to client Sv(0,0),
     * otherwise send to client 0.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    private static void retrieve() throws IOException, InterruptedException {

        System.out.println("----Starts Sharing Random Polynomial----");
        toBroadcast.println("ShareP;" + my_id + ";" + polyStr); // each Replica i shares random polynomial Pi

        Thread.sleep(7000);

        double[] P = new double[2];

        sem.acquire(); // acquire counter and check if header n-f polynomials
        if (countPMsgs >= n_replicas - 1) {
            System.out.println("----Heard n-f values of P----");
            System.out.println("----Creating P locally by Summing all Polynomials Pi----");

        } else {
            System.err.println("----Did not hear n-f values of P----");
            toClient.println("0");
        }

        countPMsgs = 0;
        sem.release();

        // sum all polynomials to create P
        for (int i = 0; i < n_replicas; i++) {
            for (int j = 0; j < 2; j++) {
                P[j] += otherPolys[i][j];
            }
        }


        System.out.println("----Finished Sharing Random Polynomial----");

        double qdi = robustLagrangeInterpolate(SdJI, 1); // calculate Sd(0,i)=qd(i)
        double qki = robustLagrangeInterpolate(SkJI, 1); // calculate Sk(0,i)=qk(i)
        System.out.println("P is " + Arrays.toString(P));

        double pi = evaluate(P, my_id); // calculate P(i)
        double sharedValue = multiply(subtract(qki, qdi), pi); // % field;  calculate (qk(i)-qd(i))*P(i) = R(i)

        System.out.println("qd(" + my_id + ")=" + qdi);
        System.out.println("qk(" + my_id + ")=" + qki);
        System.out.println("P(" + my_id + ")=" + pi);

        Thread.sleep(5000);

        System.out.println("Broadcasting my Ri value"); // broadcast R(i)

        toBroadcast.println("ShareR;" + my_id + ";" + sharedValue);

        Thread.sleep(5000);

        sem2.acquire();
        // wait for n-f values of R.
        if (countRMsgs >= n_replicas - 1) {
            System.out.println("----Heard n-f values of R----");
            System.out.println("----Performing interpolation to calculate R----");
        } else {
            System.err.println("----Did not hear n-f values of R----");
        }
        sem2.release();

        double R0 = robustLagrangeInterpolate(R, 2); // calculate R(0) using lagrange interpolation.
        System.out.println("R(0)=" + R0);
        if (R0 == 0) // if R0 == 0 return qv(i), else 0.
        {
            double myqv = robustLagrangeInterpolate(SvJI, 1);
            System.out.println("qv(" + my_id + "): " + myqv);

            if (byzMode)
                myqv = 666;

            toClient.println(myqv);
        } else {
            toClient.println("0");
        }

        countPMsgs = 0;
        countRMsgs = 0;
    }

    /**
     * The function preforms store protocol. If the protocol succeeded the replica send to client Ok and Ok2.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    private static void store() throws IOException, InterruptedException {
        repsThreadsStatus = new boolean[n_replicas];
        // Set myself is 'done' in Threads Status.
        repsThreadsStatus[my_id] = true;

        // checkDealer values.
        checkDealerDegree();

        // initialize threads for store protocol
        initStoreThreads();

        Thread.sleep(5000);

        // check if all threads finished to work.
        if (checkThreadsStatus()) {
            System.out.println("Broadcasting OK");
            toBroadcast.println("OK;" + my_id); // send OK and wait for n-f Ok's
            Thread.sleep(3000); // was 1000
            if (okCounter < n_replicas - 1) // if didn't hear n-f OK, reset values.
            {
                resetToDefault();
            } else {
                if (!checkPolyDegree(1)) { // check poly degree, if test failed (degree is not 1) reset values.
                    resetToDefault();
                } else {
                    System.out.println("Broadcasting OK2");
                    toBroadcast.println("OK2;" + my_id); // broadcast OK2
                }

                Thread.sleep(3000); // was 1000
                if (ok2Counter < n_replicas - 1) // if didn't hear n-f OK2, reset values.
                {
                    resetToDefault();
                }
            }
        }

        repsThreadsStatus = null;
        countPMsgs = 0;
        countRMsgs = 0;
    }

    /**
     * The function initialize store threads, which helps to replica perform store protocol.
     *
     * @throws InterruptedException
     */
    private static void initStoreThreads() throws InterruptedException
    {

        for (int i = 0; i < n_replicas; i++) {
            if (i != my_id) {
                Thread repThread = new Thread(new ReplicaThread(i, idToRep.get(i), idFromRep.get(i)));
                repThread.start();
            }
        }
    }

    /**
     * Reset Sk and Sv to 0.
     */
    private static void resetToDefault() {
        System.out.println("----Resetting values to Default----");
        SkIJ = new String[]{"0", "0", "0", "0"};
        SkJI = new String[]{"0", "0", "0", "0"};
        SvIJ = new String[]{"0", "0", "0", "0"};
        SvJI = new String[]{"0", "0", "0", "0"};
    }

    /**
     * The function (should) check that my polynomials are of degree 'degree'.
     *
     * @param degree - int represents polynomial degree
     * @return
     */
    private static boolean checkPolyDegree(int degree) {
        // TODO
        return true;
    }

    /**
     * The function checks that dealer values generate polynomials of degree f.
     *
     * @throws InterruptedException
     */
    private static void checkDealerDegree() throws InterruptedException {
        System.out.println("----Start to check values of Sk and Sv----");
        // TODO check polynomial degrees sk and sv
    }

    /**
     * The function check if all threads finished there work.
     *
     * @return True if all store threads finished there work.
     */
    private static boolean checkThreadsStatus()
    {
        return repsThreadsStatus[0] && repsThreadsStatus[1] && repsThreadsStatus[2] ||
                repsThreadsStatus[0] && repsThreadsStatus[1] && repsThreadsStatus[3] ||
                repsThreadsStatus[1] && repsThreadsStatus[2] && repsThreadsStatus[3] ||
                repsThreadsStatus[0] && repsThreadsStatus[2] && repsThreadsStatus[3];
    }

    /**
     * Generate random polynomial of degree f.
     *
     * @return a string representation of the polynomial coefficients.
     */
    private static String genPolynomial()
    {
        myPoly = new int[2];
        myPoly[0] = rand.nextInt(11551) + 1;
        myPoly[1] = rand.nextInt(11551) + 1;
        otherPolys[my_id][0] = myPoly[0];
        otherPolys[my_id][1] = myPoly[1];
        return String.valueOf(myPoly[0]) + ',' + myPoly[1];
    }


    /**
     * Evaluate polynomial with given coefficients on x.
     *
     * @param coef - polynomial coefficients.
     * @param x    - value to evaluate on f.
     * @return - f(x)
     */
    private static double evaluate(double[] coef, double x)
    {
        double p = 0;
        for (int i = coef.length - 1; i >= 0; i--)
            p = add(coef[i], multiply(x, p));
        return p;
    }

    /**
     * The function calculate x*y
     *
     * @param x double
     * @param y double
     * @return x * y
     */
    private static double multiply(double x, double y) {
        return (x * y);
    }

    /**
     * The function calculate x-y
     *
     * @param x double
     * @param y double
     * @return x - y
     */
    private static double subtract(double x, double y) {
        return (x - y);
    }

    /**
     * The function calculate x+y
     *
     * @param x double
     * @param y double
     * @return x + y
     */
    private static double add(double x, double y) {
        return (x + y);
    }


    /**
     * The function calculate lagrange interpolation on x.
     * @param y - String array that contains in each index i' f(i)=yi.
     * @param degree - The degree of f.
     * @return - double - f(x).
     */
    private static double robustLagrangeInterpolate(String[] y, int degree)
    {
        StringBuilder yStr = new StringBuilder();
        yStr.append("python3 Interpolation.py ");
        for (int i = 0; i < y.length; i++)
        {
            yStr.append(y[i]);
            if (i != y.length - 1)
                yStr.append(",");
        }

        yStr.append(" ").append(degree);
        String s = null;
        double out = 0;
        try
        {
            Process p = Runtime.getRuntime().exec(yStr.toString());

            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));

            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));

            // read the output from the command
            System.out.println("----Calculating Robust Lagrange interpolation----");
            while ((s = stdInput.readLine()) != null) {
                out = Double.parseDouble(s);
            }

            // read any errors from the attempted command
            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return out;
    }

}
