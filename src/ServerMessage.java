import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


public class ServerMessage implements Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = 4927132558815887108L;
  
  public static final int BUFFER_SIZE = 8 * 1024;

  public static final int ACTION_ELECTION = 1; // send message about starting of new election
  public static final int ACTION_VOTE = 2; // vote for specific candidate
  public static final int ACTION_BROADCAST_LEADER = 4; // broadcast new leader to all clients
  public static final int ACTION_CONFIRM = 8; // confirmation about accepting changes
  public static final int ACTION_HEARTBEAT = 16; // heartbeat message from current leader to all nodes
  public static final int ACTION_JOIN = 32; // notify other nodes that new node has joined the network
  
  public static final int ACTION_CLIENT_UPDATE = 64; // send update request to from the client to the system
  public static final int ACTION_CLIENT_CONFIRM = 128; // send confirmation to the client about update request
  public static final int ACTION_CLIENT_DECLINE = 256; // send decline response to the client about update request

  int action;
  
  // client part of data
  String clientUUID;
  String clientIP;
  int clientPort;
  static String clientProtocol = "UDP";

  // server part of data
  String serverUUID;
  String serverIP;
  int serverPort;
  static String serverProtocol = "UDP";
  
  int term;
  long heartbeatMessageID;
  
  // client request part of data
  long requestID;
  long responseID;
  
  String textFrom;
  String textTo;
  int cursorAbsPosFrom;
  int cursorAbsPosTo;
  int cursorRelPosFrom;
  int cursorRelPosTo;

  long requestTime;
  long lastRedirectTime;
  int redirectsCount;
  
  boolean withReplicatedData;
  ServerMessage clientMessage;
  
  public HashCodeBuilder toHashCodeBuilder(ServerMessage sm) {
    HashCodeBuilder hb; 
  
    if (sm.clientMessage != null) {
      hb = toHashCodeBuilder(sm.clientMessage);
    } else {
      hb = new HashCodeBuilder(17, 31). // two randomly chosen prime numbers
              append(sm.clientMessage);
    }
    return hb. // if deriving: appendSuper(super.hashCode()).
            append(sm.clientUUID).
            append(sm.clientIP).
            append(sm.clientPort).
            append(sm.clientProtocol).
            append(sm.serverUUID).
            append(sm.serverIP).
            append(sm.serverPort).
            append(sm.serverProtocol).
            append(sm.term).
            append(sm.heartbeatMessageID).
            append(sm.requestID).
            append(sm.responseID).
            append(sm.textFrom).
            append(sm.textTo).
            append(sm.cursorAbsPosFrom).
            append(sm.cursorAbsPosTo).
            append(sm.cursorRelPosFrom).
            append(sm.cursorRelPosTo).
            append(sm.requestTime).
            append(sm.lastRedirectTime).
            append(sm.redirectsCount).
            append(sm.withReplicatedData);
  }
  
  public EqualsBuilder toEqualsBuilder(ServerMessage l, ServerMessage r) {
    return new EqualsBuilder(). // if deriving: appendSuper(super.hashCode()).
            append(l.clientUUID, r.clientUUID).
            append(l.clientIP, r.clientIP).
            append(l.clientPort, r.clientPort).
            append(l.clientProtocol, r.clientProtocol).
            append(l.serverUUID, r.serverUUID).
            append(l.serverIP, r.serverIP).
            append(l.serverPort, r.serverPort).
            append(l.serverProtocol, r.serverProtocol).
            append(l.term, r.term).
            append(l.heartbeatMessageID, r.heartbeatMessageID).
            append(l.requestID, r.requestID).
            append(l.responseID, r.responseID).
            append(l.textFrom, r.textFrom).
            append(l.textTo, r.textTo).
            append(l.cursorAbsPosFrom, r.cursorAbsPosFrom).
            append(l.cursorAbsPosTo, r.cursorAbsPosTo).
            append(l.cursorRelPosFrom, r.cursorRelPosFrom).
            append(l.cursorRelPosTo, r.cursorRelPosTo).
            append(l.requestTime, r.requestTime).
            append(l.lastRedirectTime, r.lastRedirectTime).
            append(l.redirectsCount, r.redirectsCount).
            append(l.withReplicatedData, r.withReplicatedData).
            append(l.clientMessage, r.clientMessage);
  }  
  
  @Override
  public int hashCode() {
    return toHashCodeBuilder(this).toHashCode();
  }

  @Override
  public boolean equals(Object obj) {
     if (!(obj instanceof ServerMessage))
          return false;
      if (obj == this)
          return true;

      return toEqualsBuilder(this, (ServerMessage) obj).
          // if deriving: appendSuper(super.equals(obj)).
          isEquals();
  }
  
  public ServerMessage(int action) {
    setAction(action);
  }
  
  static byte[] serialize(ServerMessage msg) {
    return serializeObject(msg);
  }

  static ServerMessage deserialize(byte[] bytes) {
    return (ServerMessage)deserializeObject(bytes);
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
      case ACTION_CONFIRM:
        return "ACTION_CONFIRM";
      case ACTION_VOTE:
        return "ACTION_VOTE";
      case ACTION_CLIENT_UPDATE:
        return "ACTION_CLIENT_UPDATE";
      case ACTION_CLIENT_CONFIRM:
        return "ACTION_CLIENT_CONFIRM";
      case ACTION_CLIENT_DECLINE:
        return "ACTION_CLIENT_DECLINE";
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
      Server.log(0, "bytes.length = " + bytes.length);
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
    ServerMessage.clientProtocol = clientProtocol;
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

  public synchronized String getServerIP() {
    return serverIP;
  }

  public synchronized void setServerIP(String serverIP) {
    this.serverIP = serverIP;
  }

  public synchronized int getServerPort() {
    return serverPort;
  }

  public synchronized void setServerPort(int serverPort) {
    this.serverPort = serverPort;
  }

  public static synchronized String getServerProtocol() {
    return serverProtocol;
  }

  public static synchronized void setServerProtocol(String serverProtocol) {
    ServerMessage.serverProtocol = serverProtocol;
  }

  public synchronized long getRequestID() {
    return requestID;
  }

  public synchronized void setRequestID(long requestID) {
    this.requestID = requestID;
  }

  public synchronized long getResponseID() {
    return responseID;
  }

  public synchronized void setResponseID(long responseID) {
    this.responseID = responseID;
  }

  public synchronized String getTextFrom() {
    return textFrom;
  }

  public synchronized void setTextFrom(String textFrom) {
    this.textFrom = textFrom;
  }

  public synchronized String getTextTo() {
    return textTo;
  }

  public synchronized void setTextTo(String textTo) {
    this.textTo = textTo;
  }

  public synchronized int getCursorAbsPosFrom() {
    return cursorAbsPosFrom;
  }

  public synchronized void setCursorAbsPosFrom(int cursorAbsPosFrom) {
    this.cursorAbsPosFrom = cursorAbsPosFrom;
  }

  public synchronized int getCursorAbsPosTo() {
    return cursorAbsPosTo;
  }

  public synchronized void setCursorAbsPosTo(int cursorAbsPosTo) {
    this.cursorAbsPosTo = cursorAbsPosTo;
  }

  public synchronized int getCursorRelPosFrom() {
    return cursorRelPosFrom;
  }

  public synchronized void setCursorRelPosFrom(int cursorRelPosFrom) {
    this.cursorRelPosFrom = cursorRelPosFrom;
  }

  public synchronized int getCursorRelPosTo() {
    return cursorRelPosTo;
  }

  public synchronized void setCursorRelPosTo(int cursorRelPosTo) {
    this.cursorRelPosTo = cursorRelPosTo;
  }

  public synchronized ServerMessage getClientMessage() {
    return clientMessage;
  }

  public synchronized void setClientMessage(ServerMessage clientMessage) {
    this.clientMessage = clientMessage;
  }

  public synchronized long getHeartbeatMessageID() {
    return heartbeatMessageID;
  }

  public synchronized void setHeartbeatMessageID(long heartbeatMessageID) {
    this.heartbeatMessageID = heartbeatMessageID;
  }
  
}

