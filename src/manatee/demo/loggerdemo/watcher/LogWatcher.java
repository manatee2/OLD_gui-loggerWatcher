package manatee.demo.loggerdemo.watcher;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import manatee2.prototype.common.logger.shared.LogConstants;
import manatee2.prototype.common.logger.shared.LogMessage;
import manatee2.prototype.common.logger.shared.LogMessageSeverity;


/**
 * Desktop Application which displays new Log Messages.
 */
public class LogWatcher extends Application
{
    /**
     * List of Log Messages currently displayed in the scrolling list.
     */
    private ObservableList<LogMessage> logMessages = FXCollections.observableArrayList(
            new LogMessage("Logger", LogMessageSeverity.Info, "Logging started."));

    /**
     * Scrollable message area.
     */
    private TableView<LogMessage> tableView;

    /**
     * Button Panel which contains the 'Clear' and 'Scroll Lock' options.
     */
    private HBox buttonPanel;

    /**
     * Option to clear the Log Message window.
     */
    private Button clearButton = new Button();

    /**
     * Option to disable auto-scrolling of the Log Message window.
     */
    CheckBox scrollLock = new CheckBox();


    /**
     * Entry point.
     */
    public static void main(String[] args)
    {
        //
        // Build and show the GUI.
        //
        launch(args);
    }


    @Override
    public void init()
    {
        // Nothing to do.
    }


    @Override
    public void start(Stage primaryStage)
    {
        //
        // Kick-off a separate thread to listen for new Log Messages.
        //
        listenForMessages();

        //
        // Create the Scrollable Message Area.
        //
        tableView = createScrollableMessagePanel();

        //
        // Create the Top Button Panel which contains the 'Clear' and 'Scroll Lock' options.
        //
        buttonPanel = createButtonPanel(tableView);

        //
        // Create the Root Node.
        //
        BorderPane rootNode = new BorderPane();

        //
        // Add the Top Button Panel and Scrollable Message Area.
        //
        rootNode.setTop(buttonPanel);
        rootNode.setCenter(tableView);

        //
        // Create the Scene.
        //
        Scene myScene = new Scene(rootNode, 795, 350, Color.WHITE);

        //
        // Set the Scene onto the Stage.
        //
        primaryStage.setScene(myScene);

        //
        // Put a title on the window.
        //
        primaryStage.setTitle("Log Watcher");

        //
        // Show the Stage and its Scene.
        //
        primaryStage.show();

    }


    @Override
    public void stop()
    {
        Platform.exit();
        System.exit(0);
    }


    /**
     * Kick-off a separate thread which listens for new Log Messages.
     */
    private void listenForMessages()
    {
        //
        // Kick-off a separate thread which will listen for new Log Messages.
        //
        Task<Void> task = new Task<Void>()
        {
            @Override
            public Void call()
            {
                try
                {
                    //
                    // Wait a moment to allow the GUI to be built.
                    //
                    Thread.sleep(1000);

                    //
                    // Create a JMS Connection and start it.
                    //
                    ActiveMQConnectionFactory jmsConnectionFactory =
                            new ActiveMQConnectionFactory(LogConstants.JMS_URL);
                    Connection jmsConnection = jmsConnectionFactory.createConnection();
                    jmsConnection.start();

                    //
                    // Create a JMS Session.
                    //
                    Session jmsSession = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

                    //
                    // Create the Incoming JMS Topic.
                    //
                    Destination jmsIncomingTopic = jmsSession.createTopic(LogConstants.JMS_TOPIC);

                    //
                    // Create a JMS Message Consumer from the Session to the Topic.
                    //
                    MessageConsumer jmsConsumer = jmsSession.createConsumer(jmsIncomingTopic);

                    //
                    // Loop forever.
                    //
                    while (true)
                    {
                        //
                        // Wait for a Log Message to show-up on the JMS Queue.
                        //
                        Message message = jmsConsumer.receive(1000);

                        //
                        // Handle a Timeout.
                        //
                        if (message == null)
                        {
                            continue;
                        }

                        //
                        // Verify it is a valid JMS Message.
                        //
                        if (!(message instanceof ObjectMessage))
                        {
                            logMessages.add(new LogMessage("Logger", LogMessageSeverity.Error,
                                    "Ignoring Non-ObjectMessage: " + message));
                            continue;
                        }

                        //
                        // Extract the raw message.
                        //
                        ObjectMessage objectMessage = (ObjectMessage) message;

                        //
                        // Verify it is actually a Log Message.
                        //
                        if (!(objectMessage.getObject() instanceof LogMessage))
                        {
                            logMessages.add(new LogMessage("Logger", LogMessageSeverity.Error,
                                    "Ignoring Non-LogMessage: " + message));
                            continue;
                        }

                        //
                        // Extract the Log Message from the JMS Message.
                        //
                        LogMessage logMessage = (LogMessage) objectMessage.getObject();

                        //
                        // Add the Log Message to the Scrollable Log Messages.
                        // This is being done in the Event Thread.
                        //
                        Platform.runLater(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                logMessages.add(logMessage);
                            }
                        });
                    }

                }
                catch (Exception exception)
                {
                    logMessages.add(new LogMessage("Logger", LogMessageSeverity.Error, "Exception: " + exception));
                    exception.printStackTrace();
                }
                return null;
            }
        };
        new Thread(task).start();
    }


    /**
     * Creates the Scrollable Message Area.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private TableView<LogMessage> createScrollableMessagePanel()
    {
        //
        // Create each of the Columns.
        //

        TableColumn timestampColumn = new TableColumn();
        timestampColumn.setText("Timestamp");
        timestampColumn.setCellValueFactory(new PropertyValueFactory("timestamp"));
        timestampColumn.setSortable(false);

        TableColumn reporterColumn = new TableColumn();
        reporterColumn.setText("Reporter");
        reporterColumn.setCellValueFactory(new PropertyValueFactory("reporter"));
        reporterColumn.setSortable(false);

        TableColumn severityColumn = new TableColumn();
        severityColumn.setText("Severity");
        severityColumn.setCellValueFactory(new PropertyValueFactory("severity"));
        severityColumn.setSortable(false);

        TableColumn textColumn = new TableColumn();
        textColumn.setText("Text");
        textColumn.setCellValueFactory(new PropertyValueFactory("text"));
        textColumn.setSortable(false);
        textColumn.setPrefWidth(500);

        //
        // Define the main table view (i.e. the Scrollable window) and add the columns and underlying data model.
        //
        TableView tableView = new TableView();
        tableView.getColumns().addAll(timestampColumn, reporterColumn, severityColumn, textColumn);
        tableView.setItems(logMessages);

        //
        // Listen for items to be added to the underlying data model.
        tableView.getItems().addListener(new ListChangeListener()
        {
            @Override
            public void onChanged(Change change)
            {
                //
                // Accept the item.
                //
                change.next();

                //
                // Handle the 'Scroll Lock' option.
                //
                if (scrollLock.isSelected())
                {
                    return;
                }

                //
                // Otherwise, auto-scroll to the end of the table.
                //
                final int size = tableView.getItems().size();
                if (size > 0)
                {
                    tableView.scrollTo(size - 1);
                }
            }
        });

        return tableView;
    }


    /**
     * Creates the Top Button Panel which contains the 'Clear' and 'Scroll Lock' options.
     * 
     * @param tableView - The Scrollable Message Log
     * 
     * @return - The Top Button Panel.
     */
    private HBox createButtonPanel(TableView<LogMessage> tableView)
    {
        //
        // Create the top button panel. It will contain the 'Clear' and 'Scroll Lock' options.
        //
        HBox topPanel = new HBox();
        topPanel.setAlignment(Pos.CENTER_RIGHT);
        topPanel.setPadding(new Insets(5));
        topPanel.setSpacing(10.0);

        //
        // Add the 'Clear' option.
        //
        clearButton.setText("Clear");
        clearButton.setTooltip(new Tooltip("Clears the Log Message window"));
        clearButton.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                logMessages.clear();
                logMessages.add(new LogMessage("Logger", LogMessageSeverity.Info, "Logging restarted."));
            }
        });

        //
        // Add the 'Scroll Lock' option.
        //
        scrollLock.setText("Scroll Lock");
        scrollLock.setTooltip(new Tooltip("Disables auto-scrolling of the Log Message window"));
        scrollLock.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent event)
            {
                if (!scrollLock.isSelected())
                {
                    final int size = tableView.getItems().size();
                    if (size > 0)
                    {
                        tableView.scrollTo(size - 1);
                    }
                }
            }
        });

        //
        // Add the 'Clear' and 'Scroll Lock' options to the top button panel.
        //
        topPanel.getChildren().addAll(clearButton, scrollLock);

        return topPanel;
    }

}
