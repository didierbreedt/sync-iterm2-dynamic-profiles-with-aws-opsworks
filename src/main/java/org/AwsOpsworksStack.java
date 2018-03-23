package org;

import com.amazonaws.services.opsworks.model.Stack;

public class AwsOpsworksStack extends Stack {
    AwsOpsworksStack(Stack stack) {
        setName(stack.getName());
        setStackId(stack.getStackId());
    }

    @Override
    public String toString() {
        return getName();
    }
}
