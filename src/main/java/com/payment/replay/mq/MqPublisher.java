package com.payment.replay.mq;

import com.ibm.mq.MQException;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQPutMessageOptions;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;
import com.payment.replay.exception.MqPublishException;
import com.payment.replay.model.ReplayMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Publishes XML messages to IBM MQ queues.
 *
 * Handles:
 * - Message construction with proper encoding
 * - Queue access and put operations
 * - Connection failure detection and recovery via MqConnectionManager
 * - Proper resource cleanup of queue handles
 */
public final class MqPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqPublisher.class);

    private static final int OPEN_OPTIONS = MQConstants.MQOO_OUTPUT | MQConstants.MQOO_FAIL_IF_QUIESCING;
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final int CCSID_UTF8 = 1208;

    private final MqConnectionManager connectionManager;

    public MqPublisher(MqConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Publishes a replay message to the appropriate MQ queue based on site number.
     *
     * @param message the replay message containing XML payload and routing info
     * @throws MqPublishException if the message cannot be sent after connection recovery attempt
     */
    public void publish(ReplayMessage message) {
        int targetSite = message.getTargetSite();
        String queueName = message.getQueueName();

        try {
            sendToQueue(targetSite, queueName, message.getXmlPayload());
            log.trace("Published message {} to queue {} on site {}",
                    message.getMessageId(), queueName, targetSite);

        } catch (MqPublishException e) {
            // Try once more after invalidating connection (connection recovery)
            log.warn("First publish attempt failed for queue {} on site {}, attempting recovery...",
                    queueName, targetSite);
            connectionManager.invalidateConnection(targetSite);

            try {
                sendToQueue(targetSite, queueName, message.getXmlPayload());
                log.info("Recovery successful - message {} published to queue {} on site {}",
                        message.getMessageId(), queueName, targetSite);
            } catch (MqPublishException retryException) {
                log.error("Publish failed after recovery attempt for message {} to queue {} on site {}",
                        message.getMessageId(), queueName, targetSite);
                throw retryException;
            }
        }
    }

    /**
     * Sends an XML payload to a specific queue on a specific MQ site.
     *
     * @param siteNumber target MQ site (1 or 2)
     * @param queueName  target queue name
     * @param xmlPayload message content
     * @throws MqPublishException if the send operation fails
     */
    private void sendToQueue(int siteNumber, String queueName, String xmlPayload) {
        MQQueue queue = null;

        try {
            MQQueueManager queueManager = connectionManager.getConnection(siteNumber);

            // Open queue for output
            queue = queueManager.accessQueue(queueName, OPEN_OPTIONS);

            // Construct message
            MQMessage mqMessage = createMessage(xmlPayload);

            // Put message options
            MQPutMessageOptions pmo = new MQPutMessageOptions();
            pmo.options = MQConstants.MQPMO_NEW_MSG_ID | MQConstants.MQPMO_NO_SYNCPOINT;

            // Send message
            queue.put(mqMessage, pmo);

            log.trace("Message put to queue {} on site {}, msgId length: {}",
                    queueName, siteNumber, mqMessage.messageId != null ? mqMessage.messageId.length : 0);

        } catch (MQException e) {
            String errorMsg = String.format(
                    "MQ put failed for queue '%s' on site %d: CompCode=%d, Reason=%d",
                    queueName, siteNumber, e.completionCode, e.reasonCode);
            log.error(errorMsg);
            throw new MqPublishException(errorMsg, queueName, siteNumber, e);

        } catch (IOException e) {
            String errorMsg = String.format(
                    "IO error writing message to queue '%s' on site %d: %s",
                    queueName, siteNumber, e.getMessage());
            log.error(errorMsg);
            throw new MqPublishException(errorMsg, queueName, siteNumber, e);

        } finally {
            closeQueue(queue, queueName, siteNumber);
        }
    }

    /**
     * Creates an MQ message from the XML payload string.
     * Sets proper character encoding for XML content.
     *
     * @param xmlPayload the XML string to send
     * @return configured MQMessage ready for put
     * @throws IOException if message writing fails
     */
    private MQMessage createMessage(String xmlPayload) throws IOException {
        MQMessage message = new MQMessage();

        // Set message properties
        message.format = MQConstants.MQFMT_STRING;
        message.characterSet = CCSID_UTF8;
        message.encoding = MQConstants.MQENC_NATIVE;
        message.persistence = MQConstants.MQPER_PERSISTENT;

        // Write payload
        message.writeString(xmlPayload);

        return message;
    }

    /**
     * Closes a queue handle without throwing exceptions.
     */
    private void closeQueue(MQQueue queue, String queueName, int siteNumber) {
        if (queue != null) {
            try {
                queue.close();
            } catch (MQException e) {
                log.warn("Error closing queue {} on site {}: RC={}",
                        queueName, siteNumber, e.reasonCode);
            }
        }
    }

    /**
     * Shuts down the publisher, closing all MQ connections.
     * Should be called during application shutdown.
     */
    public void shutdown() {
        log.info("Shutting down MQ publisher...");
        connectionManager.closeAll();
    }
}
