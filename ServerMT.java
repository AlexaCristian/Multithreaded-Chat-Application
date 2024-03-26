import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMT implements Runnable {
    private ArrayList<ConnectionHandler> connections;
    private ServerSocket server;
    private boolean done;
    private ExecutorService pool;

    public ServerMT() {
        connections = new ArrayList<>();
        done = false;
    }

    @Override
    public void run() {
        try {
            server = new ServerSocket(4444);
            pool = Executors.newCachedThreadPool();
            while (!done) {
                Socket client = server.accept(); // Acceptă orice client
                if (connections.size() >= 4) {
                    // Serverul este plin, scoate primul client conectat pentru a face loc
                    ConnectionHandler toBeRemoved = connections.get(0); // Alege primul client conectat

                    toBeRemoved.shutDown("Server is full. You have been disconnected to make room for new connections."); // Deconectează clientul
                }
                // Procedează cu adăugarea noului client
                ConnectionHandler handler = new ConnectionHandler(client);
                connections.add(handler);
                pool.execute(handler);
            }
        }catch (Exception e) {
            broadcast("Server is closing...");
            shutDown();
        }
    }

    public void broadcast(String message) {
        for (ConnectionHandler ch : connections) {
            if (ch != null) {
                ch.sendMessage(message);
            }
        }
    }

    public void shutDown() {
        try {
            done = true;
            if (!server.isClosed()) {
                System.out.println("Server closing...");
                server.close();
                for (ConnectionHandler ch : connections) {
                    ch.shutDown();
                }
            }
        } catch (IOException e) {
            //ignore
        }
    }

    class ConnectionHandler implements Runnable {

        private Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        public ConnectionHandler(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                // Prompt the client for a nickname.
                out.println("Please enter a nickname: ");
                username = in.readLine();
                while (username.isEmpty() || !username.matches("[a-zA-Z]+")) {
                    out.println("Invalid username, only alphabets are allowed. Please enter a valid nickname: ");
                    username = in.readLine();
                }

                // Notify all other clients that a new client has joined.
                broadcast(username + " joined the chat!");

//                // This message goes back to the client who just connected.
//                out.println(username + " joined the chat!");
                System.out.println(username + " connected!");

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("/nick")) {
                        String[] messageSplit = message.split(" ", 2);
                        if (messageSplit.length == 2 && messageSplit[1].matches("[a-zA-Z]+")) {
                            broadcast(username + " renamed themselves to : " + messageSplit[1]);
                            System.out.println(username + " renamed themselves to : " + messageSplit[1]);
                            username = messageSplit[1];
                            out.println("Successfully changed username to : " + username);
                        } else {
                            out.println("No username provided!");
                        }
                    } else if (message.startsWith("/quit")) {
                        System.out.println(username + " left the server");
                        shutDown();
                        broadcast(username + " left the server!");
                        break;
                    } else {
                        broadcast(username + ": " + message);
                    }
                }


            } catch (IOException e) {
                shutDown();
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }


        public void shutDown(String... message) {
            try {
                if (message.length > 0) {
                    sendMessage(message[0]); // Trimite un mesaj clientului înainte de a-l deconecta
                }
                in.close();
                out.close();
                if (!client.isClosed()) {
                    client.close();
                }
            } catch (IOException e) {
                //ignore
            } finally {
                ServerMT.this.removeConnection(this); // Îndepărtează conexiunea curentă din listă
            }
        }

    }
    public synchronized void removeConnection(ConnectionHandler ch) {
        connections.remove(ch);
        System.out.println("A connection has been removed. Total connections: " + connections.size());
    }

    public static void main(String[] args) {
        ServerMT server = new ServerMT();
        server.run();
    }
}
