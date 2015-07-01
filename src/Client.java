import jcurses.system.CharColor;
import jcurses.system.Toolkit;
import jcurses.widgets.DefaultLayoutManager;
import jcurses.widgets.Label;
import jcurses.widgets.TextArea;
import jcurses.widgets.WidgetsConstants;
import jcurses.widgets.Window;


public class Client {
  public static void main(String[] args) throws InterruptedException {
    
    int screenWidth = Toolkit.getScreenWidth() - 8;
    int screenHeight = Toolkit.getScreenHeight() - 4;
    String message = "width: " + screenWidth + ", height: " + screenHeight;
    
    //System.out.println(message);
    
    
    Window w = new Window(screenWidth, screenHeight, true, "Distributed Systems - Individual Assignment 3");
    Label label = new Label("Oleg Iskra <o.iskra@innopolis.ru>", new CharColor(
                    CharColor.BLACK, CharColor.GREEN));
    
    int taWidth = screenWidth - 6;
    int taHeight = screenHeight - 4;
    
    TextArea textArea = new TextArea(taWidth, taHeight, message);
    DefaultLayoutManager mgr = new DefaultLayoutManager();
    mgr.bindToContainer(w.getRootPanel());
    mgr.addWidget(label, 0, 0, screenWidth, screenHeight, WidgetsConstants.ALIGNMENT_TOP,
                    WidgetsConstants.ALIGNMENT_CENTER);
    mgr.addWidget(textArea, 0, 0, screenWidth, screenHeight, WidgetsConstants.ALIGNMENT_CENTER,
            WidgetsConstants.ALIGNMENT_CENTER);
    w.show();
    Thread.sleep(3000);
    textArea.setText("Hello world!", true);

    //textArea.getText();
    //Thread.currentThread();
    //Thread.sleep(30000);
    //w.close();
  }
}
