import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;

public class Server {
  // log filename
  static String logFileName = null;
  // to write log data
  static PrintWriter logWriter = null;
  static FileOutputStream log = null;
  
  static String localIP;
  static int localPort = 2333;
  
  static Node node;
  
  static int numberOfNodes = 0;
  
  // to select density of log messages
  static int logLevel = 0;
  
  // variable to make log synchronized on System.out
  static final boolean synchronizedLog = true;
  
  /*
   * parameters:
   *  1 - multicastIP
   *  2 - multicastPort
   *  3 - logFileName
   */
  public static void main(String[] args) throws NumberFormatException, IncorrectLogFileException, IOException {
    if (args.length < 3) {
      System.out.println("Usage: Server multicastIP multicastPort logFileName numberOfNodes nodeUUID");
      return;
    }
    
    localIP = InetAddress.getLocalHost().getHostAddress();
            
    initLogger(args[2]);
    
    numberOfNodes = Integer.parseInt(args[3]);
    
    if (args.length > 4) {
      node = new Node(args[0], Integer.parseInt(args[1]), args[4]);
    } else {
      node = new Node(args[0], Integer.parseInt(args[1]));
    }
    log(0, "Node UUID is: " + node.getUUID());
    log(0, "Local address is: " + localIP);
    
    node.connect();

    // run thread that processes network requests and responses
    node.startRequestsReceiver();
    node.startRequestsDispatcher();
  }
  
  static void initLogger(String logFileName) throws IncorrectLogFileException {
    try {
      FileWriter fw = new FileWriter(logFileName, true);
      BufferedWriter bw = new BufferedWriter(fw);
      logWriter = new PrintWriter(bw);
    } catch (IOException e) {
      throw new IncorrectLogFileException();
    }      
  }
  
  static void log (int logLevel, String message) {
    if (synchronizedLog) {
      // synchronize to ensure log consistency
      synchronized (System.out) {
        _log (logLevel, message);
      }
    } else {
      _log (logLevel, message);
    }
  }

  static void _log (int logLevel, String message) {
    // log only messages with specific log level
    if (logLevel > Server.logLevel)
      return;
    
    if (node == null) {
      message = Node.dateFormatter.format(Node.localTimeMillis()) + " >> " + message + "\n";
    } else {
      message = Node.dateFormatter.format(Node.localTimeMillis()) + " " + node.stateTo3LString() + ":" + node.term + " >> " + message + "\n";
    }

    //message += "Term #" + node.term + "; Leader: " + node.leader + "\n"; 
            
    if (logWriter != null) {
      logWriter.write(message);
      logWriter.flush();
    }
    System.out.print(message);
  }

  public static String getLocalIP() {
    return localIP;
  }

  public static void setLocalIP(String localIP) {
    Server.localIP = localIP;
  }

  public static int getLocalPort() {
    return localPort;
  }

  public static void setLocalPort(int localPort) {
    Server.localPort = localPort;
  }  
}
