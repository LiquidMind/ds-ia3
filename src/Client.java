import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.GregorianCalendar;
import java.util.UUID;

import javax.management.RuntimeErrorException;

import jcurses.system.CharColor;
import jcurses.system.InputChar;
import jcurses.system.Toolkit;
import jcurses.widgets.DefaultLayoutManager;
import jcurses.widgets.Label;
import jcurses.widgets.TextArea;
import jcurses.widgets.WidgetsConstants;
import jcurses.widgets.Window;


public class Client {
  public static InputChar ch = null;
  
  public static int inputLength = 40; // width of text input in characters
  public static int cursorRelPos = 0;
  public static int cursorAbsPos = 0;
  public static String text = "One, two, three, four, five, six, seven, eight, nine, ten!";
  public static String textInput = "";
  public static String line = "";
  public static String moveCursor = "";
  public static String part1 = "";
  public static String part2 = "";
  public static String parts = "";
  public static String spaces = "";
  
  public static int code;
  public static String start = "";
  public static String end = "";
  public static int from = 0;
  public static int to = 0;
  public static int offsetX = 0;
  public static int offsetY = 1;

  public static int screenWidth = Toolkit.getScreenWidth() - 8;
  public static int screenHeight = Toolkit.getScreenHeight() - 4;
  
  public static CharColor textInputColor = new CharColor(CharColor.BLUE, CharColor.WHITE);
  public static CharColor backgroundColor = new CharColor(CharColor.BLACK, CharColor.WHITE);
  public static CharColor cursorColor = new CharColor(CharColor.GREEN, CharColor.BLACK);
  
  public static boolean leftScroll = false;
  public static boolean rightScroll = false;
  
  //separate thread to dispatch requests 
  public static Thread requestsDispatcher;
  // separate thread to receive requests 
  public static Thread requestsReceiver;
  
  public static boolean keepWorking = false;

  public static MulticastSocket multicastSocket; 
  public static String multicastIP;
  public static int multicastPort;
  public static InetAddress multicastGroup;
  
  // byte arrays to store data that is sent and received
  public static byte[] receiveData = null;
  public static byte[] sendData = null;
  
  //to store received packets
  static DatagramPacket receivePacket = null;
  
  // log filename
  public static String logFileName = null;
  // to write log data
  public static PrintWriter logWriter = null;
  public static FileOutputStream log = null;
  
  public static String localIP;
  public static int localPort = 2333;
  
  // to select density of log messages
  static int logLevel = 10;  
  
  // variable to make log synchronized on System.out
  static final boolean synchronizedLog = true;
  
  static String clientUUID = UUID.randomUUID().toString();
  
  public static int cursorAbsPosFrom;
  public static int cursorAbsPosTo;
  public static int cursorRelPosFrom;
  public static int cursorRelPosTo;
  public static String textFrom;
  public static String textTo;
  
  public static ServerMessage msg;
  
  public static long lastResponse;
  public static int timeOutStatus = 1000; // milliseconds
  public static int timeOutSleep = 100; // milliseconds
  public static int status = 0;
  
  public static void startRequestsDispatcher(){
    // Separate thread to send requests
    requestsDispatcher = new Thread(new Runnable() {           
      public void run() {
        //long leftToSleep;
        while (keepWorking) {
          if (lastResponse + timeOutStatus < System.currentTimeMillis()) {
            synchronized (Client.class) {
              status = status % 4 + 1;
              drawTextInput();
            }
          } else {
            synchronized (Client.class) {
              status = 0;
              drawTextInput();
            }
          }
          sendMessage(ServerMessage.ACTION_CLIENT_UPDATE, textFrom, text, cursorAbsPosFrom, cursorAbsPos, cursorRelPosFrom, cursorRelPos);
          try {
            Thread.sleep(timeOutSleep);
          } catch (InterruptedException e) {
            new RuntimeException(e);
          }         
        }
      }
    });
    requestsDispatcher.start();
  }
  
  public static void startRequestsReceiver() {
    // Separate thread to process responses
    requestsReceiver = new Thread(new Runnable() {           
      public void run() {
        receivePacket = new DatagramPacket(receiveData, receiveData.length);
        
        while(keepWorking) {
          try {
            //serverSocket.setSoTimeout(1000);
            multicastSocket.receive(receivePacket);
            
            msg = ServerMessage.deserialize(receivePacket.getData());
            
            //log(0, ServerMessage.actionToString(msg.action) + " message received from UUID: " + msg.clientUUID);
            
            if (msg.clientUUID.equals(clientUUID) && (
                    msg.action == ServerMessage.ACTION_CLIENT_CONFIRM 
                    || msg.action == ServerMessage.ACTION_CLIENT_DECLINE
                    )) {
              // new messages from the server received
              //log(0, ServerMessage.actionToString(msg.action) + " message received from UUID: " + msg.clientUUID);
              lastResponse = System.currentTimeMillis();
              switch(msg.action) {
                case ServerMessage.ACTION_CLIENT_CONFIRM:
                  //log(0, "ACTION_CLIENT_CONFIRM");
                  //drawTextInput();
                  break;
                case ServerMessage.ACTION_CLIENT_DECLINE:
                  synchronized(Client.class) {
                    text = msg.textTo;
                    cursorAbsPos = msg.cursorAbsPosTo;
                    cursorRelPos = msg.cursorRelPosTo;
                    
                    textFrom = text;
                    cursorAbsPosFrom = cursorAbsPos;
                    cursorRelPosFrom = cursorRelPos;
                    
                    drawTextInput();
                  }
                  break; 
                default:
                  throw new RuntimeException("Error: Unhandled action: " + ServerMessage.actionToString(msg.action));
                  //break;
              }
            }
          } catch (SocketTimeoutException e) {
            // 1000ms have elapsed but nothing was read
            log(3, "PLANNED: Socket read timeout...");
          } catch (IOException e) {
            log(4, "!");
            throw new RuntimeException(e);
          }
        }   
      }
    });
    requestsReceiver.start();
  }


  public static void main(String[] args) throws InterruptedException, IncorrectLogFileException, IOException {
    if (args.length < 3) {
      System.out.println("Usage: Client multicastIP multicastPort logFileName");
      return;
    }
    
    localIP = InetAddress.getLocalHost().getHostAddress();
            
    initLogger(args[2]);
    
    log(0, "Client UUID is: " + clientUUID );
    log(0, "Local address is: " + localIP);
 
    // init networ part of the client
    receiveData = new byte[ServerMessage.BUFFER_SIZE]; //bytes
    sendData = new byte[ServerMessage.BUFFER_SIZE]; //bytes
    
    multicastIP = args[0];
    multicastPort = 2333; 
    
    multicastGroup = InetAddress.getByAddress(stringToIP(multicastIP));
    
    multicastSocket = new MulticastSocket(multicastPort);
    // set socket timeout to 1 second that we can stop thread safely after all
    multicastSocket.setSoTimeout(1000);
    multicastSocket.joinGroup(multicastGroup);
    
    keepWorking = true;
    startRequestsReceiver();
    
    String message = "width: " + screenWidth + ", height: " + screenHeight;
    System.out.println(message);

    Toolkit.clearScreen(backgroundColor);

    drawTextInput();
    
    // init variables
    synchronized(Client.class) {
      textFrom = text;
      cursorAbsPosFrom = cursorAbsPos;
      cursorRelPosFrom = cursorRelPos;
    }
    
    startRequestsDispatcher();

    while (ch == null || ch.getCode() != 27) {
      ch = Toolkit.readCharacter();
      code = ch.getCode();

      synchronized(Client.class) {      
        if (code >= 32 && code <= 122) {
          // a-zA-Z
          part1 = (cursorAbsPos > 0) ? text.substring(0, cursorAbsPos) : "";
          part2 = (text.length() > cursorAbsPos) ? text.substring(cursorAbsPos, text.length()) : "";
          part1 += ch; 
          text = part1 + part2;
          cursorAbsPos += 1;
          cursorRelPos += cursorRelPos < inputLength ? 1 : 0;
        } else if (code == 263) {
          // backspace
          if (cursorAbsPos > 0) {
            part1 = (cursorAbsPos > 1) ? text.substring(0, cursorAbsPos - 1) : "";
            part2 = (text.length() > cursorAbsPos) ? text.substring(cursorAbsPos, text.length()) : "";
            text = part1 + part2;
            cursorAbsPos--;
            cursorRelPos -= cursorRelPos > 0 ? 1 : 0;;
          }
        } else if (code == 330) {
          // delete
          if (cursorAbsPos < text.length()) {
            part1 = (cursorAbsPos > 1) ? text.substring(0, cursorAbsPos) : "";
            part2 = (text.length() > cursorAbsPos) ? text.substring(cursorAbsPos + 1, text.length()) : "";
            text = part1 + part2;
          }
        } else if (code == 260 || code == 259) {
          // left || up
          cursorAbsPos -= cursorAbsPos > 0 ? 1 : 0;
          cursorRelPos -= cursorRelPos > 0 ? 1 : 0;
        } else if (code == 261 || code == 258) {
          // right || down
          cursorAbsPos += cursorAbsPos < text.length() ? 1 : 0;
          cursorRelPos += cursorRelPos < Math.min(text.length(), inputLength) ? 1 : 0;
        } else if (code == 262 || code == 339) {
          // home || page up
          cursorAbsPos = 0;
          cursorRelPos = 0;
        } else if (code == 360 || code == 338) {
          // end || page down
          cursorAbsPos = text.length();
          cursorRelPos = Math.min(text.length(), inputLength);
        } else if (code == 10) {
          // enter will clear text input
          text = "";
          cursorAbsPos = 0;
          cursorRelPos = 0;
        }
        
        drawTextInput();
      
        sendMessage(ServerMessage.ACTION_CLIENT_UPDATE, textFrom, text, cursorAbsPosFrom, cursorAbsPos, cursorRelPosFrom, cursorRelPos);
        
        textFrom = text;
        cursorAbsPosFrom = cursorAbsPos;
        cursorRelPosFrom = cursorRelPos;        
      }    
    }
    keepWorking = false;
    
    // wait before requestsReceiver thread will stop
    if (requestsReceiver != null) {
      requestsReceiver.join();
    }
    // wait before requestsDispatcher thread will stop
    if (requestsDispatcher != null) {
      requestsDispatcher.join();
    }
    
    Toolkit.shutdown();
  }
  
  public static String fixedWidthInteger(int value, int length) {
    String result = "";
    for (int i = length - 1; i > 0; i--) {
      result += value < Math.pow(10, i) ? " " : "";
    }
    result += value;
    return result;
  }
  
  public static String multipleCopies(String str, int n) {
    StringBuffer outputBuffer = new StringBuffer(str.length() * n);
    for (int i = 0; i < n; i++){
       outputBuffer.append(str);
    }
    return outputBuffer.toString();
  }
  
  public static void checkCursorParams() {
    if (cursorRelPos < 0 || cursorAbsPos < 0) {
      throw new RuntimeException("Error: cursorAbsPos and cursorAbsPos can't be less than 0!");
    }
    
    if (cursorRelPos > inputLength) {
      throw new RuntimeException("Error: cursorRelPos can't be more than inputLength!");
    }
    
    if (cursorAbsPos > text.length()) {
      throw new RuntimeException("Error: cursorAbsPos can't be more than text.lentgh()!");
    }
    
    if (cursorRelPos > cursorAbsPos) {
      throw new RuntimeException("Error: cursorRelPos can't be more than cursorAbsPos!");
    }        
  }
  
  public static synchronized void drawTextInput() {
    String[] statusChars = {"*", "|", "/", "-", "\\"}; 
            
    checkCursorParams();
    
    start = fixedWidthInteger(code, 3) + " | " + fixedWidthInteger(text.length(), 3) + " | " + fixedWidthInteger(cursorAbsPos, 3) + " | " + fixedWidthInteger(cursorRelPos, 2) + " | " +  statusChars[status] + " >> ";
    end = " <<";
    
    leftScroll = cursorAbsPos > cursorRelPos;
    rightScroll = text.length() - cursorAbsPos > inputLength - cursorRelPos;
    
    from = cursorAbsPos - cursorRelPos;
    to = Math.min(text.length(), cursorAbsPos - cursorRelPos + inputLength);
            
    textInput = "";
    textInput += text.substring(from, to);
    textInput += multipleCopies(" ", inputLength - (to - from));
    
    part1 = (cursorRelPos > 0) ? textInput.substring(0, cursorRelPos) : "";
    part2 = (textInput.length() > cursorRelPos) ? textInput.substring(cursorRelPos, textInput.length()) : "";
    
    part1 = (leftScroll ? "<" : " ") + part1;
    part2 = part2 + (rightScroll ? ">" : " ");
    
    offsetX = 0;
    offsetY = 1;
    Toolkit.printString(start, 0, offsetY, backgroundColor);
    offsetX += start.length();
    Toolkit.printString(part1, offsetX, offsetY, textInputColor);
    offsetX += part1.length();
    Toolkit.printString(part2.substring(0, 1), offsetX++, offsetY, cursorColor);
    Toolkit.printString(part2.substring(1, part2.length()), offsetX, offsetY, textInputColor);
    offsetX += part2.substring(1, part2.length()).length();
    Toolkit.printString(end, offsetX, offsetY, backgroundColor);
    
    line = "\r" + start + (leftScroll ? "<" : " ") + part1 + part2 + (rightScroll ? ">" : " ") + "\r" + start + (leftScroll ? "<" : " ") + part1;
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
    
    message = Node.dateFormatter.format(Node.localTimeMillis()) + " >> " + message + "\n";
            
    if (logWriter != null) {
      logWriter.write(message);
      logWriter.flush();
    }
    System.out.print(message);
  }
  
  static byte[] stringToIP(String ip) {
    //log("Once again, IP is: " + ip);
    String[] parts = ip.split("\\.");
    //log(parts[0] + " . " + parts[1] + " . " + parts[2] + " . " + parts[3]);
    byte[] IP = new byte[]{
            (byte)Integer.parseInt(parts[0]),
            (byte)Integer.parseInt(parts[1]),
            (byte)Integer.parseInt(parts[2]),
            (byte)Integer.parseInt(parts[3])
           };
    //log("Parsed IP: " + new String(IP));
    return IP;
  }
  
  public static void sendMessage(int action, String textFrom, String textTo, 
          int cursorAbsPosFrom, int cursorAbsPosTo, int cursorRelPosFrom, int cursorRelPosTo) {
    ServerMessage msg = new ServerMessage(action);
    msg.setClientUUID(clientUUID);
    try {
      msg.setClientIP(InetAddress.getLocalHost().getHostAddress());
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    msg.setClientPort(localPort);
    
    msg.setTextFrom(textFrom);
    msg.setTextTo(textTo);
    msg.setCursorAbsPosFrom(cursorAbsPosFrom);
    msg.setCursorAbsPosTo(cursorAbsPosTo);
    msg.setCursorRelPosFrom(cursorRelPosFrom);
    msg.setCursorRelPosTo(cursorRelPosTo);
    
    log(2, "Sending " + ServerMessage.actionToString(action) + " to IP: " + multicastIP + " and port: " + multicastPort);

    InetAddress address;
    try {
      address = InetAddress.getByAddress(stringToIP(multicastIP));
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    byte[] sendData = ServerMessage.serialize(msg);
    DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, multicastPort);

    //serverSocket = new DatagramSocket(); //commented because socket was already created in run() method
    try {
      multicastSocket.send(packet);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }    
  }  
}
