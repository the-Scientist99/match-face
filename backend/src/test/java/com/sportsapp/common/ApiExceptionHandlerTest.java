package com.sportsapp.common;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

  @RestController
  static class ProbeController {
    record Body(@NotBlank String name) {}

    @GetMapping("/probe/conflict")
    void conflict() { throw ApiException.conflict("TEAMS_LOCKED"); }

    @GetMapping("/probe/missing")
    void missing() { throw ApiException.notFound(); }

    @PostMapping("/probe/body")
    void body(@Valid @RequestBody Body body) {}

    @GetMapping("/probe/stale")
    void stale() { throw new ObjectOptimisticLockingFailureException("X", 1); }
  }

  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
      .setControllerAdvice(new ApiExceptionHandler())
      .build();

  @Test
  void apiExceptionBecomesProblemWithCode() throws Exception {
    mvc.perform(get("/probe/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("TEAMS_LOCKED"));
  }

  @Test
  void notFound() throws Exception {
    mvc.perform(get("/probe/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void beanValidationBecomesValidationCode() throws Exception {
    mvc.perform(post("/probe/body").contentType(APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION"))
        .andExpect(jsonPath("$.errors.name").value("NotBlank"));
  }

  @Test
  void optimisticLockBecomesStale() throws Exception {
    mvc.perform(get("/probe/stale"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STALE"));
  }
}
