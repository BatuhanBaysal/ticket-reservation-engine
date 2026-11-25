package com.ticket.inventory;

import org.testcontainers.kafka.KafkaContainer;

final class BrokerControl {

    private BrokerControl() {
    }

    static void pause(KafkaContainer kafka) {
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
    }

    static void unpause(KafkaContainer kafka) {
        Boolean paused = kafka.getDockerClient()
            .inspectContainerCmd(kafka.getContainerId())
            .exec()
            .getState()
            .getPaused();
        if (Boolean.TRUE.equals(paused)) {
            kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
        }
    }
}
