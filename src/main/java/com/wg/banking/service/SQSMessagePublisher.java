package com.wg.banking.service;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wg.banking.model.Message;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Log4j2
public class SQSMessagePublisher {

    @Value("${aws.queueName}")
    private String queueName;

    private final AmazonSQS amazonSQSClient;
    private final ObjectMapper objectMapper;

    public SQSMessagePublisher(AmazonSQS amazonSQSClient, ObjectMapper objectMapper) {
        this.amazonSQSClient = amazonSQSClient;
        this.objectMapper = objectMapper;
    }

    public void publishMessage(Message message) {
        try {
            GetQueueUrlResult queueUrl = amazonSQSClient.getQueueUrl(queueName);
            System.out.println(message);
            var result = amazonSQSClient.sendMessage(queueUrl.getQueueUrl(), objectMapper.writeValueAsString(message));
            System.out.println(result);
        } catch (Exception e) {
            log.error("Queue Exception Message: {}", e.getMessage());
        }
    }
}