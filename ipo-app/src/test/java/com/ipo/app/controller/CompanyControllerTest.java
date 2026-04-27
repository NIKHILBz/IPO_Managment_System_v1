// package com.ipo.app.controller;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.ipo.entity.model.Company;
// import com.ipo.service.service.CompanyService;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.http.MediaType;
// import org.springframework.test.web.servlet.MockMvc;

// import java.math.BigDecimal;
// import java.time.LocalDateTime;
// import java.util.Arrays;
// import java.util.Optional;

// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.Mockito.when;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @SpringBootTest
// @AutoConfigureMockMvc
// public class CompanyControllerTest {

//     @Autowired
//     private MockMvc mockMvc;

//     @MockBean
//     private CompanyService companyService;

//     @Autowired
//     private ObjectMapper objectMapper;

//     private Company testCompany;

//     @BeforeEach
//     public void setUp() {
//         testCompany = ((Object) Company.builder())
//                 .id(1L)
//                 .companyName("Test Company")
//                 .industry("Technology")
//                 .description("A test company")
//                 .foundedYear(new BigDecimal("2020"))
//                 .ceoName("John Doe")
//                 .headquarters("New York")
//                 .currentValuation(new BigDecimal("1000000000"))
//                 .createdAt(LocalDateTime.now())
//                 .updatedAt(LocalDateTime.now())
//                 .build();
//     }

//     @Test
//     public void testGetCompanyById() throws Exception {
//         when(companyService.getCompanyById(1L)).thenReturn(Optional.of(testCompany));

//         mockMvc.perform(get("/api/v1/companies/1"))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$.companyName").value("Test Company"));
//     }

//     @Test
//     public void testGetAllCompanies() throws Exception {
//         when(companyService.getAllCompanies()).thenReturn(Arrays.asList(testCompany));

//         mockMvc.perform(get("/api/v1/companies"))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$[0].companyName").value("Test Company"));
//     }

//     @Test
//     public void testCreateCompany() throws Exception {
//         when(companyService.createCompany(any(Company.class))).thenReturn(testCompany);

//         mockMvc.perform(post("/api/v1/companies")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .content(objectMapper.writeValueAsString(testCompany)))
//                 .andExpect(status().isCreated())
//                 .andExpect(jsonPath("$.companyName").value("Test Company"));
//     }

//     @Test
//     public void testDeleteCompany() throws Exception {
//         mockMvc.perform(delete("/api/v1/companies/1"))
//                 .andExpect(status().isNoContent());
//     }
// }
