import java.io.IOException;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.UUID;


public class Node extends Thread {
  public static final int STATE_NEW = 1;
  public static final int STATE_CANDIDATE = 2;
  public static final int STATE_FOLLOWER = 4;
  public static final int STATE_LEADER = 8;
  
  public static int electionTimeoutFrom = 1500; // in milliseconds
  public static int electionTimeoutTo = 3000; // in milliseconds
  public static int heartbeatInterval = electionTimeoutFrom / 3;
  public static int heartbeatTimeout = heartbeatInterval * 2;
  
  public int electionTimeout; // random value between 150ms and 300ms
  
  public int term;
  public int prevConsensusTerm;
  public String leader;
  public int votesNumber;
  public int majority;
  
  public int consecutiveElections;
  
  // Input date format
  //static SimpleDateFormat dateFormatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
  static SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss.SSS");
  
  String nodeUUID;
  
  String leaderUUID;
  String leaderRank;
  
  int nodeState;
  int prevNodeState;
  ProtocolMessage msg;
  
  MulticastSocket multicastSocket; 
  public String multicastIP;
  public int multicastPort;
  public InetAddress multicastGroup;
  
  public boolean keepWorking;
  
  // byte arrays to store data that is sent and received
  byte[] receiveData = null;
  byte[] sendData = null;

  //to store received packets
  DatagramPacket receivePacket = null;
  
  long lastHeartbeatMessageReceived;
  long lastHeartbeatMessageSent;
  
  // separate thread to dispatch requests 
  Thread requestsDispatcher;
  // separate thread to receive requests 
  Thread requestsReceiver;
  
  HashMap<String, String> nodes;
  HashMap<String, Long> lastMessageTime;
  
  public Node(String multicastIP, int multicastPort) throws UnknownHostException {
    this(multicastIP, multicastPort, UUID.randomUUID().toString());
  }
  
  public Node(String multicastIP, int multicastPort, String nodeUUID) throws UnknownHostException {
    setNodeUUID(nodeUUID); //UUID.randomUUID().toString();
    
    receiveData = new byte[1024]; //bytes
    sendData = new byte[1024]; //bytes
    
    nodeState = STATE_NEW;
    setMulticastIP(multicastIP);
    setMulticastPort(multicastPort);
    
    multicastGroup = InetAddress.getByAddress(stringToIP(multicastIP));
    
    nodes = new HashMap<String, String>();
    lastMessageTime = new HashMap<String, Long>();
    
    nodes.put(nodeUUID, Server.getLocalIP()); // save node itself to hash map
    lastMessageTime.put(nodeUUID, Node.localTimeMillis()); 
    
    keepWorking = true;
  }
  
  public void connect() throws IOException {
    multicastSocket = new MulticastSocket(multicastPort);
    // set socket timeout to 1 second that we can stop thread safely after all
    multicastSocket.setSoTimeout(1000);
    multicastSocket.joinGroup(multicastGroup);
  }
  
  public void startRequestsDispatcher(){
    //lastHeartbeatMessageReceived = System.currentTimeMillis();
    electionTimeout = randomWithRange(electionTimeoutFrom, electionTimeoutTo);
    
    // Separate thread to send requests
    requestsDispatcher = new Thread(new Runnable() {           
      public void run() {
        long leftToSleep;
        while (keepWorking) {
          log(4, "@");
          try {
            switch (nodeState) {
              case STATE_NEW:
                leftToSleep = lastHeartbeatMessageReceived + electionTimeout - System.currentTimeMillis();
                if (leftToSleep > 0) {
                  log(1, "Last heartbeat message: " + (System.currentTimeMillis() - lastHeartbeatMessageReceived) + " ms ago");
                  Thread.sleep(leftToSleep);
                } else {
                  // select random timeout within limits for a new election
                  synchronized (Server.node) {
                    lastHeartbeatMessageReceived = System.currentTimeMillis();
                    // Select random timeout within limits for a new election
                    electionTimeout = randomWithRange(electionTimeoutFrom, electionTimeoutTo);
                    // Increase current election term
                    term++;
                    // Change status to follower and waiting for the heartbeat message from current leader
                    setNodeState(STATE_FOLLOWER);
                    // Vote for himself
                    leader = nodeUUID;
                    // Notify network about new node 
                    sendJoinMessage();
                    // log event
                    log(0, stateToString(STATE_NEW) + " => " + stateToString(STATE_FOLLOWER) + " >> Timeout achieved");
                  }
                }                
                break;
              case STATE_FOLLOWER:
                leftToSleep = lastHeartbeatMessageReceived + electionTimeout - System.currentTimeMillis();
                if (leftToSleep > 0) {
                  log(1, "Last heartbeat message: " + (System.currentTimeMillis() - lastHeartbeatMessageReceived) + " ms ago");
                  Thread.sleep(leftToSleep);
                } else {
                  synchronized (Server.node) {
                    lastHeartbeatMessageReceived = System.currentTimeMillis();
                    // select random timeout within limits for a new election
                    electionTimeout = randomWithRange(electionTimeoutFrom, electionTimeoutTo);
                    // Change status to candidate and start election of a new leader
                    prevNodeState = nodeState;
                    setNodeState(STATE_CANDIDATE);
                    // increase term
                    term++;
                    // send election message to all other nodes
                    sendElectionMessage();
                    // log event 
                    log(0, stateToString(prevNodeState) + " => " + stateToString(STATE_CANDIDATE) + " >> Timeout achieved");
                  }
                }
                break;
              case STATE_CANDIDATE:
                leftToSleep = lastHeartbeatMessageReceived + electionTimeout - System.currentTimeMillis();
                if (leftToSleep > 0) {
                  log(1, "Last heartbeat message: " + (System.currentTimeMillis() - lastHeartbeatMessageReceived) + " ms ago");
                  Thread.sleep(leftToSleep);
                } else {
                  synchronized (Server.node) {
                    // Keep candidate node status and start new election round
                    lastHeartbeatMessageReceived = System.currentTimeMillis();
                    // select random timeout within limits for a new election
                    electionTimeout = randomWithRange(electionTimeoutFrom, electionTimeoutTo);
                    // increase term
                    term++;
                    // increase amount of consecutive votes
                    consecutiveElections++;
                    // change node state
                    prevNodeState = nodeState;
                    setNodeState(STATE_CANDIDATE);
                    // send election message to all other nodes
                    sendElectionMessage();
                    log(0, stateToString(prevNodeState) + " => " + stateToString(STATE_CANDIDATE) + " >> Timeout achieved. Next term.");                    
                  }
                }
                break;
              case STATE_LEADER:
                leftToSleep = lastHeartbeatMessageSent + heartbeatInterval - System.currentTimeMillis();
                if (leftToSleep > 0) {
                  log(1, "Last heartbeat message: " + (System.currentTimeMillis() - lastHeartbeatMessageSent) + " ms ago");
                  Thread.sleep(leftToSleep);
                } else {
                  synchronized (Server.node) {
                    lastHeartbeatMessageSent = System.currentTimeMillis();
                    // select random timeout within limits for a new election
                    //electionTimeout = randomWithRange(electionTimeoutFrom, electionTimeoutTo);
                    // send election message to all other nodes
                    sendHeartbeatMessage();
                  }
                }
                break;
              default:
                throw new RuntimeException("Unknown state with ID: " + nodeState);
            }
          } catch (InterruptedException e) {
            /*
             *  Some event arrive in other thread that needs to be processed.
             *  Just move to next iteration of while.
             */
            // new RuntimeException(e);
            log(0, "Thread for requests dispatch was interrupted");
          }
        }
      }
    });
    requestsDispatcher.start();
  }
  
  public void startRequestsReceiver() {
    // Separate thread to process responses
    requestsReceiver = new Thread(new Runnable() {           
      public void run() {
        receivePacket = new DatagramPacket(receiveData, receiveData.length);
        
        while(keepWorking) {
          try {
            //serverSocket.setSoTimeout(1000);
            multicastSocket.receive(receivePacket);
            
            log(4, ".");

            msg = ProtocolMessage.deserialize(receivePacket.getData());
            
            if (msg.clientUUID.equals(nodeUUID)) {
              // Echo message received. Ignore it.
            } else if (msg.serverUUID != null && !msg.serverUUID.equals(nodeUUID)) {
              // Received personal message to other node. Ignore it.
              log(4, "#");
            } else {
              //log(allObjectFieldsToString(msg));
              log(1, "Action " + ProtocolMessage.actionToString(msg.action) + " was received.");

              if (nodes.containsKey(msg.clientUUID)) {
                /*
                 *  Node already exists in hash table.
                 *  Just do nothing.
                 */
              } else {
                nodes.put(msg.clientUUID, msg.clientIP);
                log(0, "New node with UUID " + msg.clientUUID + " and IP " + msg.clientIP + " was discovered");
              }
              
              switch (msg.action) {
                case ProtocolMessage.ACTION_JOIN:
                  /* 
                   * This action was deprecated during development.
                   * DIscovery of new server nodes is done on any incomming message.
                   */
                  break;
                case ProtocolMessage.ACTION_ELECTION:
                  if (msg.term > term) {
                    synchronized (Server.node) {
                      // node hasn't vote in this election yet
                      lastHeartbeatMessageReceived = System.currentTimeMillis();
                      // select random timeout within limits for a new election
                      electionTimeout = randomWithRange(electionTimeoutFrom, electionTimeoutTo);
                      leader = msg.clientUUID;
                      term = msg.term;
                      // send vote message to initiator of election
                      //sendVoteMessage(msg.clientIP, msg.clientPort);
                      sendVoteMessage(msg.clientUUID);
                      // change node state
                      prevNodeState = nodeState;
                      setNodeState(STATE_FOLLOWER);
                      // log event
                      log(0, stateToString(prevNodeState) + " => " + stateToString(STATE_FOLLOWER) + " >> Election request from the higher term #" + msg.term + ".");
                      requestsDispatcher.interrupt();
                    }
                  } else if (msg.term == term) {
                    switch (nodeState) {
                      case STATE_FOLLOWER:
                        if (leader == null) {
                          synchronized (Server.node) {
                            // node hasn't vote in this election yet
                            lastHeartbeatMessageReceived = System.currentTimeMillis();
                            // select random timeout within limits for a new election
                            electionTimeout = randomWithRange(electionTimeoutFrom, electionTimeoutTo);
                            leader = msg.clientUUID;
                            term = msg.term;
                            // send vote message to initiator of election
                            //sendVoteMessage(msg.clientIP, msg.clientPort);
                            sendVoteMessage(msg.clientUUID);
                            // change node state
                            prevNodeState = nodeState;
                            setNodeState(STATE_FOLLOWER);
                            // log event
                            log(0, stateToString(nodeState) + " => " + stateToString(STATE_FOLLOWER) + " >> Election request from the same term #" + msg.term + ".");
                            requestsDispatcher.interrupt();
                          }
                        } else {
                          log(2, "IGNORE: Node already voted in the term #" + term + " for other node");
                        }
                        break;
                      case STATE_CANDIDATE:
                        if (leader == null) {
                          throw new RuntimeException("It's not possible! Candinate should immediately vote for himself.");
                        } else {
                          log(2, "IGNORE: Node already voted in the term #" + term + " for itself");
                        }
                        break;
                      case STATE_LEADER:
                        log(2, "IGNORE: Node already won this election term. Other candidates will receive heartbeat message soon.");
                        break;
                    }
                    
                  } else {
                    log(2, "IGNORE: Election request from the earlier term #" + msg.term);
                  }
                  break;
                case ProtocolMessage.ACTION_HEARTBEAT:
                  switch (nodeState) {
                    case STATE_FOLLOWER:
                    case STATE_CANDIDATE:
                      if (msg.term > term) {
                        synchronized (Server.node) {
                          // update node's last heartbeat time
                          lastHeartbeatMessageReceived = System.currentTimeMillis();
                          // select random timeout within limits for a new election
                          electionTimeout = randomWithRange(electionTimeoutFrom, electionTimeoutTo);
                          term = msg.term;
                          leader = msg.clientUUID;
                          // change node state
                          prevNodeState = nodeState;
                          setNodeState(STATE_FOLLOWER);
                          // log event
                          log(0, stateToString(prevNodeState) + " => " + stateToString(STATE_FOLLOWER) + " >> Heartbeat: leader with higher term #" + msg.term);
                          if (term > prevConsensusTerm) {
                            log(0, "It took " + (term - prevConsensusTerm) + " consecutive elections to achieve consensus.");
                            prevConsensusTerm = term;
                          }
                          requestsDispatcher.interrupt();
                        }  
                      } else if (msg.term == term) { 
                        if (!msg.clientUUID.equals(leader)) {
                          synchronized (Server.node) {
                            // update node's last heartbeat time
                            lastHeartbeatMessageReceived = System.currentTimeMillis();
                            // select random timeout within limits for a new election
                            electionTimeout = randomWithRange(electionTimeoutFrom, electionTimeoutTo);
                            term = msg.term;
                            leader = msg.clientUUID;
                            // change node state
                            prevNodeState = nodeState;
                            setNodeState(STATE_FOLLOWER);
                            // log event
                            log(0, stateToString(prevNodeState) + " => " + stateToString(STATE_FOLLOWER) + " >> Heartbeat: Leader with the same term #" + msg.term);
                            if (term > prevConsensusTerm) {
                              log(0, "It took " + (term - prevConsensusTerm) + " consecutive elections to achieve consensus.");
                              prevConsensusTerm = term;
                            }
                            requestsDispatcher.interrupt();
                          }
                        } else {
                          synchronized (Server.node) {
                            // update node's last heartbeat time
                            lastHeartbeatMessageReceived = System.currentTimeMillis();
                            // select random timeout within limits for a new election
                            electionTimeout = randomWithRange(electionTimeoutFrom, electionTimeoutTo);
                            log(1, "Heartbeat from the current leader with term #" + msg.term);
                            if (term > prevConsensusTerm) {
                              log(0, "It took " + (term - prevConsensusTerm) + " consecutive elections to achieve consensus.");
                              prevConsensusTerm = term;
                            }
                            requestsDispatcher.interrupt();
                          }
                        }
                      } else {
                        log(2, "IGNORE: Heartbeat from the current leader with smaller term #" + msg.term + " was detected. It will receive heartbeat from the higher leader and will turn into the follower soon.");
                      }    
                      break;
                    case STATE_LEADER:
                      if (msg.term > term) {
                        synchronized (Server.node) {
                          // update node's last heartbeat time
                          lastHeartbeatMessageReceived = System.currentTimeMillis();
                          // select random timeout within limits for a new election
                          electionTimeout = randomWithRange(electionTimeoutFrom, electionTimeoutTo);
                          term = msg.term;
                          leader = msg.clientUUID;
                          // change node state
                          prevNodeState = nodeState;
                          setNodeState(STATE_LEADER);
                          // log event
                          log(0, stateToString(prevNodeState) + " => " + stateToString(STATE_LEADER) + " >> New leader with higher term #" + msg.term);
                          if (term > prevConsensusTerm) {
                            log(0, "It took " + (term - prevConsensusTerm) + " consecutive elections to achieve consensus.");
                            prevConsensusTerm = term;
                          }
                          requestsDispatcher.interrupt();
                        } if (msg.term == term) {
                          if (!msg.clientUUID.equals(leader)) {
                            throw new RuntimeException("Ping from another leader with the same term was detected! It's not possible. Only one leader per term is alowed.");
                          } else {
                            throw new RuntimeException("It's not possible because we filtered echo messages earlier!");
                          }
                        } else {
                          log(2, "Heartbeat from the current leader with smaller term #" + msg.term + " was detected. It will receive heartbeat from the higher leader and will turn into the follower soon.");
                        }
                      } else 
                      break;
                  }
                  break;
                case ProtocolMessage.ACTION_VOTE:
                  //log(1, "Process ACTION_VOTE message. Node state is " + stateToString(nodeState));
                  if (nodeState == STATE_CANDIDATE) {
                    if (msg.term > term) {
                      throw new RuntimeException("Node can't get votes from higher term #" + msg.term);
                    } else if (msg.term == term) {
                      synchronized (Server.node) {
                        votesNumber++;
                        //majority = nodes.size() / 2 + 1;
                        majority = Server.numberOfNodes / 2 + 1;
                        // if node got majority of votes
                        log(0, "+1 vote >> " + votesNumber + " votes out of " + Server.numberOfNodes + " (majority: " + majority + ")");
                        
                        if (votesNumber >= majority) {
                          // change node state
                          prevNodeState = nodeState;
                          setNodeState(STATE_LEADER);
                          // send heartbeat message from new leader
                          sendHeartbeatMessage();
                          // log event
                          log(0, stateToString(prevNodeState) + " => " + stateToString(STATE_LEADER) + " >> " + votesNumber + " out of " + Server.numberOfNodes + " total votes.");
                          //log(0, "It took " + consecutiveElections + " consecutive elections to achieve consensus.");
                          if (term > prevConsensusTerm) {
                            log(0, "It took " + (term - prevConsensusTerm) + " consecutive elections to achieve consensus.");
                            prevConsensusTerm = term;
                          }
                        }
                        requestsDispatcher.interrupt();
                      }
                    } else {
                      log(2, "IGNORE: Vote is from earlier term #" + msg.term);
                    }
                  } else {
                    log(2, "IGNORE: Node is already in the state " + stateToString(nodeState) + " and doesn't need votes anymore.");
                  }
                  break;
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
  
  String stateToString(int nodeState) {
    switch (nodeState) {
      case STATE_CANDIDATE:
        return "S_CND";
      case STATE_FOLLOWER:
        return "S_FLW";
      case STATE_LEADER:
        return "S_LDR";
      case STATE_NEW:
        return "S_NEW";
      default:
        return "S_UND";
    }
  }
  
  String stateTo3LString() {
    switch (nodeState) {
      case STATE_CANDIDATE:
        return "CND";
      case STATE_FOLLOWER:
        return "FLW";
      case STATE_LEADER:
        return "LDR";
      case STATE_NEW:
        return "NEW";
      default:
        return "STATE_UNDEFINED";
    }
  }

  
  synchronized static long localTimeMillis() {
    return System.currentTimeMillis();
  }
  
  static int randomWithRange(int min, int max) {
     int range = (max - min) + 1;
     return (int)(Math.random() * range) + min;
  }
  
  public void sendPrivateMessage(int action, String serverUUID) {
    sendMessage(action, serverUUID);
  }
  
  public void sendPrivateMessage(int action, String ip, int port) {
    sendMessage(action, ip, port);
  }
  
  public void sendMulticastMessage(int action) {
    sendMessage(action, multicastIP, multicastPort);
  }
  
  public void sendMessage(int action, String serverUUID) {
    ProtocolMessage msg = new ProtocolMessage(action);
    msg.setClientUUID(nodeUUID);
    try {
      msg.setClientIP(InetAddress.getLocalHost().getHostAddress());
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    msg.setClientPort(Server.getLocalPort());
    msg.setTerm(term);
    msg.setServerUUID(serverUUID);
    
    log(2, "Sending " + ProtocolMessage.actionToString(action) + " to UUID: " + serverUUID);

    InetAddress address;
    try {
      address = InetAddress.getByAddress(stringToIP(multicastIP));
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    byte[] sendData = ProtocolMessage.serialize(msg);
    DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, multicastPort);

    //serverSocket = new DatagramSocket(); //commented because socket was already created in run() method
    try {
      multicastSocket.send(packet);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }    
  }
  
  public void sendMessage(int action, String ip, int port) {
    ProtocolMessage msg = new ProtocolMessage(action);
    msg.setClientUUID(nodeUUID);
    try {
      msg.setClientIP(InetAddress.getLocalHost().getHostAddress());
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    msg.setClientPort(Server.getLocalPort());
    msg.setTerm(term);
    
    log(2, "Sending " + ProtocolMessage.actionToString(action) + " to IP: " + ip + " and port: " + port);

    InetAddress address;
    try {
      address = InetAddress.getByAddress(stringToIP(ip));
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    byte[] sendData = ProtocolMessage.serialize(msg);
    DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, port);

    //serverSocket = new DatagramSocket(); //commented because socket was already created in run() method
    try {
      multicastSocket.send(packet);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }    
  }
  
  public void sendJoinMessage() {
    sendMulticastMessage(ProtocolMessage.ACTION_JOIN);
  }
  
  private void sendElectionMessage() {
    sendMulticastMessage(ProtocolMessage.ACTION_ELECTION);
  } 
  
  private void sendHeartbeatMessage() {
    sendMulticastMessage(ProtocolMessage.ACTION_HEARTBEAT);
  }
  
  private void sendVoteMessage(String ip, int port) {
    sendPrivateMessage(ProtocolMessage.ACTION_VOTE, ip, port);
  }
  
  private void sendVoteMessage(String serverUUID) {
    sendPrivateMessage(ProtocolMessage.ACTION_VOTE, serverUUID);
  }
  
  private void log(int logLevel, String message) {
    Server.log(logLevel, message);
  }

  /* 
   * The chosen leader (among those 5 master nodes) should select the right value
   * according to selected algorithm or update current value 
   * if such request was received from the slave node
   * */
  public void selectAmongMultipleValues() {
    
  }
  
  /* 
   * Leader should send all information about connected clients to other servers
   */
  public void sendConnectedClientsInfoToTheSlaves() {
    
  }
  
  /*
   *  Minority of master nodes should add randomly generated delay
   *  for communication with other master nodes, which should guarantee 
   *  different values at least on some master nodes
   */
  public void randomDelayInCommunication() {
    
  }
  
  /*
   * Leader should write the log file
   */
  public void logIfLeader(String msg) {
    
  }
  
  /*
   *  After leader election, it should broadcast his address to all clients 
   */
  public void broadcastAddressToTheClients() {
    
  }
  
  byte[] stringToIP(String ip) {
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
  
  public String allObjectFieldsToString(Object obj) {
    String out = "";
    for (Field field : msg.getClass().getDeclaredFields()) {
      field.setAccessible(true);
      String name = field.getName();
      Object value;
      try {
        value = field.get(obj);
      } catch (IllegalArgumentException e2) {
        throw new RuntimeException(e2);
      } catch (IllegalAccessException e2) {
        throw new RuntimeException(e2);
      }
      out += "Field name: " + name + ", Field value: " + value + "\n";
    }
    return out;
  }  
  
  public String getUUID() {
    return nodeUUID;
  }

  public void setUUID(String nodeUUID) {
    this.nodeUUID = nodeUUID;
  }  

  public String getMulticastIP() {
    return multicastIP;
  }

  public void setMulticastIP(String multicastIP) {
    this.multicastIP = multicastIP;
  }

  public int getMulticastPort() {
    return multicastPort;
  }

  public void setMulticastPort(int multicastPort) {
    this.multicastPort = multicastPort;
  }

  public String getNodeUUID() {
    return nodeUUID;
  }

  public void setNodeUUID(String nodeUUID) {
    this.nodeUUID = nodeUUID;
  }

  public synchronized int getNodeState() {
    return nodeState;
  }

  public synchronized void setNodeState(int nodeState) {
    if (this.nodeState != STATE_CANDIDATE && nodeState == STATE_CANDIDATE) {
      consecutiveElections = 1;
    }
    
    if (nodeState == STATE_CANDIDATE) {
      votesNumber = 1;
    }
    this.nodeState = nodeState;
  }
}
