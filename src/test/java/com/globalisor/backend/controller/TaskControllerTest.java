package com.globalisor.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalisor.backend.model.Task;
import com.globalisor.backend.repository.TaskRepository;
import com.globalisor.backend.service.NotificationService;
import com.globalisor.backend.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class TaskControllerTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TaskService taskService;

    @Test
    public void testCreateTask() throws Exception {
        String payloadJson = """
        {
            "taskScope": "CLIENT",
            "isInternal": false,
            "companyName": "Lowcode Minds",
            "companyId": "COMP-C-1068",
            "clientId": "C-1068",
            "clientName": "Lowcode Minds",
            "clientEmail": "lowcode.minds@client.globalisor.com",
            "title": "XBRL - 31 MAR 2026",
            "category": "Tax & Accounting Filing",
            "type": "REQUEST",
            "priority": "URGENT",
            "status": "ASSIGNED",
            "assignedTo":
            {
                "id": "6aa6c3e86340dedf7f1cd145",
                "name": "Saurabh Choudhary",
                "role": "STAFF",
                "email": "saurabhchoudhary@globalisor.com",
                "avatar": "SC"
            },
            "dueDate": "2026-07-10",
            "description": "",
            "createdBy":
            {
                "id": "6aa6c3e86340dedf7f1cd145",
                "name": "Saurabh Choudhary",
                "role": "STAFF",
                "email": "saurabhchoudhary@globalisor.com",
                "avatar": "SC"
            }
        }
        """;

        ObjectMapper mapper = new ObjectMapper();
        Task task = mapper.readValue(payloadJson, Task.class);

        Mockito.when(taskRepository.count()).thenReturn(3L);
        Mockito.when(taskRepository.findByTicketNumber("TSK-1004")).thenReturn(Optional.empty());
        Mockito.when(taskRepository.save(any(Task.class))).thenAnswer(i -> {
            Task t = i.getArgument(0);
            t.setId("task-123");
            return t;
        });

        Task created = taskService.createTask(task);
        assertNotNull(created);
        assertEquals("TSK-1004", created.getTicketNumber());
        assertEquals("XBRL - 31 MAR 2026", created.getTitle());
        assertEquals("ASSIGNED", created.getStatus());
    }

    @Test
    public void testCreateTaskWithCollision() throws Exception {
        Task task = Task.builder()
                .title("New Task")
                .build();

        Mockito.when(taskRepository.count()).thenReturn(3L);
        // Simulate TSK-1004 already exists, so it should advance to TSK-1005
        Mockito.when(taskRepository.findByTicketNumber("TSK-1004")).thenReturn(Optional.of(new Task()));
        Mockito.when(taskRepository.findByTicketNumber("TSK-1005")).thenReturn(Optional.empty());
        Mockito.when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        Task created = taskService.createTask(task);
        assertNotNull(created);
        assertEquals("TSK-1005", created.getTicketNumber());
    }

    @Test
    public void testCreateTaskNullCreatorFields() {
        Task task = Task.builder()
                .title("Minimal Task")
                .createdBy(new Task.UserRef()) // All fields null
                .build();

        Mockito.when(taskRepository.count()).thenReturn(0L);
        Mockito.when(taskRepository.findByTicketNumber("TSK-1001")).thenReturn(Optional.empty());
        Mockito.when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        Task created = taskService.createTask(task);
        assertNotNull(created);
        assertEquals("TSK-1001", created.getTicketNumber());
        assertNotNull(created.getActivityLog());
    }
}
