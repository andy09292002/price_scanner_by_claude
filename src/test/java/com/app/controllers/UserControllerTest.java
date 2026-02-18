package com.app.controllers;

import com.app.models.User;
import com.app.models.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .password("password123")
                .active(true)
                .build();
        testUser.setId("test-id-123");
    }

    @Test
    void getAllUsers_ReturnsUserList() throws Exception {
        when(userRepository.findAll()).thenReturn(Arrays.asList(testUser));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("testuser"));

        verify(userRepository, times(1)).findAll();
    }

    @Test
    void getUserById_WhenUserExists_ReturnsUser() throws Exception {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));

        mockMvc.perform(get("/api/users/test-id-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));

        verify(userRepository, times(1)).findById("test-id-123");
    }

    @Test
    void getUserById_WhenUserNotExists_ReturnsNotFound() throws Exception {
        when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found with id: nonexistent"));

        verify(userRepository, times(1)).findById("nonexistent");
    }

    @Test
    void createUser_WithValidData_ReturnsCreatedUser() throws Exception {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("testuser"));

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void createUser_WithDuplicateUsername_ReturnsConflict() throws Exception {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isConflict());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_WithDuplicateEmail_ReturnsConflict() throws Exception {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isConflict());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUser_WhenUserExists_ReturnsUpdatedUser() throws Exception {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        mockMvc.perform(put("/api/users/test-id-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isOk());

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void updateUser_WhenUserNotExists_ReturnsNotFound() throws Exception {
        when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/users/nonexistent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found with id: nonexistent"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deleteUser_WhenUserExists_ReturnsNoContent() throws Exception {
        when(userRepository.existsById("test-id-123")).thenReturn(true);

        mockMvc.perform(delete("/api/users/test-id-123"))
                .andExpect(status().isNoContent());

        verify(userRepository, times(1)).deleteById("test-id-123");
    }

    @Test
    void deleteUser_WhenUserNotExists_ReturnsNotFound() throws Exception {
        when(userRepository.existsById("nonexistent")).thenReturn(false);

        mockMvc.perform(delete("/api/users/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found with id: nonexistent"));

        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void createUser_WithBlankUsername_ReturnsBadRequest() throws Exception {
        User invalidUser = User.builder()
                .username("")
                .email("test@example.com")
                .password("password123")
                .active(true)
                .build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_WithNullUsername_ReturnsBadRequest() throws Exception {
        User invalidUser = User.builder()
                .email("test@example.com")
                .password("password123")
                .active(true)
                .build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_WithInvalidEmail_ReturnsBadRequest() throws Exception {
        User invalidUser = User.builder()
                .username("testuser")
                .email("not-an-email")
                .password("password123")
                .active(true)
                .build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_WithBlankEmail_ReturnsBadRequest() throws Exception {
        User invalidUser = User.builder()
                .username("testuser")
                .email("")
                .password("password123")
                .active(true)
                .build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_WithShortPassword_ReturnsBadRequest() throws Exception {
        User invalidUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .password("12345")
                .active(true)
                .build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_WithBlankPassword_ReturnsBadRequest() throws Exception {
        User invalidUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .password("")
                .active(true)
                .build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_WithShortUsername_ReturnsBadRequest() throws Exception {
        User invalidUser = User.builder()
                .username("ab")
                .email("test@example.com")
                .password("password123")
                .active(true)
                .build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUser_WithInvalidData_ReturnsBadRequest() throws Exception {
        User invalidUser = User.builder()
                .username("")
                .email("invalid")
                .password("short")
                .active(true)
                .build();

        mockMvc.perform(put("/api/users/test-id-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(userRepository, never()).save(any(User.class));
    }
}
