I will show you everything by myself but here is short description if you want to run it.

Client uses libjcurses library (exists both for Windows and Lynux) to read input without blocking and behave like real console application. There are different types of this library for x32 and x64 versions of Windows. By default there libjcurses64.dll that is x64 version is active. If you want to run it on x32 Windows rename in folder "../lib" libjcurses64.dll to -libjcurses64.dll and rename -libjcurses.dll to libjcurses.dll

To run it on lynux use libjcurses.so or libjcurses64.so

To run servers execute run_servers.bat in ../bin or ../src folder. By default there are 5 servers that are equal one of which will become leader and other - followers. (!!! bat files works only on Windows !!!) To run server directly use command (%x% is number of server, %y% - total number of servers):

java -cp ".;../lib/commons-lang3-3.4.jar;../bin" Server 224.0.0.2 2333 "../log/log_server_%x%.txt" %y%

To run clients execute run_client.bat in ../bin or ../src folder. You can write in any client and observe changes in another. You may run as many clients as you want. If more than one client is trying to change text, only modification of one of them will be applied. (!!! bat files works only on Windows !!!) To run client directly use command (%x% is number of the client):

java -cp ".;../lib/jcurses.jar;../lib/commons-lang3-3.4.jar;../bin" Client 224.0.0.2 2333 "../log/client_%x%.txt"

You can run clients first, type different nessages in all of them and than run servers. You'll see that after leader will be found all clients will have the same text. If you see rotaiting dash it means that client is not receiving responses from the server. It may be during election period or if there are no enough nodes to replicate messages. Message has to be replicated on more than half nodes.

System may tolerate failure of less than half nodes. After nodes recovery data is repicated to them from master server.

Because I din't know that we can use RMI for this assignment everything is written in pure socket interactions.
