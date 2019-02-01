package org;
import com.amazonaws.auth.*;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.*;
import com.amazonaws.services.opsworks.AWSOpsWorks;
import com.amazonaws.services.opsworks.AWSOpsWorksClientBuilder;
import com.amazonaws.services.opsworks.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.awt.event.ActionEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SyncAWS_iTerm2 extends Application {

    final static String INSTANCE_STOPPED = "stopped";

    private CheckBox includeStopped = new CheckBox("Include stopped instances?");
    private CheckBox autoSudo = new CheckBox("Sudo upon login?");
    private TextField key = new TextField();
    private TextField secret = new TextField();
    private TextField region = new TextField();
    private TextField username = new TextField();
    private ComboBox<AwsOpsworksStack> stackSelection = new ComboBox<>();
    private Tooltip regionTooltip = new Tooltip();
    private Button btnSyncStacks = new Button();
    private Button btnSyncProfiles = new Button();

    private AWSCredentials awsCredentials = new AWSCredentialsProviderChain(
            new ProfileCredentialsProvider(),
            new AWSStaticCredentialsProvider(new BasicAWSCredentials("", ""))
    ).getCredentials();

    private AWSOpsWorks awsOpsWorks;
    private String dynamicProfileLocation = System.getProperty("user.home") + "/Library/Application Support/iTerm2/DynamicProfiles";
    private Gson gson = new GsonBuilder().create();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Sync iTerm2_AWS");

        if (! new File(dynamicProfileLocation).exists()) {
            err("Missing directory:\n" + dynamicProfileLocation + "\n\nIs iTerm2 installed and on the latest version?");
            return;
        }

        key.setLayoutX(10);
        key.setLayoutY(10);
        key.setPromptText("AWS Access Key");
        key.setPrefWidth(380);
        key.setText(awsCredentials.getAWSAccessKeyId());

        secret.setLayoutX(10);
        secret.setLayoutY(50);
        secret.setPromptText("AWS Access Secret");
        secret.setPrefWidth(380);
        secret.setText(awsCredentials.getAWSSecretKey());

        region.setLayoutX(10);
        region.setLayoutY(90);
        region.setPromptText("AWS Region");
        region.setPrefWidth(380);
        region.setText(new AwsProfileRegionProvider().getRegion());

        stackSelection.setLayoutX(10);
        stackSelection.setLayoutY(130);
        stackSelection.setPromptText("Stack Selection");
        stackSelection.setPrefWidth(380);
        stackSelection.setDisable(true);
        regionTooltip.setText("Please note, if your stack is not regional, stick to us-east-1");
        region.setTooltip(regionTooltip);

        autoSudo.setLayoutX(10);
        autoSudo.setLayoutY(170);
        autoSudo.setPrefWidth(380);
        autoSudo.setVisible(false);

        includeStopped.setLayoutX(10);
        includeStopped.setLayoutY(210);
        includeStopped.setPrefWidth(380);
        includeStopped.setVisible(false);

        username.setLayoutX(210);
        username.setLayoutY(210);
        username.setPromptText("SSH Username");
        username.setPrefWidth(180);
        username.setText(System.getProperty("user.name"));
        username.setVisible(false);

        btnSyncStacks.setLayoutX(10);
        btnSyncStacks.setLayoutY(250);
        btnSyncStacks.setPrefWidth(380);
        btnSyncStacks.setText("Retrieve Stacks");
        btnSyncStacks.setOnAction(ActionEvent -> syncStacks());

        btnSyncProfiles.setLayoutX(10);
        btnSyncProfiles.setLayoutY(290);
        btnSyncProfiles.setPrefWidth(380);
        btnSyncProfiles.setText("Sync Dynamic Profiles");
        btnSyncProfiles.setDisable(true);
        btnSyncProfiles.setOnAction(ActionEvent -> syncProfiles());

        Pane root = new Pane();

        root.getChildren().add(btnSyncStacks);
        root.getChildren().add(key);
        root.getChildren().add(secret);
        root.getChildren().add(region);
        root.getChildren().add(stackSelection);
        root.getChildren().add(autoSudo);
        root.getChildren().add(includeStopped);
        root.getChildren().add(btnSyncProfiles);
        root.getChildren().add(username);
        primaryStage.setScene(new Scene(root, 410, 340));
        primaryStage.show();
    }

    private void syncProfiles() {
        if ( stackSelection.getSelectionModel().isEmpty() ) {
            err("Please select a stack");
            return;
        }
        btnSyncProfiles.setText("Syncing iTerm2 Profiles with " + stackSelection.getSelectionModel().getSelectedItem().getName());
        btnSyncProfiles.setDisable(true);
        btnSyncStacks.setDisable(true);

        Task task = new Task() {
            @Override
            protected Object call() {
                System.out.println("Sync profiles for stack: " + stackSelection.getSelectionModel().getSelectedItem().getName());
                System.out.println("Sync profiles for stack: " + stackSelection.getSelectionModel().getSelectedItem().getStackId());
                DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
                describeInstancesRequest.setStackId(stackSelection.getSelectionModel().getSelectedItem().getStackId());
                DescribeInstancesResult describeInstancesResult = awsOpsWorks.describeInstances(describeInstancesRequest);
                for(Instance instance : describeInstancesResult.getInstances()) {
                    List<Profile> profileList = new ArrayList<>();

                    if (!includeStopped.isSelected() && instance.getStatus().equals(SyncAWS_iTerm2.INSTANCE_STOPPED)) {
                        continue;
                    }

                    profileList.add(
                            new Profile(
                                stackSelection.getSelectionModel().getSelectedItem().getName(),
                                instance,
                                autoSudo.isSelected(),
                                username.getText()
                            )
                    );
                    Profiles profiles = new Profiles(profileList);
                    try (FileWriter file = new FileWriter(dynamicProfileLocation + "/" + instance.getHostname() + ".json", false)) {
                        file.write(gson.toJson(profiles));
                        file.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                return null;
            }
        };
        task.setOnSucceeded(WorkerStateEvent->{
            System.out.println("Sync profiles completed");
            btnSyncProfiles.setText("All done, sync again?");
            btnSyncProfiles.setDisable(false);
            btnSyncStacks.setDisable(false);
        });
        task.setOnFailed(WorkerStateEvent->{
            err("Something went wrong..." + task.getException());
            task.getException().printStackTrace();
        });

        new Thread(task).start();
    }

    private void err(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Application Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void syncStacks() {
        if (username.isVisible()) {
            username.setVisible(false);
        }
        if (includeStopped.isVisible()) {
            includeStopped.setVisible(false);
        }
        if (autoSudo.isVisible()) {
            autoSudo.setVisible(false);
        }
        if (btnSyncProfiles.isVisible()) {
            btnSyncProfiles.setVisible(false);
        }
        btnSyncStacks.setText("Retrieving Stacks...");
        btnSyncStacks.setDisable(true);
        stackSelection.setValue(null);
        Boolean validRegionEntered = false;
        Regions defaultRegion = Regions.US_EAST_1;
        for (Regions officialRegion : Regions.values()) {
            if (officialRegion.getName().toLowerCase().equals(region.getText().toLowerCase())) {
                validRegionEntered = true;
            }
        }
        if (validRegionEntered) {
            defaultRegion = Regions.fromName(region.getText());
        }

        AWSCredentials basicAwsCredentials = new BasicAWSCredentials(key.getText(), secret.getText());
        awsOpsWorks = AWSOpsWorksClientBuilder.standard().withRegion(defaultRegion).withCredentials(
                new AWSStaticCredentialsProvider(basicAwsCredentials)
        ).build();

        Task task = new Task() {
            @Override
            protected Object call() {
                DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
                DescribeStacksResult describeStacksResult = awsOpsWorks.describeStacks(describeStacksRequest);
                System.out.println(describeStacksResult.toString());

                stackSelection.setValue(null);
//                stackSelection.getItems().clear();
                for (Stack stack : describeStacksResult.getStacks()) {
                    AwsOpsworksStack awsOpsworksStack = new AwsOpsworksStack(stack);
                    stackSelection.getItems().add(awsOpsworksStack);
                }

                return null;
            }
        };
        task.setOnSucceeded(WorkerStateEvent->{
            //btnSyncStacks.setText("Sync Profiles");
            btnSyncStacks.setDisable(false);
            autoSudo.setVisible(true);
            includeStopped.setVisible(true);
            stackSelection.setDisable(false);
            btnSyncProfiles.setDisable(false);
            btnSyncProfiles.setVisible(true);
            username.setVisible(true);
            //btnSyncStacks.setOnAction(EventActions->syncProfiles());
        });

        task.setOnFailed(WorkerStateEvent->err("Something went wrong. Double check your AWS Credentials." + task.getException()));

        new Thread(task).start();
    }
}
