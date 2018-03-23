package org;

import com.amazonaws.services.opsworks.model.Instance;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused", "MismatchedQueryAndUpdateOfCollection"})
class Profile {
    @SerializedName("Custom Command")
    private String CustomCommand;

    @SerializedName("Initial Text")
    private String InitialText;

    private String Command;

    private String Guid;

    private List<String> Tags = new ArrayList<>();

    private String Name;

    Profile(String stackName, Instance instance, Boolean autoSudo) {
        this.CustomCommand = "Yes";
        this.Command = "ssh " + instance.getPrivateIp();
        this.Guid = instance.getInstanceId();
        this.Tags.add(stackName);
        this.Tags.add(instance.getStatus());
        this.Name = instance.getHostname();

        if ( autoSudo ) {
            this.InitialText = "sudo -s";
        }
    }
}