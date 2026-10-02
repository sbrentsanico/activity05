package edu.cit.sanico.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InstanceIdHolder {

    private static final Logger log = LoggerFactory.getLogger(InstanceIdHolder.class);
    private final String instanceId;

    public InstanceIdHolder() {
        this.instanceId = UUID.randomUUID().toString();
        log.info("[CHANNEL] App started. Unique Instance ID generated: {}", this.instanceId);
    }

    public String getInstanceId() {
        return instanceId;
    }
}
