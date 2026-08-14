package com.globalisor.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bi_chat_threads")
public class ChatThread {

    @Id
    private String id;
    private String userId = "admin";
    private String title = "New Conversation";
    private String activeCompany;
    private String activeUen;
    private List<ChatMessage> messages = new ArrayList<>();
    private Date createdAt = new Date();
    private Date updatedAt = new Date();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String id;
        private String sender; // "user" or "assistant"
        private String text;
        private String type; // e.g. "uen_query", "director_summary", "nominee_director_appointment_document", etc.
        private List<String> options;
        private String companyName;
        private String docId;
        private String viewUrl;
        private String downloadUrl;
        private Integer docCount;
        private Date timestamp = new Date();
    }
}
