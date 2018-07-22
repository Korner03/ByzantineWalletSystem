/**
 * WalletClient is responsible to integrate client requests such as store and retrieve with the system.
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;


public class WalletClient extends Thread
{
    protected static final int n_replicas = 4;
    private static final String discServerIp = "localhost";
    private static final int discServerPort = 4444;
    private static Random rand = new Random();
    private static Socket broadSocket;
    private static BufferedReader fromBroad;
    private static PrintWriter toBroad;
    private static ArrayList<Socket> repSockets = new ArrayList<Socket>();
    private static HashMap<Integer, PrintWriter> idToRep = new HashMap<Integer, PrintWriter>(n_replicas);
    private static HashMap<Integer, Socket> idToSockets = new HashMap<Integer, Socket>(n_replicas);
    protected static boolean[] repOks = new boolean[n_replicas];
    protected static boolean[] repOks2 = new boolean[n_replicas];
    private static int[][] Sd;
    private static int[][] Sk;
    private static int[][] Sv;
    protected static double[][] skValues = new double[n_replicas][n_replicas];
    protected static double[][] svValues = new double[n_replicas][n_replicas];
    private static double[][] sdValues = new double[n_replicas][n_replicas];
    private static String[] skiString = new String[n_replicas];
    private static String[] sviString = new String[n_replicas];
    private static String[] sdiString = new String[n_replicas];
    protected static String[] revealedValues = new String[n_replicas];
    protected static Semaphore revValSem;
    protected static int valCounter = 0;
    protected static int countOks = 0;
    protected static int countOks2 = 0;

    /**
     * Initialize Client by connect him with all the replicas and then wait for client requests.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException
    {
        if (args.length != 2) {
            System.err.println("Usage: <hostname> <port>");
            System.exit(1);
        }

        try {
            System.out.println("----Attempting to connect to Discover Server----");
            getInfoFromDiscoverServer(); // connect to system.

            System.out.println("----Store protocol is ready to initialize----");
            getRequests(); // system is up, handle client requests.

        } catch (UnknownHostException e) {
            System.err.println("Don't know about host");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection");
            System.exit(1);
        } catch (InterruptedException e) {
            System.err.println("Couldn't get I/O for the connection");
            System.exit(1);
        }
    }

    /**
     * The function connects the client to the system
     * @throws IOException
     */
    private static void getInfoFromDiscoverServer() throws IOException {
        Socket discSocket = new Socket(discServerIp, discServerPort); // connect to Discover Server
        BufferedReader fromDisc = new BufferedReader(new InputStreamReader(discSocket.getInputStream()));

        while (true)
        {
            if (fromDisc.ready())
            {
                String info = fromDisc.readLine(); // extract info from Discover Server message
                System.out.println("Received info from Discover server");

                String[] pInfo = info.split(";");

                System.out.println("----Attempting to connect to Replicas----");
                for (int i = 0; i < n_replicas; i++) // connect to all replicas.
                {
                    String[] ppInfo = pInfo[i].split(",");

                    int currRepId = Integer.parseInt(ppInfo[0]);
                    String currRepIp = ppInfo[1];
                    int currPort = Integer.parseInt(ppInfo[2]);

                    connectToOneReplica(currRepId, currRepIp, currPort);
                }

                String[] bcInfo = pInfo[n_replicas].split(",");

                System.out.println("----Attempting to connect to Broadcast Server----");
                connectToBroadcastServer(bcInfo[0], Integer.parseInt(bcInfo[1])); // connect to Broadcast-Server
                break;
            }
        }
        discSocket.close();
        System.out.println("----Closed Discover Server socket----");
    }

    /**
     * The function connect client to broadcast-server
     * @param ip - broadcast server ip
     * @param port - broadcast-server port
     * @throws IOException
     */
    private static void connectToBroadcastServer(String ip, int port) throws IOException
    {
        broadSocket = new Socket(ip, port);
        System.out.println("Connected to Broadcast server!");
        toBroad = new PrintWriter(broadSocket.getOutputStream(), true);
        fromBroad = new BufferedReader(new InputStreamReader(broadSocket.getInputStream()));
    }

    /**
     * The function connects client to Replica in the system.
     * @param id - Replica id
     * @param ip - Replica ip
     * @param port - Replica port
     * @throws IOException
     */
    private static void connectToOneReplica(int id, String ip, int port) throws IOException
    {
        Socket currRepSocket = new Socket(ip, port);
        System.out.println("Connected to Replica with id: " + id);

        PrintWriter toRep = new PrintWriter(currRepSocket.getOutputStream(), true);
        idToRep.put(id, toRep);
        repSockets.add(currRepSocket);
        idToSockets.put(id, currRepSocket);
    }

    /**
     * The function get client requests:
     *  1. store
     *  2. retrieve
     * @throws IOException
     * @throws InterruptedException
     */
    private static void getRequests() throws IOException, InterruptedException
    {
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

        while (true)
        {
            System.out.println("Enter 1 for Store Or 2 for Retrieve");

            int fromClient = Integer.parseInt(stdIn.readLine());
            switch (fromClient) {
                case 1:
                    store();
                    break;
                case 2:
                    retrieve();
                    break;
                default:
                    System.out.println("Unsupported action");
            }
        }
    }

    /**
     * The function sends to Replica 'id' Sd values.
     * @param id - Replica Id
     */
    private static void sendReplicasSd(int id)
    {
        String SdValues = generateSValues(Sd, id, 2);

        sdiString[id] = SdValues;

        idToRep.get(id).println("Retrieve#" + SdValues);
    }

    /**
     * The function performs retrieve protocol from client (dealer) view.
     * @throws IOException
     * @throws InterruptedException
     */
    private static void retrieve() throws IOException, InterruptedException
    {
        revValSem = new Semaphore(1);
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Enter Key: ");
        int key = Integer.parseInt(stdIn.readLine());
        Sd = genPolynomial(key); // generate polynomial Sd such that Sd(0,0)=key

        for (int i = 0; i < n_replicas; i++) // send each replica Sd
        {
            sendReplicasSd(i);
        }

        System.out.println("----Please wait while the system is retrieving your value----");

        for (int i = 0; i < n_replicas; i++) // initialize thread to handle each Replica requests
        {
            Thread currRepThread = new Thread(new WalletClientThread(idToSockets.get(i), i));
            currRepThread.start();
        }

        while (true) // wait for n-f points
        {
            revValSem.acquire();
            if (valCounter >= n_replicas-1)
            {
                revValSem.release();
                break;
            }
            revValSem.release();
        }

        System.out.println("----Performing interpolation to Induce qv----");
        System.out.println("Received qv values from replicas: " + Arrays.toString(revealedValues));

        // perform lagrange interpolation on the given points.
        double value = robustLagrangeInterpolate(revealedValues, 1);
        if (value == 0)
        {
            System.out.println("----Retrieve protocol Failed----");
        } else {
            System.out.println("----Retrieve protocol Finished Successfully----");
            System.out.println("Your value is: " + value);
        }
        valCounter = 0;
        revealedValues = new String[n_replicas];
    }

    /**
     * The function performs store protocol from client (dealer) view.
     * @throws IOException
     * @throws InterruptedException
     */
    private static void store() throws IOException, InterruptedException
    {
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        Thread clientStoreThread = new Thread(new WalletClientStoreThread(fromBroad, toBroad));
        clientStoreThread.start();
        repOks = new boolean[n_replicas];
        repOks2 = new boolean[n_replicas];
        countOks = 0;
        countOks2 = 0;

        System.out.println("Enter Key: ");

        int key = Integer.parseInt(stdIn.readLine());

        System.out.println("Enter Value: ");

        int value = Integer.parseInt(stdIn.readLine());

        Sk = genPolynomial(key); // generate polynomial such that Sk(0,0)=key
        Sv = genPolynomial(value); // generate polynomial such that Sv(0,0)=value

        for (int i = 0; i < n_replicas; i++) // send values to replicas.
        {
            sendReplicaValues(Sk, Sv, i);
        }

        Thread.sleep(7500);
        // if OK check passed, check if we need to humiliate some replica..
        if (listenOks())
        {
            for (int i = 0; i < n_replicas; i++ )
            {
                if (!repOks[i]) { // Humiliate replica that is not OK.
                    toBroad.println("Humiliate;" + i + ";" + skiString[i] + ';' + sviString[i]);
                }
            }
        } else {
            System.out.println("Store Failed! Please try again");
        }
        Thread.sleep(7500);
        // Kill store thread.
        clientStoreThread.interrupt();
        // Check if heard n-f Ok2 messages. If check passed the store protocol succeeded
        if (listenOks2()) {
            System.out.println("Store Success! your key is: " + key + ". Please don't lose it =D");
        } else {
            System.out.println("Store Failed, Please try again");
        }
    }

    /**
     * The function checks if the first step of the Store protocol succeeded
     * @return true if the first step of store protocol succeeded, otherwise false.
     * @throws IOException
     * @throws InterruptedException
     */
    private static boolean listenOks() throws IOException, InterruptedException
    {
        System.out.println("----Dealer checks n-f OK messages----");
        return countOks >= n_replicas -1;
    }

    /**
     * The function checks if the second step of Store protocol succeeded
     * @return true if the second step of store protocol succeeded, otherwise false.
     * @throws IOException
     */
    private static boolean listenOks2() throws IOException
    {
        System.out.println("----Dealer checks n-f OK2 messages----");
        return countOks2 >= n_replicas -1;
    }


    /**
     * Generate random polynomial S(x,y) such that S(0,0)=key.
     * S is of degree 1 in x and y.
     * @param key - secret to hide in the polynomial
     * @return
     */
    private static int[][] genPolynomial(int key)
    {
        int[][] coefficients = new int[2][2];
        coefficients[0][0] = 1;
        coefficients[0][1] = rand.nextInt(11551) + 1;
        coefficients[1][0] = key;
        coefficients[1][1] = rand.nextInt(11551) + 1;
        return coefficients;
    }

    /**
     * Send Replica 'id' Sk and Sv.
     * @param Sk - coefficients of Sk
     * @param Sv - coefficients of Sv
     * @param id - Replica id
     */
    private static void sendReplicaValues(int[][] Sk, int[][] Sv, int id)
    {
        String SkValues = generateSValues(Sk, id, 0);
        String SvValues = generateSValues(Sv, id, 1);

        skiString[id] = SkValues;
        sviString[id] = SvValues;

        idToRep.get(id).println("Store#" + SkValues + '#' + SvValues);
    }


    private static String generateSValues(int[][] coef, int id, int identifier)
    {
        StringBuilder builderIJ = new StringBuilder();
        StringBuilder builderJI = new StringBuilder();

        for (int j = 0; j < n_replicas; j++)
        {
            double tmp1 = evaluatePolynomial(coef, id + 1, j + 1);
            builderIJ.append(tmp1).append(',');
            double tmp2 = evaluatePolynomial(coef, j + 1, id + 1);
            builderJI.append(tmp2).append(',');

            if (identifier == 0) {
                skValues[id][j] = tmp1;
                skValues[j][id] = tmp2;
            } else if (identifier == 1) {
                svValues[id][j] = tmp1;
                svValues[j][id] = tmp2;
            } else {
                sdValues[id][j] = tmp1;
                sdValues[j][id] = tmp2;
            }
        }

        builderIJ.deleteCharAt(builderIJ.lastIndexOf(","));
        builderJI.deleteCharAt(builderJI.lastIndexOf(","));
        builderIJ.append(';').append(builderJI.toString());
        return builderIJ.toString();
    }

    /**
     * Evaluate S(x,y) which represented by coefficients on x and y.
     * @param coefficients - S coefficients
     * @param x - x value to evaluate on S.
     * @param y - y value to evaluate on S.
     * @return S(x,y)
     */
    private static double evaluatePolynomial(int[][] coefficients, int x, int y)
    {
        double y1 = evaluate(coefficients[0], x);
        double y2 = evaluate(coefficients[1], y);
        return multiply(y1, y2);
    }

    /**
     * Evaluate polynomial in 1 dimension.
     * @param coef - polynomial coefficients
     * @param x - value to evaluate on the polynomial f
     * @return f(x)
     */
    private static double evaluate(int[] coef, int x)
    {
        double p = 0;
        for (int i = coef.length-1; i >= 0; i--)
            p = add(coef[i], (multiply(x,p)));
        return p;
    }

    /**
     * The function calculate x*y
     * @param x double
     * @param y double
     * @return x * y
     */
    private static double multiply(double x, double y)
    {
        return (x*y);
    }

    /**
     * The function calculate x+y
     * @param x double
     * @param y double
     * @return x + y
     */
    private static double add(double x, double y)
    {
        return (x+y);
    }


    /**
     * The function calculate lagrange interpolation on x.
     * @param y - String array that contains in each index i' f(i)=yi.
     * @param degree - The degree of f.
     * @return - double - f(x).
     */
    private static double robustLagrangeInterpolate(String[] y, int degree) throws IOException
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

        Process p = Runtime.getRuntime().exec(yStr.toString());

        BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
        BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

        // read the output from the command
        System.out.println("----Calculting Robust Lagrange interpolation----");
        while ((s = stdInput.readLine()) != null) {
            out = Double.parseDouble(s);
        }

        // read any errors from the attempted command
        while ((s = stdError.readLine()) != null) {
            System.out.println(s);
        }

        return out;
    }
}

