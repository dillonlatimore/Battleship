/*  
*   Dillon Latimore
*   SENG4500
*   3142759
*   Battleship.java
*/

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.BindException;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.util.Random;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Battleship {
    static Random rand = new Random();
    static String myPort = Integer.toString(rand.nextInt(1000) + 5000);
    static volatile boolean connected = false;

    // 2d arrays for own and enemy board
    static char[][] myBoard = new char[10][10];
    static char[][] enemyBoard = new char[10][10];

    // arrays of each ships postion coordinates
    static String[] acPos = new String[5];
    static String[] bsPos = new String[4];
    static String[] crPos = new String[3];
    static String[] sbPos = new String[3];
    static String[] paPos = new String[2];

    // array of hit parts of ships
    static boolean[] acHit = new boolean[5];
    static boolean[] bsHit = new boolean[4];
    static boolean[] crHit = new boolean[3];
    static boolean[] sbHit = new boolean[3];
    static boolean[] paHit = new boolean[2];

    // array of ships that are sunk
    static boolean[] shipSunk = new boolean[5];

    public static void main(String args[]) {

        String bAdd = args[0];
        int bPort = Integer.parseInt(args[1]);
        System.out.println("My port: " + myPort);
        Thread t = new Thread(new Runner());
        // Start thread to wait for tcp connection
        t.start();

        // 192.168.0.255
        try {
            String myIp = InetAddress.getLocalHost().getHostAddress();
            System.out.println(myIp);

            InetAddress addr = InetAddress.getByName(bAdd);
            // setup udp
            MulticastSocket socket = new MulticastSocket(bPort);

            // You can change this if you dont want to wait for 30 seconds for connection
            socket.setSoTimeout(3000);

            while (true) {
                if (connected) {
                    break;
                }
                byte[] buffer = new byte[50];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                // wait for udp message
                try {
                    System.out.println("Waiting for message");
                    socket.receive(packet);
                    String udpPacket = new String(packet.getData()).trim();
                    System.out.println("Received " + udpPacket);
                    String[] tokens = udpPacket.split(":");

                    // If message is own port number
                    if (tokens[0].equals("NEW PLAYER") && tokens[1].equals(myPort)) {
                        System.out.println("I got my own port");
                        continue;
                    }
                    // If received SYN message to start connection
                    else if (tokens[0].equals("SYN")) {
                        System.out.println("Received SYN section");

                        // send ip address
                        String ipMsg = "IP:" + myIp;
                        byte[] byteArr = ipMsg.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket outPacket = new DatagramPacket(byteArr, byteArr.length, addr, 20000);
                        System.out.println("Sending ip address");
                        socket.send(outPacket);
                        Thread.sleep(10000);

                    }

                    // If received new player message
                    else if (tokens[0].equals("NEW PLAYER") && tokens[1] != myPort) {

                        // create SYN message to send back
                        byte[] byteArr = { 'S', 'Y', 'N', ':' };
                        DatagramPacket outPacket = new DatagramPacket(byteArr, 4, addr, 20000);
                        System.out.println("sending SYN");
                        socket.send(outPacket);

                        Thread.sleep(2000);

                        for (int i = 0; i < 20; i++) {
                            System.out.println("in loop");
                            byte[] bufferNew = new byte[50];
                            DatagramPacket newPacket = new DatagramPacket(bufferNew, bufferNew.length);
                            socket.receive(newPacket);
                            String udpPacket1 = new String(newPacket.getData()).trim();
                            System.out.println(udpPacket1);

                            if (udpPacket1.contains("IP")) {
                                String theIP = udpPacket1.substring(3);
                                System.out.println("theIP " + theIP + "+++");

                                // try tcp connection as client
                                for (int j = 0; j < 200; j++) {
                                    try (
                                            Socket s = new Socket(theIP, Integer.parseInt(tokens[1]));
                                            BufferedReader in = new BufferedReader(
                                                    new InputStreamReader(s.getInputStream()));
                                            PrintWriter out = new PrintWriter(s.getOutputStream(), true);) {

                                        socket.close();
                                        // setup own and enemy boards
                                        for (int k = 0; k < 10; k++) {
                                            for (int l = 0; l < 10; l++) {
                                                myBoard[k][l] = 'o';
                                                enemyBoard[k][l] = 'o';
                                            }
                                        }

                                        // display the boards
                                        placeShip(0);
                                        placeShip(1);
                                        placeShip(2);
                                        placeShip(3);
                                        placeShip(4);
                                        printBoard();

                                        // game loop
                                        while (true) {

                                            if (takeFire(in, out)) {
                                                try {
                                                    s.close();
                                                    System.exit(0);
                                                } catch (Exception e) {
                                                    // TODO: handle exception
                                                }
                                            }

                                            if (fire(in, out)) {
                                                try {
                                                    s.close();
                                                    System.exit(0);
                                                } catch (Exception e) {
                                                    // TODO: handle exception
                                                }
                                            }

                                        }

                                    } catch (Exception e) {
                                        System.out.println("couldnt join socket");
                                    }
                                    Thread.sleep(100);
                                }
                            }

                        }

                    }

                }
                // if udp wait times out now send udp message
                catch (SocketTimeoutException e) {
                    System.out.println("Timed out");

                    String es = "NEW PLAYER:" + myPort;
                    System.out.println("UDP port im sending " + es);
                    byte[] byteArr = es.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket outPacket = new DatagramPacket(byteArr, byteArr.length, addr, 20000);
                    System.out.println("Sending UDP packet");
                    socket.send(outPacket);

                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // place ships on board
    public static void placeShip(int ship) {

        int shipLength;
        char shipChar;

        // aircraft carrier
        if (ship == 0) {
            shipLength = 5;
            shipChar = 'A';
            System.out.println("Aicraft carrier===========");
        }
        // battleship
        else if (ship == 1) {
            shipLength = 4;
            shipChar = 'B';
            System.out.println("Battleship=====================");
        }
        // cruiser
        else if (ship == 2) {
            shipLength = 3;
            shipChar = 'C';
            System.out.println("Cruiser=========================");
        }
        // submarine
        else if (ship == 3) {
            shipLength = 3;
            shipChar = 'S';
            System.out.println("Submarine=======================");
        }
        // patrol boat
        else {
            shipLength = 2;
            shipChar = 'P';
            System.out.println("Patrol=====================");
        }

        boolean shipPlaced = false;
        while (!shipPlaced) {
            // select random coordinate
            Random rand = new Random();
            int row = rand.nextInt(10);
            int col = rand.nextInt(10);
            System.out.println("random coord is: " + (row + 1) + ", " + (col + 1));
            // place letter for ship there
            if (myBoard[row][col] == 'o') {
                myBoard[row][col] = shipChar;
                recordShipPos(ship, row, col);
            } else {
                continue;
            }

            // choose random direction
            int direction = rand.nextInt(4);
            // up
            if (direction == 0) {
                System.out.println("Direction: UP");
                row -= 1;
                for (int i = 0; i < shipLength - 1; i++) {
                    if (row >= 0 && myBoard[row][col] == 'o') {
                        myBoard[row][col] = shipChar;
                        recordShipPos(ship, row, col);
                        row -= 1;
                        if (i == shipLength - 2) {
                            shipPlaced = true;
                        }
                    } else {
                        removeShipPos(ship);
                        for (int j = 0; j < 10; j++) {
                            for (int k = 0; k < 10; k++) {
                                if (myBoard[j][k] == shipChar) {
                                    myBoard[j][k] = 'o';
                                }

                            }
                        }
                        break;
                    }
                }
            }
            // right
            else if (direction == 1) {
                System.out.println("Direction: RIGHT");
                col += 1;
                for (int i = 0; i < shipLength - 1; i++) {
                    if (col <= 9 && myBoard[row][col] == 'o') {
                        myBoard[row][col] = shipChar;
                        recordShipPos(ship, row, col);
                        col += 1;
                        if (i == shipLength - 2) {
                            shipPlaced = true;
                        }
                    } else {
                        removeShipPos(ship);
                        for (int j = 0; j < 10; j++) {
                            for (int k = 0; k < 10; k++) {
                                if (myBoard[j][k] == shipChar) {
                                    myBoard[j][k] = 'o';
                                }

                            }
                        }

                        break;
                    }
                }
            }
            // down
            else if (direction == 2) {
                System.out.println("Direction: DOWN");
                row += 1;
                for (int i = 0; i < shipLength - 1; i++) {
                    if (row <= 9 && myBoard[row][col] == 'o') {
                        myBoard[row][col] = shipChar;
                        recordShipPos(ship, row, col);
                        row += 1;
                        if (i == shipLength - 2) {
                            shipPlaced = true;
                        }
                    } else {
                        removeShipPos(ship);
                        for (int j = 0; j < 10; j++) {
                            for (int k = 0; k < 10; k++) {
                                if (myBoard[j][k] == shipChar) {
                                    myBoard[j][k] = 'o';
                                }

                            }
                        }
                        break;
                    }
                }

            }
            // left
            else {
                System.out.println("Direction: LEFT");
                col -= 1;
                for (int i = 0; i < shipLength - 1; i++) {
                    if (col >= 0 && myBoard[row][col] == 'o') {
                        myBoard[row][col] = shipChar;
                        recordShipPos(ship, row, col);
                        col -= 1;
                        if (i == shipLength - 2) {
                            shipPlaced = true;
                        }
                    } else {
                        removeShipPos(ship);
                        for (int j = 0; j < 10; j++) {
                            for (int k = 0; k < 10; k++) {
                                if (myBoard[j][k] == shipChar) {
                                    myBoard[j][k] = 'o';
                                }

                            }
                        }
                        break;
                    }
                }

            }

        }
    }

    // record ship positions as placed to check for hits
    public static void recordShipPos(int whichShip, int row, int col) {

        int newRow = row + 65;
        char rowChar = (char) newRow;
        int newCol = col + 1;
        String pos = rowChar + Integer.toString(newCol);
        System.out.println("position is " + pos);

        if (whichShip == 0) {
            for (int i = 0; i < acPos.length; i++) {
                if (acPos[i] == null) {
                    acPos[i] = pos;
                    break;
                }
            }
        } else if (whichShip == 1) {
            for (int i = 0; i < bsPos.length; i++) {
                if (bsPos[i] == null) {
                    bsPos[i] = pos;
                    break;
                }
            }
        } else if (whichShip == 2) {
            for (int i = 0; i < crPos.length; i++) {
                if (crPos[i] == null) {
                    crPos[i] = pos;
                    break;
                }
            }
        } else if (whichShip == 3) {
            for (int i = 0; i < sbPos.length; i++) {
                if (sbPos[i] == null) {
                    sbPos[i] = pos;
                    break;
                }
            }
        } else if (whichShip == 4) {
            for (int i = 0; i < paPos.length; i++) {
                if (paPos[i] == null) {
                    paPos[i] = pos;
                    break;
                }
            }
        }
    }

    // if invalid ship placement removes the ship positions from array
    public static void removeShipPos(int whichShip) {
        if (whichShip == 0) {
            for (int i = 0; i < acPos.length; i++) {
                acPos[i] = null;
            }
        } else if (whichShip == 1) {
            for (int i = 0; i < bsPos.length; i++) {
                bsPos[i] = null;
            }
        } else if (whichShip == 2) {
            for (int i = 0; i < crPos.length; i++) {
                crPos[i] = null;
            }
        } else if (whichShip == 3) {
            for (int i = 0; i < sbPos.length; i++) {
                sbPos[i] = null;
            }
        } else if (whichShip == 4) {
            for (int i = 0; i < paPos.length; i++) {
                paPos[i] = null;
            }
        }

    }

    // displays boards for player
    public static void printBoard() {

        System.out.println("in print baord");
        int currentAscii = 65;
        char c;
        System.out.println("                            My Radar");
        System.out.println("    =====================================================================");
        System.out.println("       1      2      3      4      5      6      7      8      9      10");
        System.out.println("    =====================================================================");

        for (int i = 0; i < 10; i++) {
            // System.out.println(" | ");

            for (int j = 0; j < 10; j++) {
                if (j == 0) {
                    c = (char) currentAscii;
                    System.out.print(" " + c + " |  ");
                }
                if (enemyBoard[i][j] != 'o') {
                    System.out.print("{" + enemyBoard[i][j] + "}    ");
                } else {
                    System.out.print(" " + enemyBoard[i][j] + "     ");
                }

            }
            System.out.println();
            currentAscii++;
            // System.out.println("
            // |----------------------------------------------------------");
            System.out.println("   |  ");

        }

        currentAscii = 65;
        System.out.println();
        System.out.println();

        System.out.println("                            My Board");
        System.out.println("    =====================================================================");
        System.out.println("       1      2      3      4      5      6      7      8      9      10");
        System.out.println("    =====================================================================");

        for (int i = 0; i < 10; i++) {
            // System.out.println(" | ");

            for (int j = 0; j < 10; j++) {
                if (j == 0) {
                    c = (char) currentAscii;
                    System.out.print(" " + c + " |  ");
                }
                if (myBoard[i][j] != 'o' && myBoard[i][j] != 'X') {
                    System.out.print("{" + myBoard[i][j] + "}    ");
                } else {
                    System.out.print(" " + myBoard[i][j] + "     ");
                }

            }
            System.out.println();
            currentAscii++;
            // System.out.println("
            // |----------------------------------------------------------");
            System.out.println("   |  ");

        }

    }

    // fire logic in game loop
    public static boolean fire(BufferedReader in, PrintWriter out) {
        boolean finishGame = false;
        Scanner scan = new Scanner(System.in);
        String msg;

        try {

        } catch (Exception e) {
            // TODO: handle exception
        }

        while (true) {
            // prompt for FIRE
            System.out.println("Enter FIRE coordinates.");
            msg = scan.nextLine();

            // check that msg is in right range
            int coordInt1 = (int) msg.charAt(0);
            int coordInt2 = Integer.parseInt(msg.substring(1));
            if (coordInt1 >= 65 && coordInt1 <= 74 && coordInt2 >= 1 &&
                    coordInt2 <= 10) {
                // send fire coordinates
                out.println("FIRE:" + msg);
                System.out.println("sending " + msg);
                break;
            } else {
                // retry prompt
                System.out.println("Message not in correct form. Try again.");
                continue;
            }
        }

        // wait for response and update board or end game

        try {
            System.out.println("Waiting for response");
            while ((msg = in.readLine()) != null) {
                String[] tokens = msg.split(":");
                if (tokens[0].equals("MISS")) {
                    markBoard(enemyBoard, tokens[1], 'X');
                    printBoard();
                    System.out.println("Received MISS:" + tokens[1]);
                } else if (tokens[0].equals("HIT")) {
                    markBoard(enemyBoard, tokens[1], 'H');
                    printBoard();
                    System.out.println("Received HIT:" + tokens[1]);
                } else if (tokens[0].equals("SUNK")) {
                    markBoard(enemyBoard, tokens[1], 'H');
                    printBoard();
                    System.out.println("Received SUNK:" + tokens[1] + ":" + tokens[2]);
                } else if (tokens[0].equals("GAME OVER")) {
                    markBoard(enemyBoard, tokens[1], 'H');
                    printBoard();
                    System.out.println("Received GAME OVER:" + tokens[1] + ":" + tokens[2]);
                    System.out.println("YOU WIN!!!");
                    finishGame = true;
                    // CLOSE CONNECTION
                }
                break;
            }

        } catch (Exception e) {

        }

        return finishGame;
    }

    // mark board with chosen char
    public static void markBoard(char[][] whichBoard, String coord, char markWith) {
        System.out.println(coord);
        int coord1 = (int) coord.charAt(0);
        System.out.println(coord1);
        int coord2 = Integer.parseInt(coord.substring(1));
        System.out.println(coord2);
        coord1 -= 65;
        coord2 -= 1;
        whichBoard[coord1][coord2] = markWith;
    }

    // take fire logic for game
    public static boolean takeFire(BufferedReader in, PrintWriter out) {
        boolean finishGame = false;
        String msg = "";
        // wait for enemy fire and respond to MISS, HIT, SUNK or GAME OVER
        System.out.println("Waiting for enemy fire");

        try {
            while ((msg = in.readLine()) != null) {

                String[] tokens = msg.split(":");

                // get coord from message

                int coordRow = (int) tokens[1].charAt(0) - 65;
                int coordCol = Integer.parseInt(tokens[1].substring(1)) - 1;

                System.out.println(coordRow);
                System.out.println(coordCol);

                // MISS
                if (myBoard[coordRow][coordCol] == 'o') {
                    System.out.println("in miss not hit before");
                    markBoard(myBoard, tokens[1], 'X');
                    printBoard();
                    msg = "MISS:" + tokens[1];
                    System.out.println(msg);
                    out.println(msg);
                }
                // Repeat coordinate
                else if (myBoard[coordRow][coordCol] == 'X') {
                    System.out.println("in miss has hit before");
                    System.out.println("They've already hit that coordinate.");
                    printBoard();
                    msg = "MISS:" + tokens[1];
                    System.out.println(msg);
                    out.println(msg);
                }
                // HIT
                else {
                    System.out.println("in hit");
                    String[] returned = checkArrays(coordRow, coordCol, tokens[1]).split(":");
                    System.out.println("returned is " + returned[0] + " " + returned[1]);
                    String shipName = "";
                    if (returned[1].equals("0")) {
                        shipName = "Aircraft Carrier";
                    } else if (returned[1].equals("1")) {
                        shipName = "Battleship";
                    } else if (returned[1].equals("2")) {
                        shipName = "Cruiser";
                    } else if (returned[1].equals("3")) {
                        shipName = "Submarine";
                    } else if (returned[1].equals("4")) {
                        shipName = "Patrol Boat";
                    }
                    markBoard(myBoard, tokens[1], 'H');
                    printBoard();
                    // take action based on checkArrays
                    if (returned[0].equals("HIT")) {
                        // send hit
                        msg = "HIT:" + tokens[1];
                        System.out.println(msg);
                        out.println(msg);
                    } else if (returned[0].equals("SUNK")) {
                        // send sunk
                        msg = "SUNK:" + tokens[1] + ":" + shipName;
                        System.out.println(msg);
                        out.println(msg);
                    } else if (returned[0].equals("GAME OVER")) {
                        msg = "GAME OVER:" + tokens[1] + ":" + shipName;
                        System.out.println(msg);
                        out.println(msg);
                        // close connection
                        finishGame = true;
                    }
                }
                break;
            }

        } catch (Exception e) {

        }
        return finishGame;
    }

    // checks arrays for a hit, sunk or game over
    public static String checkArrays(int coordRow, int coordCol, String coord) {
        System.out.println("in check arrays");
        System.out.println("Coord: " + coord);
        boolean sunk = true;
        int shipInt = 5;
        if (myBoard[coordRow][coordCol] == 'A') {
            System.out.println("in aircraft");
            // find matching coord in coord array
            for (int i = 0; i < acPos.length; i++) {
                if (coord.equals(acPos[i])) {
                    acHit[i] = true;
                }
                System.out.println("got here");

            }

            System.out.println("about to start sunk loop");
            // check if ship is sunk
            for (int j = 0; j < acHit.length; j++) {
                System.out.println("sunk loop " + j);
                if (acHit[j] == false) {
                    sunk = false;
                    break;
                }
            }
            if (sunk) {
                shipSunk[0] = true;
                shipInt = 0;
            }
        }

        else if (myBoard[coordRow][coordCol] == 'B') {
            System.out.println("in battleship");
            // find matching coord in coord array
            for (int i = 0; i < bsPos.length; i++) {
                if (coord.equals(bsPos[i])) {
                    bsHit[i] = true;

                }

            }

            System.out.println("about to start bs sunk loop");
            // check if ship is sunk
            for (int j = 0; j < bsHit.length; j++) {
                if (bsHit[j] == false) {
                    sunk = false;
                    break;
                }
            }
            if (sunk) {
                shipSunk[1] = true;
                shipInt = 1;
            }
        }

        else if (myBoard[coordRow][coordCol] == 'C') {
            System.out.println("in cruiser");
            // find matching coord in coord array
            for (int i = 0; i < crPos.length; i++) {
                if (coord.equals(crPos[i])) {
                    crHit[i] = true;

                }

            }
            // check if ship is sunk
            for (int j = 0; j < crHit.length; j++) {
                if (crHit[j] == false) {
                    sunk = false;
                    break;
                }
            }
            if (sunk) {
                shipSunk[2] = true;
                shipInt = 2;
            }
        }

        else if (myBoard[coordRow][coordCol] == 'S') {
            System.out.println("in submarine");
            // find matching coord in coord array
            for (int i = 0; i < sbPos.length; i++) {
                if (coord.equals(sbPos[i])) {
                    sbHit[i] = true;

                }

            }
            // check if ship is sunk
            for (int j = 0; j < sbHit.length; j++) {
                if (sbHit[j] == false) {
                    sunk = false;
                    break;
                }
            }
            if (sunk) {
                shipSunk[3] = true;
                shipInt = 3;
            }
        }

        else if (myBoard[coordRow][coordCol] == 'P') {
            System.out.println("in patrol b");
            // find matching coord in coord array
            for (int i = 0; i < paPos.length; i++) {
                if (coord.equals(paPos[i])) {
                    paHit[i] = true;

                }

            }
            // check if ship is sunk
            for (int j = 0; j < paHit.length; j++) {
                if (paHit[j] == false) {
                    sunk = false;
                    break;
                }
            }
            if (sunk) {
                shipSunk[4] = true;
                shipInt = 4;
            }
        }

        // if sunk check if all ships are sunk
        if (sunk) {

            boolean allSunk = true;
            // check if game over
            for (int i = 0; i < shipSunk.length; i++) {
                if (shipSunk[i] == false) {
                    allSunk = false;
                }
            }
            if (allSunk) {
                // return GAME OVER
                System.out.println("returning gameover");
                return "GAME OVER:" + shipInt;
            } else {
                System.out.println("returning sunk");
                return "SUNK:" + shipInt;
            }
        } else {
            System.out.println("returning HIT");
            return "HIT:" + shipInt;
        }

    }
}

// other thread that waits for tcp connection, this is the server thread
class Runner implements Runnable {
    public void run() {
        System.out.println("thread started");

        try (
                ServerSocket ss = new ServerSocket(Integer.parseInt(a2.myPort));) {
            Socket s = ss.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);

            synchronized (this) {
                a2.connected = true;
            }
            System.out.println("connection made");

            // setup own and enemy boards
            for (int k = 0; k < 10; k++) {
                for (int l = 0; l < 10; l++) {
                    a2.myBoard[k][l] = 'o';
                    a2.enemyBoard[k][l] = 'o';
                }
            }

            // display the boards
            a2.placeShip(0);
            a2.placeShip(1);
            a2.placeShip(2);
            a2.placeShip(3);
            a2.placeShip(4);
            a2.printBoard();

            while (true) {
                if (a2.fire(in, out)) {
                    try {
                        Thread.sleep(3000);
                        ss.close();
                        System.exit(0);
                    } catch (Exception e) {
                        // TODO: handle exception
                    }
                }

                if (a2.takeFire(in, out)) {
                    try {
                        Thread.sleep(3000);
                        ss.close();
                        System.exit(0);
                    } catch (Exception e) {
                        // TODO: handle exception
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();

        }
    }
}