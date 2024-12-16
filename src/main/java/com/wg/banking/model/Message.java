package com.wg.banking.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String id;
    private String content;
    private MessageType type;
    private String receiver;
    private LocalDateTime createdAt;
}