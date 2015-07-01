import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;


public class ProtocolMessage implements Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = 4927132558815887108L;

  public static final int ACTION_ELECTION = 1; // send message about starting of new election
  public static final int ACTION_VOTE = 2; // vote for specific candidate
  public static final int ACTION_BROADCAST_LEADER = 4; // broadcast new leader to all clients
  public static final int ACTION_REQUEST = 8; // request to the system
  public static final int ACTION_HEARTBEAT = 16; // heartbeat message from current leader to all nodes
  public static final int ACTION_JOIN = 32; // notify other nodes that new node has joined the network

  int action;
  
  String clientUUID;
  String clientIP;
  int clientPort;
  static String clientProtocol = "UDP";
  
  String serverUUID;
  
  long requestTime;
  long lastRedirectTime;
  int redirectsCount;
  
  int term;
  
  public ProtocolMessage(int action) {
    setAction(action);
  }
  
  static byte[] serialize(ProtocolMessage msg) {
    return serializeObject(msg);
  }

  static ProtocolMessage deserialize(byte[] bytes) {
    return (ProtocolMessage)deserializeObject(bytes);
  }
  
  public static String actionToString(int actionID) {
    switch (actionID) {
      case ACTION_BROADCAST_LEADER:
        return "ACTION_BROADCAST_LEADER";
      case ACTION_ELECTION:
        return "ACTION_ELECTION";
      case ACTION_HEARTBEAT:
        return "ACTION_HEARTBEAT";
      case ACTION_JOIN:
        return "ACTION_JOIN";
      case ACTION_REQUEST:
        return "ACTION_REQUEST";
      case ACTION_VOTE:
        return "ACTION_VOTE";
      default:
        return "ACTION_UNDEFINED";       
    }
  }
  
  static byte[] serializeObject(Object obj) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutput out = null;
    try {
      out = new ObjectOutputStream(bos);
      out.writeObject(obj);
      out.flush();
      byte[] bytes = bos.toByteArray();
      //log("Size of serialized object is: " + bytes.length + " bytes\n");
      //log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
      return bytes;
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      //System.out.println("Finally");
      try {
        if (out != null) {
          out.close();
        }
      } catch (IOException e) {
        new RuntimeException(e);
      }
      try {
        bos.close();
      } catch (IOException e) {
        new RuntimeException(e);
      }
    }
  }

  static Object deserializeObject(byte[] bytes) {
    ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
    ObjectInput in = null;
    try {
      in = new ObjectInputStream(bis);
      Object obj = in.readObject();
      return obj;
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        bis.close();
      } catch (IOException e) {
        new RuntimeException(e);
      }
      try {
        if (in != null) {
          in.close();
        }
      } catch (IOException e) {
        new RuntimeException(e);
      }
    }
  }  
  
  public int getAction() {
    return action;
  }

  public void setAction(int action) {
    this.action = action;
  }

  public String getClientIP() {
    return clientIP;
  }
  
  public void setClientIP(String clientIP) {
    this.clientIP = clientIP;
  }
  
  public int getClientPort() {
    return clientPort;
  }
  
  public void setClientPort(int clientPort) {
    this.clientPort = clientPort;
  }
  
  public static String getClientProtocol() {
    return clientProtocol;
  }
  
  public static void setClientProtocol(String clientProtocol) {
    ProtocolMessage.clientProtocol = clientProtocol;
  }
  
  public long getRequestTime() {
    return requestTime;
  }
  
  public void setRequestTime(long requestTime) {
    this.requestTime = requestTime;
  }
  
  public long getLastRedirectTime() {
    return lastRedirectTime;
  }
  
  public void setLastRedirectTime(long lastRedirectTime) {
    this.lastRedirectTime = lastRedirectTime;
  }
  
  public int getRedirectsCount() {
    return redirectsCount;
  }
  
  public void setRedirectsCount(int redirectsCount) {
    this.redirectsCount = redirectsCount;
  }

  public String getClientUUID() {
    return clientUUID;
  }

  public void setClientUUID(String clientUUID) {
    this.clientUUID = clientUUID;
  }

  public synchronized int getTerm() {
    return term;
  }

  public synchronized void setTerm(int term) {
    this.term = term;
  }

  public synchronized String getServerUUID() {
    return serverUUID;
  }

  public synchronized void setServerUUID(String serverUUID) {
    this.serverUUID = serverUUID;
  }
}

