import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;


public class ClientMessage implements Serializable  {

  /**
   * 
   */
  private static final long serialVersionUID = 1237113035188360749L;
  
  public static final int ACTION_UPDATE = 1; // update current state of the text input
  public static final int ACTION_CONFIRM = 2; // confirm update of text input state
  public static final int ACTION_DECLINE = 4; // confirm update of text input state
  
  long requestID;
  long responseID;
  
  String text;
  int cursorAbsPos;
  int cursorRelPos;
  
  String clientIP;
  int clientPort;
  
  String serverIP;
  int serverPort;

  static byte[] serialize(ClientMessage msg) {
    return serializeObject(msg);
  }

  static ClientMessage deserialize(byte[] bytes) {
    return (ClientMessage)deserializeObject(bytes);
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
}
