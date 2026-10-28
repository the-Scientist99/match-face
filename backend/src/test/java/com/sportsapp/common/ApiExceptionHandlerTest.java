package com.sportsapp.common;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/probe/item/{id}")
    void item(@PathVariable UUID id) {}

    @GetMapping("/probe/page")
    void page(@RequestParam @Min(1) int page) {}
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
  void malformedIdInPathIsNotFound() throws Exception {
    mvc.perform(get("/probe/item/not-a-uuid"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void badRequestParamTypeIsBadRequest() throws Exception {
    mvc.perform(get("/probe/page").param("page", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void malformedJsonHasCode() throws Exception {
    mvc.perform(post("/probe/body").contentType(APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void wrongMethodHasCode() throws Exception {
    mvc.perform(post("/probe/missing"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void missingRequestParamHasCode() throws Exception {
    mvc.perform(get("/probe/page"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void requestParamConstraintIsValidation() throws Exception {
    mvc.perform(get("/probe/page").param("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION"))
        .andExpect(jsonPath("$.errors.page").value("Min"));
  }

  @Test
  void optimisticLockBecomesStale() throws Exception {
    mvc.perform(get("/probe/stale"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STALE"));
  }
}
