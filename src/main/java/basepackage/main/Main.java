package basepackage.main;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.*;

class Main
{
    static final String URL = "https://socket-v3.bittrex.com/signalr";
    static final String API_KEY = "";
    static final String API_SECRET = "";
    public static void main(String[] args)
            throws Exception
    {
        final SocketClient client = new SocketClient(URL);
        if (!connect(client)) {
            throw new Exception("Could not connect to server");
        }

        if (!API_SECRET.isEmpty()) {
            authenticateClient(client);
        } else {
            System.out.println("Authentication skipped because API key was not provided");
        }

        subscribe(client);
    }

    static Boolean connect(SocketClient client)
    {
        boolean connected = false;
        try {
            connected = client.connect();
        } catch (Exception e) {
            System.out.println(e);
        }

        if (connected) {
            System.out.println("Connected");
        } else {
            System.out.println("Failed to connect");
        }
        return connected;
    }

    static void authenticateClient(SocketClient client)
    {
        if (authenticate(client, API_KEY, API_SECRET)) {
            final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            Object authExpiringHandler = new Object() {
                public void authenticationExpiring() {
                    System.out.println("Authentication expiring...");
                    scheduler.schedule(new Runnable() {
                        @Override
                        public void run() {
                            authenticate(client, API_KEY, API_SECRET);
                        }
                    }, 1, TimeUnit.SECONDS);
                }
            };
            client.setMessageHandler(authExpiringHandler);
        }
    }

    static Boolean authenticate(SocketClient client, String apiKey, String apiSecret)
    {
        try {
            SocketResponse response = client.authenticate(apiKey, apiSecret);
            if (response.Success) {
                System.out.println("Authenticated");
            } else {
                System.out.println("Failed to authenticate: " + response.ErrorCode);
            }
            return response.Success;
        } catch (Exception e) {
            System.out.println("Failed to authenticate: " + e.toString());
            return false;
        }
    }

    static void subscribe(SocketClient client)
    {
        String[] channels = new String[]{"heartbeat", "trade_BTC-USD", "balance"};

        // If subscribed to multiple market's trade streams,
        // use the marketSymbol field in the message to differentiate
        Object msgHandler = new Object() {
            public void heartbeat() {
                System.out.println("<heartbeat>");
            }

            public void trade(String compressedData) {
                // If subscribed to multiple market's trade streams,
                // use the marketSymbol field in the message to differentiate
                printSocketMessage("Trade", compressedData);
            }

            public void balance(String compressedData) {
                printSocketMessage("Balance", compressedData);
            }
        };

        client.setMessageHandler(msgHandler);
        try {
            SocketResponse[] response = client.subscribe(channels);
            for (int i = 0; i < channels.length; i++) {
                System.out.println(channels[i] + ": " + (response[i].Success ? "Success" : response[i].ErrorCode));
            }
        } catch (Exception e) {
            System.out.println("Failed to subscribe: " + e.toString());
        }
    }

    static void printSocketMessage(String msgType, String compressedData)
    {
        String text = "";
        try {
            JsonObject msg = DataConverter.decodeMessage(compressedData);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            text = gson.toJson(msg);
        } catch (Exception e) {
            text = "Error decompressing message - " + e.toString() + " - " + compressedData;
        }
        System.out.println(msgType + ": " + text);
    }
}
